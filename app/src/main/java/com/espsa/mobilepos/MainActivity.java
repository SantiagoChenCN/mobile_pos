package com.espsa.mobilepos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ImportGateway;
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.Screen;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;
import com.espsa.mobilepos.ui.screens.CheckoutScreen;
import com.espsa.mobilepos.ui.screens.DailySummaryScreen;
import com.espsa.mobilepos.ui.screens.SalesScreen;
import com.espsa.mobilepos.ui.screens.SettingsScreen;

public final class MainActivity extends Activity implements ImportGateway, ScanGateway {
    private static final int IMPORT_FILE_REQUEST = 42;
    private static final int SCAN_BARCODE_REQUEST = 43;

    private AppServices services;
    private AppLanguage language = AppLanguage.ZH;
    private Screen screen = Screen.CHECKOUT;
    private FrameLayout content;
    private CheckoutScreen activeCheckoutScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        services = ((MobilePosApplication) getApplication()).services();
        renderShell();
    }

    private void renderShell() {
        LinearLayout root = Views.vertical(this);
        root.setBackgroundColor(StyleGuide.PAPER);
        applySystemBarPadding(root);

        root.addView(header());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(bottomNav());
        setContentView(root);
        renderCurrentScreen();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(StyleGuide.PAPER);
        window.setNavigationBarColor(StyleGuide.INK);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void applySystemBarPadding(View root) {
        root.setPadding(0, systemBarHeight("status_bar"), 0, systemBarHeight("navigation_bar"));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = Math.max(insets.getSystemWindowInsetTop(), systemBarHeight("status_bar"));
            int bottom = Math.max(insets.getSystemWindowInsetBottom(), systemBarHeight("navigation_bar"));
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private int systemBarHeight(String resourceName) {
        int resourceId = getResources().getIdentifier(resourceName, "dimen", "android");
        if (resourceId == 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private View header() {
        LinearLayout header = Views.horizontal(this);
        header.setPadding(18, 14, 18, 10);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Views.text(this, UiText.choose(language, "应急收银", "Caja de emergencia"), 22, StyleGuide.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, Views.weight(1));

        Button languageButton = Views.button(this, language == AppLanguage.ZH ? "ES" : "中");
        languageButton.setOnClickListener(v -> {
            language = language == AppLanguage.ZH ? AppLanguage.ES : AppLanguage.ZH;
            renderShell();
        });
        header.addView(languageButton);
        return header;
    }

    private View bottomNav() {
        LinearLayout nav = Views.horizontal(this);
        nav.setPadding(8, 8, 8, 8);
        nav.setBackgroundColor(StyleGuide.INK);
        addNavButton(nav, Screen.CHECKOUT, UiText.choose(language, "收银", "Caja"));
        addNavButton(nav, Screen.SALES, UiText.choose(language, "明细", "Ventas"));
        addNavButton(nav, Screen.DAILY, UiText.choose(language, "日账", "Diario"));
        addNavButton(nav, Screen.SETTINGS, UiText.choose(language, "设置", "Ajustes"));
        return nav;
    }

    private void addNavButton(LinearLayout nav, Screen target, String label) {
        Button button = Views.button(this, label);
        button.setEnabled(screen != target);
        button.setOnClickListener(v -> {
            screen = target;
            renderShell();
        });
        nav.addView(button, Views.weight(1));
    }

    private void renderCurrentScreen() {
        content.removeAllViews();
        activeCheckoutScreen = null;
        View view;
        if (screen == Screen.SALES) {
            view = new SalesScreen(this, services, language, this::renderCurrentScreen).render();
        } else if (screen == Screen.DAILY) {
            view = new DailySummaryScreen(this, services, language).render();
        } else if (screen == Screen.SETTINGS) {
            view = new SettingsScreen(this, services, language, this).render();
        } else {
            activeCheckoutScreen = new CheckoutScreen(this, services, language, this, () -> {
                screen = Screen.SALES;
                renderShell();
            });
            view = activeCheckoutScreen.render();
        }
        content.addView(view);
    }

    @Override
    public void requestImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_FILE_REQUEST);
    }

    @Override
    public void onImportFileSelected(Uri uri) {
        try {
            ProductImportResult result = services.importMingshengDatabase(this, uri);
            Toast.makeText(
                    this,
                    UiText.choose(language, "导入成功：", "Importado: ") + result.productCount() + UiText.choose(language, " 个商品", " productos"),
                    Toast.LENGTH_LONG
            ).show();
            renderShell();
        } catch (ProductImportException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void requestBarcodeScan() {
        startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_BARCODE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_BARCODE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                String barcode = data.getStringExtra(ScannerActivity.EXTRA_BARCODE);
                screen = Screen.CHECKOUT;
                renderShell();
                if (activeCheckoutScreen != null) {
                    activeCheckoutScreen.addScannedBarcode(barcode);
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            onImportFileSelected(data.getData());
        }
    }
}
