package com.espsa.mobilepos.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

public final class StyleGuide {
    public static final int INK = Color.rgb(17, 24, 39);
    public static final int PAPER = Color.rgb(249, 250, 251);
    public static final int SURFACE = Color.WHITE;
    public static final int LINE = Color.rgb(209, 213, 219);
    public static final int TEAL = Color.rgb(15, 118, 110);
    public static final int RED = Color.rgb(185, 28, 28);
    public static final int AMBER = Color.rgb(180, 83, 9);
    public static final int MUTED = Color.rgb(75, 85, 99);

    private StyleGuide() {
    }

    public static void pageTitle(TextView view) {
        view.setTextColor(INK);
        view.setTextSize(24);
        view.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static void total(TextView view) {
        view.setTextColor(INK);
        view.setTextSize(34);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    }

    public static void label(TextView view) {
        view.setTextColor(MUTED);
        view.setTextSize(13);
    }

    public static void card(View view) {
        view.setBackgroundColor(SURFACE);
        view.setPadding(18, 16, 18, 16);
    }
}

