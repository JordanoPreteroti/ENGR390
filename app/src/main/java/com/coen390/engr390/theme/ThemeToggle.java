package com.coen390.engr390.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coen390.engr390.R;

public class ThemeToggle {

    private static final String PREFS_NAME = "theme";
    private static final String KEY_NIGHT_MODE = "night_mode";

    // Applies the saved night mode, call before super.onCreate
    public static void applySaved(Context context) {
        AppCompatDelegate.setDefaultNightMode(savedMode(context));
    }

    // Adds the theme button over the activity content and shows the mode menu on click
    public static void attach(AppCompatActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        activity.getLayoutInflater().inflate(R.layout.theme_toggle_button, content, true);
        View button = content.getChildAt(content.getChildCount() - 1);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) button.getLayoutParams();
        int baseMargin = params.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(button, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = baseMargin + bars.bottom;
            v.setLayoutParams(lp);
            return insets;
        });

        button.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(activity, v);
            popup.inflate(R.menu.theme_menu);
            int saved = savedMode(activity);
            int checkedId = saved == AppCompatDelegate.MODE_NIGHT_NO ? R.id.theme_light
                    : saved == AppCompatDelegate.MODE_NIGHT_YES ? R.id.theme_dark
                    : R.id.theme_system;
            popup.getMenu().findItem(checkedId).setChecked(true);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                int mode = id == R.id.theme_light ? AppCompatDelegate.MODE_NIGHT_NO
                        : id == R.id.theme_dark ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_NIGHT_MODE, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
                return true;
            });
            popup.show();
        });
    }

    private static int savedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
