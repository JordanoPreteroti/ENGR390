package com.coen390.engr390.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.coen390.engr390.R;
import com.coen390.engr390.controller.DeviceService;
import com.coen390.engr390.model.Reading;

import java.util.Locale;

/** Screen (a): connect to the breathalyzer over WiFi and start a session. */
public class ConnectActivity extends AppCompatActivity implements DeviceService.Listener {

    private DeviceService device;

    private TextView tvDeviceStatus;
    private TextView tvLiveValues;
    private TextView tvSensor;
    private Button btnConnect;
    private Button btnStartSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        setTitle("Breathalyzer");

        device = DeviceService.getInstance(this);

        TextView tvHint = findViewById(R.id.tvHint);
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        tvLiveValues = findViewById(R.id.tvLiveValues);
        tvSensor = findViewById(R.id.tvSensor);
        btnConnect = findViewById(R.id.btnConnect);
        btnStartSession = findViewById(R.id.btnStartSession);

        tvHint.setText("Join the \"" + DeviceService.WIFI_NAME
                + "\" WiFi in your phone's settings, then tap Connect.");

        btnConnect.setOnClickListener(v -> {
            if (device.isRunning()) {
                device.stop();
            } else {
                device.start();
            }
        });
        btnStartSession.setOnClickListener(v ->
                startActivity(new Intent(this, ProfilesActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        device.addListener(this);
    }

    @Override
    protected void onStop() {
        device.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged(DeviceService.State state, String message) {
        tvDeviceStatus.setText("Device: " + message);
        btnConnect.setText(device.isRunning() ? "Disconnect" : "Connect to device");

        boolean receiving = state == DeviceService.State.WARMING_UP
                || state == DeviceService.State.READY;
        btnStartSession.setEnabled(receiving);

        if (state == DeviceService.State.READY) {
            tvSensor.setText("Sensor: ready");
        } else if (state == DeviceService.State.WARMING_UP) {
            tvSensor.setText("Sensor: warming up");
        } else {
            tvSensor.setText("Sensor: --");
            tvLiveValues.setText("Live values: --");
        }
    }

    @Override
    public void onReading(Reading r) {
        tvLiveValues.setText(String.format(Locale.US, "MQ-3 %.2f V   %.1f °C   %.0f %% RH",
                r.getVolts(), r.getTempC(), r.getHumidity()));
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            device.stop(); // leaving the app: stop polling the ESP32
        }
        super.onDestroy();
    }
}
