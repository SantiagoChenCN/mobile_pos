package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class HomeScreen {
    private final Context context;
    private final AppLanguage language;
    private final HomeNavigation navigation;

    public HomeScreen(Context context, AppLanguage language, HomeNavigation navigation) {
        this.context = context;
        this.language = language;
        this.navigation = navigation;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 18, 16, 18);

        TextView title = Views.text(context, UiText.choose(language, "应急收银首页", "Inicio"), 28, StyleGuide.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title, Views.matchWrap());

        TextView subtitle = Views.text(
                context,
                UiText.choose(language, "离线商品、收银和导入维护", "Productos, caja e importacion sin conexion"),
                15,
                StyleGuide.MUTED
        );
        subtitle.setPadding(0, 6, 0, 18);
        page.addView(subtitle, Views.matchWrap());

        page.addView(entry(UiText.choose(language, "商品编辑", "Editar productos"), navigation::openProductEditing));
        page.addView(entry(UiText.choose(language, "收银", "Caja"), navigation::openCheckout));
        page.addView(entry(UiText.choose(language, "日账", "Resumen diario"), navigation::openDailySummary));
        page.addView(entry(UiText.choose(language, "设置", "Ajustes"), navigation::openSettings));
        page.addView(entry(UiText.choose(language, "导入", "Importar"), navigation::openImport));
        return page;
    }

    private View entry(String label, Runnable action) {
        Button button = Views.button(context, label);
        button.setTextSize(22);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(18, 18, 18, 18);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    public interface HomeNavigation {
        void openProductEditing();

        void openCheckout();

        void openDailySummary();

        void openSettings();

        void openImport();
    }
}
