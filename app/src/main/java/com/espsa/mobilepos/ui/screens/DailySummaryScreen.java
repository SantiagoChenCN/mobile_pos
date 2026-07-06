package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.core.ledger.DailySummary;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.time.LocalDate;

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

        DailySummary summary = services.ledger().dailySummary(LocalDate.now());
        page.addView(metric(UiText.choose(language, "今日销售额", "Total de hoy"), "$" + summary.total().amount(), true));
        page.addView(metric(UiText.choose(language, "订单数", "Ventas"), Integer.toString(summary.saleCount()), false));
        page.addView(metric(UiText.choose(language, "作废单", "Anuladas"), summary.voidedCount() + " / $" + summary.voidedTotal().amount(), false));
        page.addView(Views.divider(context));
        page.addView(metric("Efectivo", "$" + summary.totalFor(PaymentMethod.CASH).amount(), false));
        page.addView(metric("Mercado Pago", "$" + summary.totalFor(PaymentMethod.MERCADO_PAGO).amount(), false));
        page.addView(metric("Debito", "$" + summary.totalFor(PaymentMethod.DEBIT_CARD).amount(), false));
        page.addView(metric("Credito", "$" + summary.totalFor(PaymentMethod.CREDIT_CARD).amount(), false));
        page.addView(metric("transferencia", "$" + summary.totalFor(PaymentMethod.TRANSFERENCIA).amount(), false));
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

