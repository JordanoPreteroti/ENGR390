package com.coen390.engr390.view;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coen390.engr390.R;
import com.coen390.engr390.theme.ThemeToggle;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Shared base for every screen: adds a toolbar (title + back arrow),
 * keeps content clear of the system bars, and adds the light/dark theme toggle.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeToggle.applySaved(this);
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
        ThemeToggle.attach(this);
    }
}
