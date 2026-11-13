package com.coen390.engr390.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.coen390.engr390.R;
import com.coen390.engr390.controller.BACController;
import com.coen390.engr390.controller.DeviceService;
import com.coen390.engr390.data.DatabaseHelper;
import com.coen390.engr390.model.Profile;
import com.coen390.engr390.model.Reading;

/**
 * Screen (c): waits until the sensor is warm and back to clean air,
 * then records the peak reading during a 5-second blow.
 */
public class TestActivity extends BaseActivity implements DeviceService.Listener {

    public static final String EXTRA_PROFILE_ID = "profile_id";

    private static final long BLOW_MS = 5000;
    private static final long TICK_MS = 50;
    /** Sensor must be within this much of its clean-air voltage before a new test. */
    private static final double RECOVERY_MARGIN_V = 0.15;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private DatabaseHelper db;
    private DeviceService device;
    private Profile profile;

    private TextView tvState;
    private TextView tvStateSub;
    private TextView tvInstruction;
    private ProgressBar progressBlow;
    private Button btnStartTest;

    private boolean blowing = false;
    private long blowStart;
    private Reading peak; // highest MQ-3 voltage seen during the blow

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        db = DatabaseHelper.getInstance(this);
        device = DeviceService.getInstance(this);
        profile = db.getProfile(getIntent().getLongExtra(EXTRA_PROFILE_ID, -1));
        if (profile == null) {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setTitle("Breath test: " + profile.getName());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvState = findViewById(R.id.tvState);
        tvStateSub = findViewById(R.id.tvStateSub);
        tvInstruction = findViewById(R.id.tvInstruction);
        progressBlow = findViewById(R.id.progressBlow);
        btnStartTest = findViewById(R.id.btnStartTest);

        btnStartTest.setOnClickListener(v -> startBlow());
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

    // ---------- Device updates ----------

    @Override
    public void onStateChanged(DeviceService.State state, String message) {
        updateReadiness();
    }

    @Override
    public void onReading(Reading r) {
        if (blowing) {
            if (peak == null || r.getVolts() > peak.getVolts()) {
                peak = r;
            }
        } else {
            updateReadiness(); // shows "Recovering" until the sensor clears
        }
    }

    private void updateReadiness() {
        if (blowing) return;

        DeviceService.State state = device.getState();
        if (state == DeviceService.State.DISCONNECTED || state == DeviceService.State.CONNECTING) {
            show("No device", "go back and connect", false);
            return;
        }
        if (state == DeviceService.State.WARMING_UP) {
            show("Warming up", "please wait", false);
            return;
        }
        Reading latest = device.getLatest();
        if (latest != null
                && latest.getVolts() > device.getBaselineVolts() + RECOVERY_MARGIN_V) {
            show("Recovering", "sensor clearing", false);
            return;
        }
        show("Ready", "sensor warmed up", true);
    }

    private void show(String title, String subtitle, boolean canStart) {
        tvState.setText(title);
        tvStateSub.setText(subtitle);
        btnStartTest.setEnabled(canStart);
    }

    // ---------- The blow ----------

    private void startBlow() {
        blowing = true;
        peak = null;
        btnStartTest.setEnabled(false);
        tvState.setText("Blow");
        tvStateSub.setText("steady breath");
        tvInstruction.setText("Keep blowing...");
        blowStart = SystemClock.uptimeMillis();
        device.simulateBreath(); // only does something when DeviceService.SIMULATE is true
        handler.post(progressTick);
    }

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis() - blowStart;
            progressBlow.setProgress((int) Math.min(100, elapsed * 100 / BLOW_MS));
            if (elapsed >= BLOW_MS) {
                finishBlow();
            } else {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private void finishBlow() {
        blowing = false;

        if (peak == null) {
            Toast.makeText(this, "No data received from the device. Try again.",
                    Toast.LENGTH_LONG).show();
            progressBlow.setProgress(0);
            tvInstruction.setText("Blow steadily for 5 seconds");
            updateReadiness();
            return;
        }

        double bac = BACController.estimateBac(peak.getVolts(), peak.getTempC(),
                peak.getHumidity(), device.getBaselineVolts());
        db.addReading(profile.getId(), bac, BACController.pointsFor(bac));

        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_NAME, profile.getName());
        i.putExtra(ResultActivity.EXTRA_BAC, bac);
        i.putExtra(ResultActivity.EXTRA_TEMP, peak.getTempC());
        i.putExtra(ResultActivity.EXTRA_HUM, peak.getHumidity());
        startActivity(i);
        finish(); // so Back from the result goes to the profiles list
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}