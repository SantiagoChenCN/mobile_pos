package com.espsa.mobilepos.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

public final class Views {
    private Views() {
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static TextView text(Context context, String value, int sizeSp, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(StyleGuide.scaledSp(sizeSp));
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    public static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(StyleGuide.scaledSp(16));
        button.setAllCaps(false);
        button.setMinHeight(48);
        return button;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.setBackground(cardBackground(context));
        return card;
    }

    public static View actionCard(
            Context context,
            String title,
            String description,
            String meta,
            String actionLabel,
            Runnable action
    ) {
        LinearLayout card = card(context);

        TextView titleView = text(context, title, 19, StyleGuide.INK);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleView, matchWrap());

        if (description != null && !description.trim().isEmpty()) {
            TextView descriptionView = text(context, description, 14, StyleGuide.MUTED);
            descriptionView.setPadding(0, dp(context, 6), 0, 0);
            descriptionView.setSingleLine(false);
            card.addView(descriptionView, matchWrap());
        }

        if (meta != null && !meta.trim().isEmpty()) {
            TextView metaView = text(context, meta, 13, StyleGuide.TEAL);
            metaView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            metaView.setPadding(0, dp(context, 8), 0, 0);
            card.addView(metaView, matchWrap());
        }

        Button button = button(context, actionLabel);
        button.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(context, 12), 0, 0);
        card.addView(button, params);
        return card;
    }

    public static View infoBlock(Context context, String label, String value) {
        LinearLayout block = vertical(context);
        block.setPadding(0, dp(context, 6), 0, dp(context, 6));
        TextView labelView = text(context, label, 13, StyleGuide.MUTED);
        block.addView(labelView, matchWrap());
        TextView valueView = text(context, value, 20, StyleGuide.INK);
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        valueView.setSingleLine(false);
        block.addView(valueView, matchWrap());
        return block;
    }

    public static EditText editText(Context context) {
        EditText input = new EditText(context);
        input.setTextSize(StyleGuide.scaledSp(16));
        return input;
    }

    public static ArrayAdapter<String> spinnerAdapter(Context context, String[] labels) {
        return spinnerAdapter(context, Arrays.asList(labels));
    }

    public static ArrayAdapter<String> spinnerAdapter(Context context, List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                labels
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextSize(StyleGuide.scaledSp(16));
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextSize(StyleGuide.scaledSp(16));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(context, 10));
        return params;
    }

    public static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(StyleGuide.LINE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable cardBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(StyleGuide.SURFACE);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, 1), StyleGuide.LINE);
        return drawable;
    }
}
