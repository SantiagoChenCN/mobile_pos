package com.espsa.mobilepos.ui.screens;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

public final class CheckoutSectionScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final ScanGateway scanGateway;
    private CheckoutTab activeTab = CheckoutTab.CHECKOUT;
    private CheckoutScreen checkoutScreen;
    private FrameLayout content;
    private Button checkoutTabButton;
    private Button salesTabButton;

    public CheckoutSectionScreen(Context context, AppServices services, AppLanguage language, ScanGateway scanGateway) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.scanGateway = scanGateway;
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);
        page.addView(tabSwitcher(), Views.matchWrap());

        content = new FrameLayout(context);
        page.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        renderActiveTab();
        return page;
    }

    public void addScannedBarcode(String barcode) {
        if (checkoutScreen != null) {
            checkoutScreen.addScannedBarcode(barcode);
        }
    }

    private View tabSwitcher() {
        LinearLayout row = Views.horizontal(context);
        checkoutTabButton = Views.button(context, UiText.choose(language, "收银", "Caja"));
        checkoutTabButton.setOnClickListener(v -> switchTo(CheckoutTab.CHECKOUT));
        row.addView(checkoutTabButton, Views.weight(1));

        salesTabButton = Views.button(context, UiText.choose(language, "交易明细", "Ventas"));
        salesTabButton.setOnClickListener(v -> switchTo(CheckoutTab.SALES_DETAIL));
        row.addView(salesTabButton, Views.weight(1));
        updateTabButtons();
        return row;
    }

    private void switchTo(CheckoutTab tab) {
        activeTab = tab;
        updateTabButtons();
        renderActiveTab();
    }

    private void updateTabButtons() {
        if (checkoutTabButton != null) {
            checkoutTabButton.setEnabled(activeTab != CheckoutTab.CHECKOUT);
        }
        if (salesTabButton != null) {
            salesTabButton.setEnabled(activeTab != CheckoutTab.SALES_DETAIL);
        }
    }

    private void renderActiveTab() {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        checkoutScreen = null;
        if (activeTab == CheckoutTab.SALES_DETAIL) {
            content.addView(new SalesScreen(context, services, language, this::renderActiveTab).render());
            return;
        }
        checkoutScreen = new CheckoutScreen(context, services, language, scanGateway, () -> switchTo(CheckoutTab.SALES_DETAIL));
        content.addView(checkoutScreen.render());
    }

    private enum CheckoutTab {
        CHECKOUT,
        SALES_DETAIL
    }
}
