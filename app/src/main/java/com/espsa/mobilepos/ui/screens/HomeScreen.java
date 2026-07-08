package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        ScrollView scroll = new ScrollView(context);
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 18, 16, 16);

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
        subtitle.setPadding(0, Views.dp(context, 6), 0, Views.dp(context, 18));
        page.addView(subtitle, Views.matchWrap());

        page.addView(entry(
                UiText.choose(language, "收银", "Caja"),
                UiText.choose(language, "扫码、搜索商品、现金找零和快速结账。", "Escanear, buscar productos, calcular cambio y vender rapido."),
                UiText.choose(language, "日常主流程", "Flujo principal"),
                UiText.choose(language, "进入收银", "Abrir caja"),
                navigation::openCheckout
        ), Views.cardParams(context));
        page.addView(entry(
                UiText.choose(language, "商品编辑", "Editar productos"),
                UiText.choose(language, "维护条码、名称、售价、分类、单位和促销。", "Mantener codigos, nombres, precios, categorias, unidades y promociones."),
                UiText.choose(language, "本地商品库", "Catalogo local"),
                UiText.choose(language, "管理商品", "Gestionar productos"),
                navigation::openProductEditing
        ), Views.cardParams(context));
        page.addView(entry(
                UiText.choose(language, "导入商品库", "Importar productos"),
                UiText.choose(language, "支持鸣盛 .db 和通用 CSV 商品表。", "Soporta base Ming Sheng .db y tablas CSV genericas."),
                UiText.choose(language, "DB / CSV", "DB / CSV"),
                UiText.choose(language, "选择导入", "Elegir importacion"),
                navigation::openImport
        ), Views.cardParams(context));
        page.addView(entry(
                UiText.choose(language, "每日总账", "Resumen diario"),
                UiText.choose(language, "查看今日销售、作废和付款方式汇总。", "Revisar ventas, anulaciones y medios de pago del dia."),
                UiText.choose(language, "销售复盘", "Control del dia"),
                UiText.choose(language, "查看总账", "Ver resumen"),
                navigation::openDailySummary
        ), Views.cardParams(context));
        page.addView(entry(
                UiText.choose(language, "设置", "Ajustes"),
                UiText.choose(language, "切换语言、字体大小和离线工作信息。", "Cambiar idioma, tamano de letra e informacion local."),
                UiText.choose(language, "设备偏好", "Preferencias"),
                UiText.choose(language, "打开设置", "Abrir ajustes"),
                navigation::openSettings
        ), Views.cardParams(context));

        scroll.addView(page);
        return scroll;
    }

    private View entry(String title, String description, String meta, String actionLabel, Runnable action) {
        return Views.actionCard(context, title, description, meta, actionLabel, action);
    }

    public interface HomeNavigation {
        void openProductEditing();

        void openCheckout();

        void openDailySummary();

        void openSettings();

        void openImport();
    }
}
