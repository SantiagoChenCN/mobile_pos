package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.TextScale;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class SettingsScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final Runnable onToggleLanguage;
    private final TextScale currentTextScale;
    private final TextScaleChangeHandler onTextScaleChanged;

    public SettingsScreen(
            Context context,
            AppServices services,
            AppLanguage language,
            Runnable onToggleLanguage,
            TextScale currentTextScale,
            TextScaleChangeHandler onTextScaleChanged
    ) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.onToggleLanguage = onToggleLanguage;
        this.currentTextScale = currentTextScale == null ? TextScale.NORMAL : currentTextScale;
        this.onTextScaleChanged = onTextScaleChanged;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "设置 / Ajustes", "Ajustes"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(info(UiText.choose(language, "当前商品数", "Productos"), Integer.toString(services.catalog().productCount())));
        page.addView(info(UiText.choose(language, "App 版本", "Version"), "debug"));
        page.addView(info(UiText.choose(language, "模式", "Modo"), UiText.choose(language, "单机离线", "Local sin sincronizacion")));
        page.addView(info(UiText.choose(language, "当前语言", "Idioma"), language == AppLanguage.ZH ? "中文" : "Espanol"));
        page.addView(info(UiText.choose(language, "字体大小", "Tamano de letra"), currentTextScale.label(language)));

        Button languageButton = Views.button(context, language == AppLanguage.ZH ? "Cambiar a Espanol" : "切换到中文");
        languageButton.setOnClickListener(v -> onToggleLanguage.run());
        page.addView(languageButton, Views.matchWrap());
        page.addView(textScaleButtons(), Views.matchWrap());
        return page;
    }

    private View textScaleButtons() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 14, 0, 0);

        TextView label = Views.text(context, UiText.choose(language, "选择字体大小", "Elegir tamano de letra"), 14, StyleGuide.MUTED);
        panel.addView(label, Views.matchWrap());

        LinearLayout firstRow = Views.horizontal(context);
        firstRow.addView(textScaleButton(TextScale.SMALL), Views.weight(1));
        firstRow.addView(textScaleButton(TextScale.NORMAL), Views.weight(1));
        panel.addView(firstRow, Views.matchWrap());

        LinearLayout secondRow = Views.horizontal(context);
        secondRow.addView(textScaleButton(TextScale.LARGE), Views.weight(1));
        secondRow.addView(textScaleButton(TextScale.EXTRA_LARGE), Views.weight(1));
        panel.addView(secondRow, Views.matchWrap());
        return panel;
    }

    private Button textScaleButton(TextScale textScale) {
        String label = textScale.label(language);
        if (textScale == currentTextScale) {
            label = label + UiText.choose(language, " ✓", " ✓");
        }
        Button button = Views.button(context, label);
        button.setEnabled(textScale != currentTextScale);
        button.setOnClickListener(v -> onTextScaleChanged.onTextScaleChanged(textScale));
        return button;
    }

    private View info(String label, String value) {
        LinearLayout row = Views.vertical(context);
        row.setPadding(0, 14, 0, 14);
        TextView labelView = Views.text(context, label, 14, StyleGuide.MUTED);
        row.addView(labelView, Views.matchWrap());
        TextView valueView = Views.text(context, value, 22, StyleGuide.INK);
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(valueView, Views.matchWrap());
        return row;
    }

    public interface TextScaleChangeHandler {
        void onTextScaleChanged(TextScale textScale);
    }
}
