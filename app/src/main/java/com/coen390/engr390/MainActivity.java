package com.coen390.engr390; // Package declaration for project namespace

import android.net.ConnectivityManager; // Import system connectivity service manager
import android.net.Network; // Import network connection object abstraction
import android.net.NetworkCapabilities; // Import network capabilities definition class
import android.net.NetworkRequest; // Import network request specification builder
import android.os.Bundle; // Import key-value bundle for activity state management
import android.util.Log; // Import system logging utility
import android.widget.Button; // Import UI button widget class
import android.widget.TextView; // Import UI text view widget class

import androidx.appcompat.app.AppCompatActivity; // Import backward-compatible activity base class

import java.io.BufferedReader; // Import buffered character reader stream
import java.io.IOException; // Import input/output exception class
import java.io.InputStreamReader; // Import stream to character reader converter
import java.net.HttpURLConnection; // Import HTTP client connection class
import java.net.URL; // Import uniform resource locator class
import java.nio.charset.StandardCharsets; // Import standard character encodings
import java.util.Locale; // Import locale formatter specification class
import java.util.concurrent.Executors; // Import thread executor factory utilities
import java.util.concurrent.ScheduledExecutorService; // Import periodic task executor service
import java.util.concurrent.ScheduledFuture; // Import handle for scheduled async tasks
import java.util.concurrent.TimeUnit; // Import time unit scale enumeration

