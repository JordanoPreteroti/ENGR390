package com.coen390.engr390.view;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.coen390.engr390.R;
import com.coen390.engr390.controller.BACController.Status;

import java.util.Locale;

/** Colours and text for each BAC status, shared by all screens. */
public final class StatusUi {

    private StatusUi() { }

    public static int dotColor(Context c, Status s) {
        switch (s) {
            case UNDER:
                return ContextCompat.getColor(c, R.color.bz_green);
            case CAUTION:
                return ContextCompat.getColor(c, R.color.bz_yellow);
            default:
                return ContextCompat.getColor(c, R.color.bz_red);
        }
    }

    public static int boxBackground(Context c, Status s) {
        switch (s) {
            case UNDER:
                return ContextCompat.getColor(c, R.color.bz_under_bg);
            case CAUTION:
                return ContextCompat.getColor(c, R.color.bz_caution_bg);
            default:
                return ContextCompat.getColor(c, R.color.bz_over_bg);
        }
    }

    public static int boxText(Context c, Status s) {
        switch (s) {
            case UNDER:
                return ContextCompat.getColor(c, R.color.bz_under_text);
            case CAUTION:
                return ContextCompat.getColor(c, R.color.bz_caution_text);
            default:
                return ContextCompat.getColor(c, R.color.bz_over_text);
        }
    }

    public static String label(Status s) {
        switch (s) {
            case UNDER:
                return "Under the limit";
            case CAUTION:
                return "Do not drive";
            case OVER:
                return "Over the limit: do not drive";
            default:
                return "Very high BAC: get help";
        }
    }

    public static String formatBac(double bac) {
        return String.format(Locale.US, "%.2f%%", bac);
    }
}
