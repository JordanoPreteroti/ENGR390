package com.coen390.engr390.view;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.coen390.engr390.R;
import com.coen390.engr390.controller.BACController;

import java.util.Locale;

/** Screen (d): BAC result, driving status and time until sober. Shows (f) when needed. */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_BAC = "bac";
    public static final String EXTRA_TEMP = "temp";
    public static final String EXTRA_HUM = "hum";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        double bac = getIntent().getDoubleExtra(EXTRA_BAC, 0);
        double temp = getIntent().getDoubleExtra(EXTRA_TEMP, 0);
        double hum = getIntent().getDoubleExtra(EXTRA_HUM, 0);
        BACController.Status status = BACController.getStatus(bac);

        setTitle("Result: " + name);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView tvBac = findViewById(R.id.tvBac);
        TextView tvStatus = findViewById(R.id.tvStatus);
        TextView tvSober = findViewById(R.id.tvSober);
        TextView tvConditions = findViewById(R.id.tvConditions);
        Button btnDone = findViewById(R.id.btnDone);

        tvBac.setText(StatusUi.formatBac(bac));
        tvStatus.setText(StatusUi.label(status));
        tvStatus.getBackground().mutate().setTint(StatusUi.boxBackground(this, status));
        tvStatus.setTextColor(StatusUi.boxText(this, status));
        tvSober.setText(soberText(bac));
        tvConditions.setText(String.format(Locale.US, "Temp %.0f °C   RH %.0f %%", temp, hum));
        btnDone.setOnClickListener(v -> finish());

        if (status == BACController.Status.DANGER) {
            showHighBacAlert(name, bac);
        }
    }

    private String soberText(double bac) {
        if (bac <= 0) {
            return "You are at 0.00%";
        }
        int totalMin = (int) Math.ceil(BACController.hoursUntilSober(bac) * 60);
        int h = totalMin / 60;
        int m = totalMin % 60;
        return "Back to 0.00% in about " + (h > 0 ? h + " h " : "") + m + " min";
    }

    /** Screen (f): warning for a dangerously high BAC. */
    private void showHighBacAlert(String name, double bac) {
        View v = getLayoutInflater().inflate(R.layout.dialog_high_bac, null);
        ((TextView) v.findViewById(R.id.tvAlertBac)).setText(StatusUi.formatBac(bac));
        ((TextView) v.findViewById(R.id.tvAlertName)).setText(name + ": very high BAC");
        ((TextView) v.findViewById(R.id.tvAlertMessage)).setText(
                "Signs of alcohol poisoning may appear. Do not leave " + name + " alone.");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).create();

        v.findViewById(R.id.btnCall911).setOnClickListener(b ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))));
        v.findViewById(R.id.btnSafeRide).setOnClickListener(b -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=taxi")));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show();
            }
        });
        v.findViewById(R.id.btnCloseAlert).setOnClickListener(b -> dialog.dismiss());

        dialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