// Main activity class handling breathalyzer UI and WiFi communication
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Breathalyzer"; // Logcat tag for debugging

    // Must match the ESP32 sketch
    private static final String WIFI_NAME_HINT = "Breathalyzer-ESP32"; // Expected WiFi network name
    private static final String DATA_URL = "http://192.168.4.1/data"; // ESP32 HTTP data endpoint URL

    private static final long POLL_INTERVAL_MS = 1000; // Data polling interval in milliseconds
    private static final int TIMEOUT_MS = 2500; // HTTP connection timeout in milliseconds

    // UI text views for displaying sensor readings and device status
    private TextView statusText, adcText, voltText, BACRaw, bacText, tempText, humText;
    private Button connectButton; // UI button to toggle connection polling

    private ConnectivityManager connectivityManager; // System connectivity manager reference
    private volatile Network wifiNetwork; // Active WiFi network handle across threads

    private ScheduledExecutorService executor; // Background thread pool for polling timer
    private ScheduledFuture<?> pollTask; // Handle to track repeating polling task
    private volatile boolean polling = false; // Flag indicating if polling is active

    // Listener callback for monitoring active WiFi network state changes
    private final ConnectivityManager.NetworkCallback wifiCallback =
            new ConnectivityManager.NetworkCallback() { // Anonymous network callback instance
                @Override // Overrides callback method when network becomes available
                public void onAvailable(Network network) { // Called when WiFi network connects
                    wifiNetwork = network; // Store connected network reference
                    Log.d(TAG, "WiFi network available: " + network); // Log network availability
                } // End of onAvailable method

                @Override // Overrides callback method when network connection is lost
                public void onLost(Network network) { // Called when WiFi network disconnects
                    if (network.equals(wifiNetwork)) wifiNetwork = null; // Clear network reference if lost
                    Log.d(TAG, "WiFi network lost"); // Log network disconnect
                } // End of onLost method
            }; // End of NetworkCallback declaration

    @Override // Overrides activity creation lifecycle method
    protected void onCreate(Bundle savedInstanceState) { // Called when activity is created
        super.onCreate(savedInstanceState); // Execute parent activity setup
        setContentView(R.layout.activity_main); // Bind activity layout XML resource

        // Bind layout view instances to Java member fields
        statusText = findViewById(R.id.statusText); // Find status text view by ID
        adcText = findViewById(R.id.adcText); // Find raw ADC text view by ID
        BACRaw = findViewById(R.id.BACRaw); // Find raw BAC text view by ID
        voltText = findViewById(R.id.voltText); // Find voltage text view by ID
        bacText = findViewById(R.id.bacText); // Find estimated BAC text view by ID
        tempText = findViewById(R.id.tempText); // Find temperature text view by ID
        humText = findViewById(R.id.humText); // Find humidity text view by ID
        connectButton = findViewById(R.id.connectButton); // Find connect button by ID

        // Register system network callback for WiFi connections
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); // Get system connectivity service
        NetworkRequest request = new NetworkRequest.Builder() // Create network request builder
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI) // Filter for WiFi networks only
                .build(); // Build network request object
        connectivityManager.registerNetworkCallback(request, wifiCallback); // Start listening for WiFi network state

        // Set click listener on connect button to toggle polling state
        connectButton.setOnClickListener(v -> { // Lambda for button click event listener
            if (polling) { // Check if polling is currently active
                stopPolling("Stopped"); // Stop polling if active
            } else { // Check if polling is inactive
                startPolling(); // Start polling if inactive
            } // End of condition check
        }); // End of click listener binding

        setStatus("Idle. Join the ESP32's wifi then press connect"); // Display initial instructions on UI
        updateButton(); // Update button label to match initial state
    } // End of onCreate method

    @Override // Overrides activity destruction lifecycle method
    protected void onDestroy() { // Called when activity is being destroyed
        super.onDestroy(); // Execute parent activity cleanup
        polling = false; // Stop polling loop flag
        if (executor != null) executor.shutdownNow(); // Immediately cancel background tasks
        try { // Try block to safely unregister network callback
            connectivityManager.unregisterNetworkCallback(wifiCallback); // Remove network callback listener
        } catch (IllegalArgumentException ignored) { // Catch exception if callback was already unregistered
        } // Ignore unregister exception
    } // End of onDestroy method

    // Starts background HTTP polling task
    private void startPolling() { // Helper method to start data polling
        polling = true; // Set polling flag to active
        setStatus("Connecting to " + DATA_URL + " ..."); // Update status text on UI
        updateButton(); // Refresh button label text
        executor = Executors.newSingleThreadScheduledExecutor(); // Create single background thread executor
        pollTask = executor.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, // Schedule recurring poll task
                TimeUnit.MILLISECONDS); // Specify time unit in milliseconds
    } // End of startPolling method

    // Stops background HTTP polling task
    private void stopPolling(String reason) { // Helper method to stop data polling
        polling = false; // Set polling flag to inactive
        if (pollTask != null) { // Check if polling task is active
            pollTask.cancel(true); // Cancel scheduled task execution
            pollTask = null; // Clear task reference
        } // End of pollTask check
        if (executor != null) { // Check if executor service exists
            executor.shutdownNow(); // Shutdown thread executor pool
            executor = null; // Clear executor reference
        } // End of executor check
        setStatus(reason); // Update status text with given reason
        updateButton(); // Refresh button label text
    } // End of stopPolling method

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
            } // End of response code check
            try (BufferedReader reader = new BufferedReader( // Create buffered reader for connection input stream
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { // Convert byte stream to UTF-8 text
                String line = reader.readLine(); // Read single response line from ESP32
                if (line != null) handleLine(line.trim()); // Parse line data if not null
            } // End of reader auto-close block
        } catch (IOException e) { // Catch network I/O exceptions
            Log.w(TAG, "Poll failed", e); // Log network warning details
            if (wifiNetwork == null) { // Check if WiFi network is unavailable
                pollStatus("Phone is not on a WiFi network. Join \"" + WIFI_NAME_HINT + "\" in Settings"); // Prompt user to connect WiFi
            } else { // Handle reachable network with unreachable host
                pollStatus("Can't reach the ESP32 (" + e.getClass().getSimpleName() // Display connection failure message
                        + "). Is the phone on \"" + WIFI_NAME_HINT + "\"?"); // Suggest checking network name
            } // End of network state check
        } catch (RuntimeException e) { // Catch unexpected runtime exceptions
            Log.e(TAG, "Poll crashed", e); // Log exception error stack trace
        } finally { // Cleanup block always executed
            if (conn != null) conn.disconnect(); // Disconnect HTTP connection to free resources
        } // End of try-catch-finally block
    } // End of pollOnce method

    // Parses CSV formatted string from ESP32: raw,volts,rawBAC,estBAC,temp,hum
    private void handleLine(String line) { // Helper method to parse received data line
        String[] f = line.split(","); // Split comma-separated values into array
        if (f.length != 6) { // Verify expected count of 6 elements
            pollStatus("ESP32 says: " + line); // Display raw output if formatting fails
            return; // Exit parser on invalid length
        } // End of field count validation
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
            }); // End of UI thread dispatch lambda
            pollStatus("Connected, receiving data"); // Update status text to connected state
        } catch (NumberFormatException e) { // Catch string to number conversion error
            pollStatus("Bad data from ESP32: " + line); // Report invalid number format on UI
        } // End of try-catch block
    } // End of handleLine method

    // Updates status message on UI thread
    private void setStatus(String s) { // Helper method to set status text safely
        runOnUiThread(() -> statusText.setText("Device: " + s)); // Set formatted status on UI thread
    } // End of setStatus method

    // Updates status message only if polling is currently active
    private void pollStatus(String s) { // Helper method for conditional status update
        if (polling) setStatus(s); // Update status if polling is active
    } // End of pollStatus method

    // Updates connect button text depending on polling state
    private void updateButton() { // Helper method to refresh button UI state
        runOnUiThread(() -> connectButton.setText(polling ? "Disconnect" : "Connect")); // Set button text on UI thread
    } // End of updateButton method
} // End of MainActivity class