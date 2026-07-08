package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        ScrollView scroll = new ScrollView(context);
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 16);

        TextView title = Views.text(context, UiText.choose(language, "设置 / Ajustes", "Ajustes"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(systemCard(), Views.cardParams(context));
        page.addView(languageCard(), Views.cardParams(context));
        page.addView(textScaleCard(), Views.cardParams(context));
        scroll.addView(page);
        return scroll;
    }

    private View systemCard() {
        LinearLayout card = Views.card(context);
        addCardTitle(card, UiText.choose(language, "基础信息", "Informacion basica"));
        card.addView(info(UiText.choose(language, "当前商品数", "Productos"), Integer.toString(services.catalog().productCount())));
        card.addView(info(UiText.choose(language, "App 版本", "Version"), "debug"));
        card.addView(info(UiText.choose(language, "模式", "Modo"), UiText.choose(language, "单机离线", "Local sin sincronizacion")));
        return card;
    }

    private View languageCard() {
        return Views.actionCard(
                context,
                UiText.choose(language, "语言", "Idioma"),
                UiText.choose(language, "切换收银、商品维护和导入页面的显示语言。", "Cambia el idioma de caja, productos e importacion."),
                language == AppLanguage.ZH ? "中文" : "Espanol",
                language == AppLanguage.ZH ? "Cambiar a Espanol" : "切换到中文",
                onToggleLanguage
        );
    }

    private View textScaleCard() {
        LinearLayout card = Views.card(context);
        addCardTitle(card, UiText.choose(language, "字体大小", "Tamano de letra"));
        card.addView(info(UiText.choose(language, "当前选择", "Seleccion actual"), currentTextScale.label(language)));

        TextView helper = Views.text(
                context,
                UiText.choose(language, "用于改善竖屏收银时的可读性。", "Mejora la lectura en pantalla vertical."),
                14,
                StyleGuide.MUTED
        );
        helper.setPadding(0, Views.dp(context, 6), 0, Views.dp(context, 8));
        card.addView(helper, Views.matchWrap());
        card.addView(textScaleButtons(), Views.matchWrap());
        return card;
    }

    private View textScaleButtons() {
        LinearLayout panel = Views.vertical(context);

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
            label = label + UiText.choose(language, " 选中", " activo");
        }
        Button button = Views.button(context, label);
        button.setEnabled(textScale != currentTextScale);
        button.setOnClickListener(v -> onTextScaleChanged.onTextScaleChanged(textScale));
        return button;
    }

    private void addCardTitle(LinearLayout card, String title) {
        TextView titleView = Views.text(context, title, 18, StyleGuide.INK);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleView, Views.matchWrap());
    }

    private View info(String label, String value) {
        return Views.infoBlock(context, label, value);
    }

    public interface TextScaleChangeHandler {
        void onTextScaleChanged(TextScale textScale);
    }
}
