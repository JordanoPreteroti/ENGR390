package com.coen390.engr390.controller;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import com.coen390.engr390.model.Reading;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the ESP32 over its WiFi access point (polls GET /data).
 * One shared instance, so the connection stays up while moving between screens.
 * Based on the polling code from the original MainActivity.
 */
public final class DeviceService {

    /**
     * true = generate fake readings (no ESP32 needed), false = poll the real device.
     * Remember to set this back to false before testing with the hardware.
     */
    public static final boolean SIMULATE = true;

    // Must match the ESP32 sketch
    public static final String WIFI_NAME = "Breathalyzer-ESP32";
    private static final String DATA_URL = "http://192.168.4.1/data";

    private static final long POLL_INTERVAL_MS = 500;
    private static final int TIMEOUT_MS = 2500;

    // Warm-up: the sensor is "ready" once the voltage stays steady for 10 s.
    private static final int STABLE_SAMPLES = 20;       // 20 x 500 ms = 10 s
    private static final double STABLE_RANGE_V = 0.05;

    public enum State { DISCONNECTED, CONNECTING, WARMING_UP, READY }

    public interface Listener {
        void onStateChanged(State state, String message);
        void onReading(Reading reading);
    }

    private static DeviceService instance;

    public static synchronized DeviceService getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceService(context.getApplicationContext());
        }
        return instance;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Double> recentVolts = new ArrayDeque<>(); // poll thread only

    private ScheduledExecutorService executor;
    private volatile Network wifiNetwork;
    private volatile boolean running = false;
    private volatile State state = State.DISCONNECTED;
    private volatile String message = "not connected";
    private volatile Reading latest;
    private volatile double baselineVolts = -1; // clean-air voltage, -1 until warmed up

    // Simulation state
    private final Random random = new Random();
    private long simStartMs;
    private volatile long simBlowUntilMs = 0;
    private volatile double simBreathTargetV = 0;
    private double simBreathV = 0; // poll thread only

    // Detects when the phone joins or leaves a WiFi network
    private final ConnectivityManager.NetworkCallback wifiCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    wifiNetwork = network;
                }

                @Override
                public void onLost(Network network) {
                    if (network.equals(wifiNetwork)) wifiNetwork = null;
                }
            };

    private DeviceService(Context appContext) {
        ConnectivityManager cm =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        cm.registerNetworkCallback(request, wifiCallback);
    }

    // ---------- Public API (call from the main thread) ----------

    public void addListener(Listener l) {
        listeners.add(l);
        l.onStateChanged(state, message); // give the new screen the current state right away
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        latest = null;
        baselineVolts = -1;
        simStartMs = System.currentTimeMillis();
        simBreathV = 0;
        simBlowUntilMs = 0;
        setState(State.CONNECTING, SIMULATE
                ? "connecting to simulated device..."
                : "connecting to " + WIFI_NAME + "...");
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(recentVolts::clear);
        executor.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        latest = null;
        baselineVolts = -1;
        setState(State.DISCONNECTED, "not connected");
    }

    public boolean isRunning() { return running; }
    public State getState() { return state; }
    public Reading getLatest() { return latest; }
    public double getBaselineVolts() { return baselineVolts; }

    /**
     * Simulation only: pretend someone blows into the device for 5 seconds.
     * Does nothing when using the real ESP32.
     */
    public void simulateBreath() {
        if (!SIMULATE) return;
        simBreathTargetV = 0.2 + random.nextDouble() * 3.3; // gives a BAC between about 0.02 and 0.27
        simBlowUntilMs = System.currentTimeMillis() + 5000;
    }

    // ---------- Polling (background thread) ----------

    private void pollOnce() {
        if (!running) return;
        if (SIMULATE) {
            simulateOnce();
            return;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DATA_URL);
            Network net = wifiNetwork;
            // Use the WiFi network explicitly, otherwise Android may send the
            // request over mobile data because the ESP32 has no internet.
            conn = (HttpURLConnection) (net != null ? net.openConnection(url) : url.openConnection());
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                setState(State.CONNECTING, "connected, waiting for the first reading");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null) handleLine(line.trim());
            }
        } catch (IOException e) {
            if (wifiNetwork == null) {
                setState(State.CONNECTING, "phone is not on WiFi. Join \"" + WIFI_NAME + "\" in Settings");
            } else {
                setState(State.CONNECTING, "can't reach the device. Is the phone on \"" + WIFI_NAME + "\"?");
            }
        } catch (RuntimeException ignored) {
            // e.g. a malformed response; try again on the next poll
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Builds a fake CSV line in the ESP32's format and feeds it to the normal parser. */
    private void simulateOnce() {
        long now = System.currentTimeMillis();
        double elapsedS = (now - simStartMs) / 1000.0;

        // Warm-up: the voltage drifts down from 1.2 V to 0.4 V over 8 s, then stays steady.
        double cleanAir = elapsedS < 8 ? 1.2 - 0.1 * elapsedS : 0.4;

        // Breath: jumps up while blowing, then decays back to clean air (recovery).
        if (now < simBlowUntilMs) {
            simBreathV = simBreathTargetV;
        } else {
            simBreathV *= 0.85;
            if (simBreathV < 0.005) simBreathV = 0;
        }

        double volts = cleanAir + simBreathV + (random.nextDouble() - 0.5) * 0.02;
        int raw = (int) (volts / 5.0 * 1023.0);
        double temp = 21.0 + random.nextDouble() * 0.5;
        double hum = 44.0 + random.nextDouble() * 2.0;
        double rawBac = 0.67 * volts * 0.1;

        handleLine(String.format(Locale.US, "%d,%.2f,%.4f,%.4f,%.1f,%.1f",
                raw, volts, rawBac, rawBac, temp, hum));
    }

    private void handleLine(String line) {
        String[] f = line.split(",");
        if (f.length != 6) {
            // e.g. "Failed to read from DHT sensor!"
            setState(state == State.DISCONNECTED ? State.CONNECTING : state, "device says: " + line);
            return;
        }
        Reading r;
        try {
            r = new Reading(
                    Integer.parseInt(f[0].trim()),
                    Double.parseDouble(f[1].trim()),
                    Double.parseDouble(f[2].trim()),
                    Double.parseDouble(f[3].trim()),
                    Double.parseDouble(f[4].trim()),
                    Double.parseDouble(f[5].trim()));
        } catch (NumberFormatException e) {
            setState(state, "bad data from device: " + line);
            return;
        }
        if (!running) return;

        latest = r;
        updateWarmUp(r.getVolts());
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onReading(r);
        });
    }

    private void updateWarmUp(double volts) {
        if (baselineVolts >= 0) {
            setState(State.READY, "connected, sensor ready");
            return;
        }
        recentVolts.addLast(volts);
        while (recentVolts.size() > STABLE_SAMPLES) recentVolts.removeFirst();

        if (recentVolts.size() == STABLE_SAMPLES) {
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0;
            for (double v : recentVolts) {
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
            }
            if (max - min < STABLE_RANGE_V) {
                baselineVolts = sum / STABLE_SAMPLES; // clean-air reference
                setState(State.READY, "connected, sensor ready");
                return;
            }
        }
        setState(State.WARMING_UP, "connected, sensor warming up");
    }

    private void setState(State newState, String newMessage) {
        if (newState == state && newMessage.equals(message)) return;
        state = newState;
        message = newMessage;
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onStateChanged(newState, newMessage);
        });
    }
}