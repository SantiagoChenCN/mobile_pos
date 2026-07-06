package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.ledger.SaleLine;
import com.espsa.mobilepos.core.model.SaleStatus;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.time.LocalDate;
import java.util.List;

public final class SalesScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final Runnable refresh;

    public SalesScreen(Context context, AppServices services, AppLanguage language, Runnable refresh) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.refresh = refresh;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "交易明细 / Ventas", "Ventas"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = Views.vertical(context);
        List<Sale> sales = services.ledger().salesForDate(LocalDate.now());
        if (sales.isEmpty()) {
            TextView empty = Views.text(context, UiText.choose(language, "今天还没有销售记录", "Sin ventas hoy"), 18, StyleGuide.MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 64, 0, 64);
            list.addView(empty, Views.matchWrap());
        } else {
            for (Sale sale : sales) {
                list.addView(saleRow(sale), Views.matchWrap());
                list.addView(Views.divider(context));
            }
        }
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private View saleRow(Sale sale) {
        LinearLayout row = Views.vertical(context);
        row.setPadding(0, 14, 0, 14);

        TextView top = Views.text(context, sale.id() + "  $" + sale.total().amount(), 18, StyleGuide.INK);
        top.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(top, Views.matchWrap());

        TextView meta = Views.text(context, sale.paymentMethod().name() + "  " + sale.status().name(), 14, sale.status() == SaleStatus.VOIDED ? StyleGuide.RED : StyleGuide.MUTED);
        row.addView(meta, Views.matchWrap());

        LinearLayout actions = Views.horizontal(context);
        Button detail = Views.button(context, UiText.choose(language, "查看", "Ver"));
        detail.setOnClickListener(v -> showDetail(sale));
        actions.addView(detail, Views.weight(1));

        Button voidSale = Views.button(context, UiText.choose(language, "作废", "Anular"));
        voidSale.setEnabled(sale.status() != SaleStatus.VOIDED);
        voidSale.setOnClickListener(v -> confirmVoid(sale));
        actions.addView(voidSale, Views.weight(1));
        row.addView(actions, Views.matchWrap());
        return row;
    }

    private void showDetail(Sale sale) {
        StringBuilder detail = new StringBuilder();
        detail.append(sale.id()).append("\n");
        detail.append(sale.paymentMethod().name()).append("  $").append(sale.total().amount()).append("\n\n");
        for (SaleLine line : sale.lines()) {
            detail.append(line.name())
                    .append(" x")
                    .append(line.quantity())
                    .append("  $")
                    .append(line.finalSubtotal().amount())
                    .append("\n");
        }
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "交易详情", "Detalle"))
                .setMessage(detail.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmVoid(Sale sale) {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "作废这笔销售？", "Anular venta?"))
                .setMessage(sale.id())
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "作废", "Anular"), (dialog, which) -> {
                    services.checkout().voidSale(sale.id());
                    refresh.run();
                })
                .show();
    }
}

