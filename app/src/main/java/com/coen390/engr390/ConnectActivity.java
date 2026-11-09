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
import android.content.Intent;
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
public class ConnectActivity extends AppCompatActivity {

    private static final String TAG = "Breathalyzer";

    private static final UUID SERIAL_SERVICE =
            UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL_CHAR =
            UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView statusText, batteryText, sensorText;
    private Button connectButton, startButton;

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder buffer = new StringBuilder();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (!result.containsValue(false)) {
                            startScan();
                        } else {
                            Toast.makeText(this, "Bluetooth permissions are required",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connect_activity);

        statusText = findViewById(R.id.statusText);
        batteryText = findViewById(R.id.batteryText);
        sensorText = findViewById(R.id.sensorText);
        connectButton = findViewById(R.id.connectButton);
        startButton = findViewById(R.id.startButton);

        connectButton.setOnClickListener(v -> ensurePermissions());
        startButton.setOnClickListener(v ->
                startActivity(new Intent(this, ProfilesActivity.class)));
    }

    private void ensurePermissions() {
        String[] needed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed = new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            needed = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(needed);
                return;
            }
        }
        startScan();
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
                gatt = device.connectGatt(ConnectActivity.this, false, gattCallback,
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
                g.close();
                gatt = null;
                setStatus("Disconnected");
                runOnUiThread(() -> {
                    startButton.setEnabled(false);
                    sensorText.setText("Sensor: --");
                    batteryText.setText("Device battery: --");
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService service = g.getService(SERIAL_SERVICE);
            if (service == null) {
                setStatus("Serial service not found");
                return;
            }
            BluetoothGattCharacteristic ch = service.getCharacteristic(SERIAL_CHAR);
            if (ch == null) {
                setStatus("Characteristic not found");
                return;
            }

            g.setCharacteristicNotification(ch, true);
            BluetoothGattDescriptor cccd = ch.getDescriptor(CCCD);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(cccd);
            }

            setStatus("Connected");
            runOnUiThread(() -> startButton.setEnabled(true));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            byte[] bytes = ch.getValue();
            if (bytes != null) {
                handleBytes(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    };

    private void handleBytes(String chunk) {
        buffer.append(chunk);
        int end;
        while ((end = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, end).trim();
            buffer.delete(0, end + 1);
            if (!line.isEmpty()) parseLine(line);
        }
    }

    private void parseLine(String line) {
        String[] fields = line.split(",");
        if (fields.length != 7) {
            Log.w(TAG, "Malformed line: " + line);
            return;
        }
        try {
            Integer.parseInt(fields[0].trim());
            int battery = Integer.parseInt(fields[6].trim());
            Log.d(TAG, "Packet: " + line);
            runOnUiThread(() -> {
                sensorText.setText("Sensor: OK");
                batteryText.setText("Device battery: " + battery + "%");
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