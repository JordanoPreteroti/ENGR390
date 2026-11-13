package com.coen390.engr390.view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coen390.engr390.R;
import com.coen390.engr390.data.DatabaseHelper;
import com.coen390.engr390.model.Profile;

import java.util.List;

/** Screen (e): tonight's ranking by points. */
public class LeaderboardActivity extends BaseActivity {

    private DatabaseHelper db;
    private LeaderboardAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        setTitle("Tonight's session");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = DatabaseHelper.getInstance(this);
        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.rvLeaderboard);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new LeaderboardAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btnNewSession).setOnClickListener(v -> confirmNewSession());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        refresh();
    }

    private void refresh() {
        List<Profile> profiles = db.getLeaderboard();
        adapter.setProfiles(profiles);
        tvEmpty.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmNewSession() {
        new AlertDialog.Builder(this)
                .setTitle("Start a new session?")
                .setMessage("Points and drink counts go back to 0. Reading history is kept.")
                .setPositiveButton("Start", (d, w) -> {
                    db.resetSession();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
