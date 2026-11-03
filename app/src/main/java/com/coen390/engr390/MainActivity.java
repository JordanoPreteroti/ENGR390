package com.coen390.engr390;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Breathalyzer";

    private static final UUID SERIAL_SERVICE =
            UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL_CHAR =
            UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView statusText, adcText, voltText, bacText, tempText, humText;
    private Button connectButton;

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder buffer = new StringBuilder();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = !result.containsValue(false);
                        if (allGranted) {
                            startScan();
                        } else {
                            Toast.makeText(this, "Bluetooth permissions are required",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        adcText = findViewById(R.id.adcText);
        voltText = findViewById(R.id.voltText);
        bacText = findViewById(R.id.bacText);
        tempText = findViewById(R.id.tempText);
        humText = findViewById(R.id.humText);
        connectButton = findViewById(R.id.connectButton);

        connectButton.setOnClickListener(v -> ensurePermissions());
    }

    private void ensurePermissions() {
        String[] needed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed = new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            needed = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        boolean missing = false;
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing = true;
            }
        }
        if (missing) {
            permissionLauncher.launch(needed);
        } else {
            startScan();
        }
    }

    private void startScan() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Turn Bluetooth on first", Toast.LENGTH_LONG).show();
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null || scanning) return;

        scanning = true;
        setStatus("Scanning...");
        scanner.startScan(scanCallback);

        handler.postDelayed(() -> {
            if (scanning) {
                stopScan();
                setStatus("No device found");
            }
        }, 10_000);
    }

    private void stopScan() {
        if (scanner != null && scanning) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.toLowerCase().contains("bluno")) {
                stopScan();
                setStatus("Connecting...");
                gatt = device.connectGatt(MainActivity.this, false, gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected, status=" + status);
                g.close();
                gatt = null;
                setStatus("Disconnected (status " + status + ")");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService service = g.getService(SERIAL_SERVICE);
            if (service == null) {
                setStatus("Serial service 0xDFB0 not found");
                return;
            }
            BluetoothGattCharacteristic ch = service.getCharacteristic(SERIAL_CHAR);
            if (ch == null) {
                setStatus("Characteristic 0xDFB1 not found");
                return;
            }
            g.setCharacteristicNotification(ch, true);
            BluetoothGattDescriptor cccd = ch.getDescriptor(CCCD);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(cccd);
            }
            setStatus("Connected");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            byte[] bytes = ch.getValue();
            if (bytes == null) return;
            handleBytes(new String(bytes, StandardCharsets.UTF_8));
        }
    };

    private void handleBytes(String chunk) {
        buffer.append(chunk);
        int nl;
        while ((nl = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, nl).trim();
            buffer.delete(0, nl + 1);
            if (!line.isEmpty()) parseLine(line);
        }
    }

    private void parseLine(String line) {
        String[] f = line.split(",");
        if (f.length != 6) {
            Log.w(TAG, "Ignoring malformed line: " + line);
            return;
        }
        try {
            int raw = Integer.parseInt(f[0].trim());
            float volts = Float.parseFloat(f[1].trim());
            float estBac = Float.parseFloat(f[3].trim());
            float temp = Float.parseFloat(f[4].trim());
            float hum = Float.parseFloat(f[5].trim());

            runOnUiThread(() -> {
                adcText.setText("MQ-3 raw: " + raw);
                voltText.setText(String.format("MQ-3 voltage: %.2f V", volts));
                bacText.setText(String.format("Estimated BAC: %.4f", estBac));
                tempText.setText(String.format("Temperature: %.1f °C", temp));
                humText.setText(String.format("Humidity: %.1f %%", hum));
            });
        } catch (NumberFormatException e) {
            Log.w(TAG, "Bad number in line: " + line);
        }
    }

    private void setStatus(String s) {
        runOnUiThread(() -> statusText.setText("Device: " + s));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
    }
}