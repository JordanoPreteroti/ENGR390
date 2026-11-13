package com.coen390.engr390.view;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coen390.engr390.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Shared base for every screen: adds a toolbar (title + back arrow),
 * keeps content clear of the system bars, and handles light/dark mode.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String PREFS = "settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(loadNightMode());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.activity_base);
        FrameLayout container = findViewById(R.id.baseContainer);
        getLayoutInflater().inflate(layoutResID, container, true);

        MaterialToolbar toolbar = findViewById(R.id.baseToolbar);
        setSupportActionBar(toolbar);

        // Toolbar extends under the status bar; content stops above the navigation bar.
        View root = findViewById(R.id.baseRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            toolbar.setPadding(bars.left, bars.top, bars.right, 0);
            container.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_appearance, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_appearance) {
            showAppearanceDialog();
            return true;
        }
        return super.onOptionsItemSelected(item); // also handles the back arrow
    }

    private void showAppearanceDialog() {
        String[] labels = {"Follow system", "Light", "Dark"};
        int[] modes = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
        };
        int current = loadNightMode();
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveNightMode(modes[which]);
                    dialog.dismiss();
                    AppCompatDelegate.setDefaultNightMode(modes[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int loadNightMode() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void saveNightMode(int mode) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt(KEY_NIGHT_MODE, mode);
        editor.apply();
    }
}