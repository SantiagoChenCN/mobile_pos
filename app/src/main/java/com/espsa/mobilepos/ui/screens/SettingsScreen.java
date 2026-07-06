package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ImportGateway;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class SettingsScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final ImportGateway importGateway;

    public SettingsScreen(Context context, AppServices services, AppLanguage language, ImportGateway importGateway) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.importGateway = importGateway;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "设置 / Ajustes", "Ajustes"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(info(UiText.choose(language, "当前商品数", "Productos"), Integer.toString(services.catalog().productCount())));
        page.addView(info("Android", "10+"));
        page.addView(info(UiText.choose(language, "模式", "Modo"), UiText.choose(language, "单机离线", "Local sin sincronizacion")));

        Button importButton = Views.button(context, UiText.choose(language, "导入鸣盛商品库 .db", "Importar base .db"));
        importButton.setOnClickListener(v -> importGateway.requestImportFile());
        page.addView(importButton, Views.matchWrap());

        TextView note = Views.text(context, UiText.choose(
                language,
                "请选择 AGT_MAIN_20260705.db 这类鸣盛 SQLite 商品库。导入后会保存到手机本地。",
                "Elija una base SQLite tipo AGT_MAIN_20260705.db. Queda guardada en el telefono."
        ), 14, StyleGuide.MUTED);
        note.setPadding(0, 12, 0, 0);
        page.addView(note, Views.matchWrap());

        if (!services.lastImportMessage().isEmpty()) {
            page.addView(info(UiText.choose(language, "最近导入", "Ultima importacion"), services.lastImportMessage()));
        }
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
