package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class SettingsScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final Runnable onToggleLanguage;

    public SettingsScreen(Context context, AppServices services, AppLanguage language, Runnable onToggleLanguage) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.onToggleLanguage = onToggleLanguage;
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

        Button languageButton = Views.button(context, language == AppLanguage.ZH ? "Cambiar a Espanol" : "切换到中文");
        languageButton.setOnClickListener(v -> onToggleLanguage.run());
        page.addView(languageButton, Views.matchWrap());
        return page;
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
}
