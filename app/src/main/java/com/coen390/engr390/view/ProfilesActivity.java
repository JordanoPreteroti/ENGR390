package com.coen390.engr390.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coen390.engr390.R;
import com.coen390.engr390.data.DatabaseHelper;
import com.coen390.engr390.model.Profile;

import java.util.List;

/** Screen (b): everyone in the group with their current BAC. */
public class ProfilesActivity extends BaseActivity implements ProfileAdapter.Listener {

    private DatabaseHelper db;
    private ProfileAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        setTitle("Profiles");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = DatabaseHelper.getInstance(this);
        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.rvProfiles);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new ProfileAdapter(this);
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAddProfile);
        Button btnLeaderboard = findViewById(R.id.btnLeaderboard);
        // region Profiles: Add opens the shared add/edit dialog with no profile
        btnAdd.setOnClickListener(v -> showProfileDialog(null));
        // endregion
        btnLeaderboard.setOnClickListener(v ->
                startActivity(new Intent(this, LeaderboardActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // picks up new results when coming back from a test
    }

    private void refresh() {
        List<Profile> profiles = db.getAllProfiles();
        adapter.setProfiles(profiles);
        tvEmpty.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onProfileClick(Profile profile) {
        Intent i = new Intent(this, TestActivity.class);
        i.putExtra(TestActivity.EXTRA_PROFILE_ID, profile.getId());
        startActivity(i);
    }

    @Override
    public void onProfileLongClick(Profile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + profile.getName() + "?")
                .setMessage("Their reading history will also be deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteProfile(profile.getId());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // region Profiles: add / edit dialog (existing == null adds, otherwise edits that profile)
    @Override
    public void onProfileEdit(Profile profile) {
        showProfileDialog(profile);
    }

    private void showProfileDialog(Profile existing) {
        View form = getLayoutInflater().inflate(R.layout.dialog_add_profile, null);
        EditText etName = form.findViewById(R.id.etName);
        EditText etWeight = form.findViewById(R.id.etWeight);
        RadioGroup rgSex = form.findViewById(R.id.rgSex);
        if (existing != null) {
            etName.setText(existing.getName());
            etWeight.setText(String.valueOf(existing.getWeightKg()));
            rgSex.check("F".equals(existing.getSex()) ? R.id.rbFemale : R.id.rbMale);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add profile" : getString(R.string.edit_profile))
                .setView(form)
                .setPositiveButton(existing == null ? "Add" : getString(R.string.save_profile), null) // set below so we can validate first
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        etName.setError("Enter a name");
                        return;
                    }
                    double weight;
                    try {
                        weight = Double.parseDouble(etWeight.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        etWeight.setError("Enter a weight in kg");
                        return;
                    }
                    if (weight < 30 || weight > 300) {
                        etWeight.setError("Enter a weight between 30 and 300 kg");
                        return;
                    }
                    String sex = rgSex.getCheckedRadioButtonId() == R.id.rbFemale ? "F" : "M";

                    if (existing == null) {
                        db.addProfile(name, weight, sex);
                    } else {
                        db.updateProfile(new Profile(existing.getId(), name, weight, sex,
                                existing.getLastBac(), existing.getLastTestTime(),
                                existing.getPoints(), existing.getDrinks()));
                    }
                    dialog.dismiss();
                    refresh();
                }));
        dialog.show();
    }
    // endregion

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
