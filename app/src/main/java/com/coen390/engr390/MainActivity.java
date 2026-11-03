package com.coen390.engr390;

import android.net.ConnectivityManager; 
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest; 
import android.os.Bundle; 
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException; 
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Right now Main activity class handle the communication between breathalyzer UI and WiFi communication
public class MainActivity extends AppCompatActivity {

    // Must match the ESP32 sketch
    private static final String WIFI_NAME = "Breathalyzer-ESP32"; // The WiFi network name
    private static final String DATA_URL = "http://192.168.4.1/data"; // This is the url for the ESP32 HTTP data endpoint

    private static final long POLL_INTERVAL_MS = 1000;
    private static final int TIMEOUT_MS = 2500;

    private TextView statusText, adcText, voltText, BACRaw, bacText, tempText, humText;
    private Button connectButton; // UI button to toggle connection polling

    private ConnectivityManager connectivityManager; // System connectivity manager reference
    private volatile Network wifiNetwork; // Active WiFi network handle across threads

    private ScheduledExecutorService executor; // Background thread pool for polling timer
    private ScheduledFuture<?> pollTask; // Handle to track repeating polling task
    private volatile boolean polling = false; // Flag indicating if polling is active

    // Listner that detects when the phone finds new WiFi networks or loses connection
    private final ConnectivityManager.NetworkCallback wifiCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) { // Called when WiFi network connects
                    wifiNetwork = network; // Store connected network reference
                }

                @Override
                public void onLost(Network network) { // Called when WiFi network disconnects
                    if (network.equals(wifiNetwork)) wifiNetwork = null; // Clear network reference if lost
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        adcText = findViewById(R.id.adcText);
        BACRaw = findViewById(R.id.BACRaw);
        voltText = findViewById(R.id.voltText);
        bacText = findViewById(R.id.bacText);
        tempText = findViewById(R.id.tempText);
        humText = findViewById(R.id.humText);
        connectButton = findViewById(R.id.connectButton);

        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); // CONNECTIVITY_SERVICE is from Context class, to get the system service of the network.
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(); // only wifi 
        connectivityManager.registerNetworkCallback(request, wifiCallback); // Listne for WiFi network changes and call wifiCallback methods when events occur

        // On click the listener start listen to the ESP32 data or stop listening to it
        connectButton.setOnClickListener(v -> {
            if (polling) { 
                stopPolling("Stopped");
            } else {
                startPolling();
            }
        });

        setStatus("Idle. Join the ESP32's wifi then press connect"); // Custum fonction to set the status message
        updateButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        polling = false;
        if (executor != null) executor.shutdownNow(); // Cancel the background tasks
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback); // Remove network callback listener
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPolling() {
        polling = true;
        setStatus("Connecting to " + DATA_URL + " ..."); // Custum fonction to set the status message
        updateButton();
        executor = Executors.newSingleThreadScheduledExecutor();
        pollTask = executor.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS); // Make the executor poll the ESP32 data every 1 second
    }

    private void stopPolling(String reason) {
        polling = false;
        if (pollTask != null) {
            pollTask.cancel(true); // Cancel scheduled task execution
            pollTask = null;
        }
        if (executor != null) {
            executor.shutdownNow(); // Shutdown thread executor 
            executor = null;
        }
        setStatus(reason);
        updateButton();
    }

    // Executes a one-time HTTP GET request to read ESP32 data, the fonction is called every 1 second by the executor
    private void pollOnce() {
        if (!polling) return;
        HttpURLConnection conn = null; // Connection instance holder
        try {
            URL url = new URL(DATA_URL);
            Network net = wifiNetwork; // Takes the current WiFi network reference
            conn = (HttpURLConnection) (net != null ? net.openConnection(url) : url.openConnection()); // Create HTTP connection via WiFi network
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode(); // Request and fetch response code
            if (code != HttpURLConnection.HTTP_OK) { // Handle errors HTTP response codes
                pollStatus("ESP32 answered HTTP " + code + " (no reading yet?)"); // Update UI status with error code
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { // Convert to UTF-8 text to be used by readline()
                String line = reader.readLine(); 
                if (line != null) handleLine(line.trim()); // Used to format the data received from the ESP32
            }
        } catch (IOException e) {
            if (wifiNetwork == null) {
                pollStatus("Phone is not on a WiFi network. Join \"" + WIFI_NAME + "\" in Settings"); // Ask user to connect WiFi
            } else { // Handle reachable network with unreachable host
                pollStatus("Can't reach the ESP32 (" + e.getClass().getSimpleName()
                        + "). Is the phone on \"" + WIFI_NAME + "\"?");
            }
        } catch (RuntimeException e) {
        } finally {
            if (conn != null) conn.disconnect(); // Disconnect HTTP connection
        }
    }

    // Parses CSV formatted string from ESP32: raw,volts,rawBAC,estBAC,temp,hum
    private void handleLine(String line) {
        String[] f = line.split(",");
        if (f.length != 6) {
            pollStatus("ESP32 says: " + line); // Display raw output
            return;
        }
        try { 
            int raw = Integer.parseInt(f[0].trim()); // raw MQ-3 ADC integer
            float volts = Float.parseFloat(f[1].trim()); // calculated sensor voltage float
            float rawBac = Float.parseFloat(f[2].trim()); // raw BAC value float
            float estBac = Float.parseFloat(f[3].trim()); // estimated BAC value float
            float temp = Float.parseFloat(f[4].trim()); // ambient temperature float
            float hum = Float.parseFloat(f[5].trim()); // ambient humidity float

            if (!polling) return;
            runOnUiThread(() -> { // Dispatch UI updates to main UI thread
                adcText.setText("MQ-3 raw: " + raw);
                voltText.setText(String.format(Locale.US, "MQ-3 voltage: %.2f V", volts)); // Format and display voltage
                BACRaw.setText(String.format(Locale.US, "Raw BAC: %.4f", rawBac)); // Format and display raw BAC
                bacText.setText(String.format(Locale.US, "Estimated BAC: %.4f", estBac)); // Format and display estimated BAC
                tempText.setText(String.format(Locale.US, "Temperature: %.1f \u00B0C", temp)); // Format and display temperature
                humText.setText(String.format(Locale.US, "Humidity: %.1f %%", hum)); // Format and display humidity
            });
            pollStatus("Connected, receiving data");
        } catch (NumberFormatException e) {
            pollStatus("Bad data from ESP32: " + line);
        }
    } 

    // Updates the status message
    private void setStatus(String s) {
        runOnUiThread(() -> statusText.setText("Device: " + s));
    }

    private void pollStatus(String s) {
        if (polling) setStatus(s);
    }

    // Updates connect button text when state changes
    private void updateButton() {
        runOnUiThread(() -> connectButton.setText(polling ? "Disconnect" : "Connect"));
    }
}