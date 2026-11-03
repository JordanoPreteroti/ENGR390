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

        setStatus("Idle. Join the ESP32's wifi then press connect"); // Display initial instructions on UI
        updateButton(); // Update button label to match initial state
    }

    @Override // Overrides activity destruction lifecycle method
    protected void onDestroy() { // Called when activity is being destroyed
        super.onDestroy(); // Execute parent activity cleanup
        polling = false; // Stop polling loop flag
        if (executor != null) executor.shutdownNow(); // Immediately cancel background tasks
        try { // Try block to safely unregister network callback
            connectivityManager.unregisterNetworkCallback(wifiCallback); // Remove network callback listener
        } catch (IllegalArgumentException ignored) { // Catch exception if callback was already unregistered
        }
    }

    // Starts background HTTP polling task
    private void startPolling() { // Helper method to start data polling
        polling = true; // Set polling flag to active
        setStatus("Connecting to " + DATA_URL + " ..."); // Update status text on UI
        updateButton(); // Refresh button label text
        executor = Executors.newSingleThreadScheduledExecutor(); // Create single background thread executor
        pollTask = executor.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, // Schedule recurring poll task
                TimeUnit.MILLISECONDS); // Specify time unit in milliseconds
    }

    // Stops background HTTP polling task
    private void stopPolling(String reason) { // Helper method to stop data polling
        polling = false; // Set polling flag to inactive
        if (pollTask != null) { // Check if polling task is active
            pollTask.cancel(true); // Cancel scheduled task execution
            pollTask = null; // Clear task reference
        }
        if (executor != null) { // Check if executor service exists
            executor.shutdownNow(); // Shutdown thread executor pool
            executor = null; // Clear executor reference
        }
        setStatus(reason); // Update status text with given reason
        updateButton(); // Refresh button label text
    }

    // Executes a single HTTP GET request to read ESP32 data
    private void pollOnce() { // Background polling task execution method
        if (!polling) return; // Exit if polling is stopped
        HttpURLConnection conn = null; // Connection instance holder
        try { // Try block for network I/O operations
            URL url = new URL(DATA_URL); // Create target URL object
            Network net = wifiNetwork; // Capture current WiFi network reference
            conn = (HttpURLConnection) (net != null ? net.openConnection(url) : url.openConnection()); // Open HTTP connection via WiFi network
            conn.setConnectTimeout(TIMEOUT_MS); // Set connection timeout limit
            conn.setReadTimeout(TIMEOUT_MS); // Set data read timeout limit
            conn.setUseCaches(false); // Disable HTTP request caching
            conn.setRequestMethod("GET"); // Set HTTP request method to GET

            int code = conn.getResponseCode(); // Execute request and fetch response code
            if (code != HttpURLConnection.HTTP_OK) { // Check if response code is not HTTP 200 OK
                pollStatus("ESP32 answered HTTP " + code + " (no reading yet?)"); // Update UI status with error code
                return; // Exit poll attempt on error
            }
            try (BufferedReader reader = new BufferedReader( // Create buffered reader for connection input stream
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { // Convert byte stream to UTF-8 text
                String line = reader.readLine(); // Read single response line from ESP32
                if (line != null) handleLine(line.trim()); // Parse line data if not null
            }
        } catch (IOException e) { // Catch network I/O exceptions
            if (wifiNetwork == null) { // Check if WiFi network is unavailable
                pollStatus("Phone is not on a WiFi network. Join \"" + WIFI_NAME + "\" in Settings"); // Prompt user to connect WiFi
            } else { // Handle reachable network with unreachable host
                pollStatus("Can't reach the ESP32 (" + e.getClass().getSimpleName() // Display connection failure message
                        + "). Is the phone on \"" + WIFI_NAME + "\"?"); // Suggest checking network name
            }
        } catch (RuntimeException e) { // Catch unexpected runtime exceptions
        } finally { // Cleanup block always executed
            if (conn != null) conn.disconnect(); // Disconnect HTTP connection to free resources
        }
    }

    // Parses CSV formatted string from ESP32: raw,volts,rawBAC,estBAC,temp,hum
    private void handleLine(String line) { // Helper method to parse received data line
        String[] f = line.split(","); // Split comma-separated values into array
        if (f.length != 6) { // Verify expected count of 6 elements
            pollStatus("ESP32 says: " + line); // Display raw output if formatting fails
            return; // Exit parser on invalid length
        }
        try { // Try block for numeric string parsing
            int raw = Integer.parseInt(f[0].trim()); // Parse raw MQ-3 ADC integer
            float volts = Float.parseFloat(f[1].trim()); // Parse calculated sensor voltage float
            float rawBac = Float.parseFloat(f[2].trim()); // Parse raw BAC value float
            float estBac = Float.parseFloat(f[3].trim()); // Parse estimated BAC value float
            float temp = Float.parseFloat(f[4].trim()); // Parse ambient temperature float
            float hum = Float.parseFloat(f[5].trim()); // Parse ambient humidity float

            if (!polling) return; // Exit if polling was cancelled mid-request
            runOnUiThread(() -> { // Dispatch UI updates to main UI thread
                adcText.setText("MQ-3 raw: " + raw); // Display raw ADC value
                voltText.setText(String.format(Locale.US, "MQ-3 voltage: %.2f V", volts)); // Format and display voltage
                BACRaw.setText(String.format(Locale.US, "Raw BAC: %.4f", rawBac)); // Format and display raw BAC
                bacText.setText(String.format(Locale.US, "Estimated BAC: %.4f", estBac)); // Format and display estimated BAC
                tempText.setText(String.format(Locale.US, "Temperature: %.1f \u00B0C", temp)); // Format and display temperature
                humText.setText(String.format(Locale.US, "Humidity: %.1f %%", hum)); // Format and display humidity
            });
            pollStatus("Connected, receiving data"); // Update status text to connected state
        } catch (NumberFormatException e) { // Catch string to number conversion error
            pollStatus("Bad data from ESP32: " + line); // Report invalid number format on UI
        }
    } 

    // Updates status message on UI thread
    private void setStatus(String s) { // Helper method to set status text safely
        runOnUiThread(() -> statusText.setText("Device: " + s)); // Set formatted status on UI thread
    }

    // Updates status message only if polling is currently active
    private void pollStatus(String s) { // Helper method for conditional status update
        if (polling) setStatus(s); // Update status if polling is active
    }

    // Updates connect button text depending on polling state
    private void updateButton() { // Helper method to refresh button UI state
        runOnUiThread(() -> connectButton.setText(polling ? "Disconnect" : "Connect")); // Set button text on UI thread
    }
}