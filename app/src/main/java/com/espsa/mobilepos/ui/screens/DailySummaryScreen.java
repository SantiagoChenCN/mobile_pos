package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.time.ArgentinaTime;
import com.espsa.mobilepos.core.ledger.DailySummary;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.MoneyText;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class DailySummaryScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;

    public DailySummaryScreen(Context context, AppServices services, AppLanguage language) {
        this.context = context;
        this.services = services;
        this.language = language;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "每日总账 / Resumen diario", "Resumen diario"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        DailySummary summary = services.ledger().dailySummary(ArgentinaTime.today());
        page.addView(metric(UiText.choose(language, "今日销售额", "Total de hoy"), MoneyText.currency(summary.total()), true));
        page.addView(metric(UiText.choose(language, "订单数", "Ventas"), Integer.toString(summary.saleCount()), false));
        page.addView(metric(UiText.choose(language, "作废单", "Anuladas"), summary.voidedCount() + " / " + MoneyText.currency(summary.voidedTotal()), false));
        page.addView(Views.divider(context));
        page.addView(metric("Efectivo", MoneyText.currency(summary.totalFor(PaymentMethod.CASH)), false));
        page.addView(metric("Mercado Pago", MoneyText.currency(summary.totalFor(PaymentMethod.MERCADO_PAGO)), false));
        page.addView(metric("Debito", MoneyText.currency(summary.totalFor(PaymentMethod.DEBIT_CARD)), false));
        page.addView(metric("Credito", MoneyText.currency(summary.totalFor(PaymentMethod.CREDIT_CARD)), false));
        page.addView(metric("transferencia", MoneyText.currency(summary.totalFor(PaymentMethod.TRANSFERENCIA)), false));
        return page;
    }

    private View metric(String label, String value, boolean large) {
        LinearLayout row = Views.vertical(context);
        row.setPadding(0, 14, 0, 14);
        TextView labelView = Views.text(context, label, 14, StyleGuide.MUTED);
        row.addView(labelView, Views.matchWrap());
        TextView valueView = Views.text(context, value, large ? 34 : 24, StyleGuide.INK);
        valueView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        row.addView(valueView, Views.matchWrap());
        return row;
    }
}
