package com.espsa.mobilepos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
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
import com.espsa.mobilepos.ui.TextScale;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;
import com.espsa.mobilepos.ui.screens.CheckoutSectionScreen;
import com.espsa.mobilepos.ui.screens.DailySummaryScreen;
import com.espsa.mobilepos.ui.screens.HomeScreen;
import com.espsa.mobilepos.ui.screens.ImportScreen;
import com.espsa.mobilepos.ui.screens.ProductEditScreen;
import com.espsa.mobilepos.ui.screens.SettingsScreen;

public final class MainActivity extends Activity implements ImportGateway, ScanGateway {
    private static final int IMPORT_FILE_REQUEST = 42;
    private static final int SCAN_BARCODE_REQUEST = 43;

    private AppServices services;
    private AppLanguage language = AppLanguage.ZH;
    private Screen screen = Screen.HOME;
    private Screen pendingScanScreen = Screen.CHECKOUT;
    private FrameLayout content;
    private CheckoutSectionScreen activeCheckoutSectionScreen;
    private ProductEditScreen activeProductEditScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        services = ((MobilePosApplication) getApplication()).services();
        language = services.preferencesStore().loadLanguage(this);
        StyleGuide.setTextScale(services.preferencesStore().loadTextScale(this));
        renderShell();
    }

    private void renderShell() {
        cancelPendingUiTasks();
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

        if (screen != Screen.HOME) {
            Button home = Views.button(this, UiText.choose(language, "首页", "Inicio"));
            home.setOnClickListener(v -> navigateHome());
            header.addView(home);
        }

        TextView title = Views.text(this, UiText.choose(language, "应急收银", "Caja de emergencia"), 22, StyleGuide.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, Views.weight(1));

        Button languageButton = Views.button(this, language == AppLanguage.ZH ? "ES" : "中");
        languageButton.setOnClickListener(v -> toggleLanguage());
        header.addView(languageButton);
        return header;
    }

    private void renderCurrentScreen() {
        content.removeAllViews();
        activeCheckoutSectionScreen = null;
        activeProductEditScreen = null;
        View view;
        if (screen == Screen.HOME) {
            view = new HomeScreen(this, language, new HomeScreen.HomeNavigation() {
                @Override
                public void openProductEditing() {
                    navigateTo(Screen.PRODUCT_EDIT);
                }

                @Override
                public void openCheckout() {
                    navigateTo(Screen.CHECKOUT);
                }

                @Override
                public void openDailySummary() {
                    navigateTo(Screen.DAILY);
                }

                @Override
                public void openSettings() {
                    navigateTo(Screen.SETTINGS);
                }

                @Override
                public void openImport() {
                    navigateTo(Screen.IMPORT);
                }
            }).render();
        } else if (screen == Screen.PRODUCT_EDIT) {
            activeProductEditScreen = new ProductEditScreen(this, services, language, this);
            view = activeProductEditScreen.render();
        } else if (screen == Screen.CHECKOUT) {
            activeCheckoutSectionScreen = new CheckoutSectionScreen(this, services, language, this);
            view = activeCheckoutSectionScreen.render();
        } else if (screen == Screen.DAILY) {
            view = new DailySummaryScreen(this, services, language).render();
        } else if (screen == Screen.SETTINGS) {
            view = new SettingsScreen(
                    this,
                    services,
                    language,
                    this::toggleLanguage,
                    StyleGuide.textScale(),
                    this::changeTextScale
            ).render();
        } else if (screen == Screen.IMPORT) {
            view = new ImportScreen(this, services, language, this, this::renderShell).render();
        } else {
            view = new HomeScreen(this, language, new HomeScreen.HomeNavigation() {
                @Override
                public void openProductEditing() {
                    navigateTo(Screen.PRODUCT_EDIT);
                }

                @Override
                public void openCheckout() {
                    navigateTo(Screen.CHECKOUT);
                }

                @Override
                public void openDailySummary() {
                    navigateTo(Screen.DAILY);
                }

                @Override
                public void openSettings() {
                    navigateTo(Screen.SETTINGS);
                }

                @Override
                public void openImport() {
                    navigateTo(Screen.IMPORT);
                }
            }).render();
        }
        content.addView(view);
    }

    private void toggleLanguage() {
        language = language == AppLanguage.ZH ? AppLanguage.ES : AppLanguage.ZH;
        services.preferencesStore().saveLanguage(this, language);
        renderShell();
    }

    private void changeTextScale(TextScale textScale) {
        StyleGuide.setTextScale(textScale);
        services.preferencesStore().saveTextScale(this, textScale);
        renderShell();
    }

    private void navigateTo(Screen target) {
        confirmLeaveIfNeeded(() -> {
            screen = target;
            renderShell();
        });
    }

    private void navigateHome() {
        navigateTo(Screen.HOME);
    }

    private void confirmLeaveIfNeeded(Runnable afterConfirm) {
        if (screen == Screen.PRODUCT_EDIT && activeProductEditScreen != null && activeProductEditScreen.hasUnsavedChanges()) {
            activeProductEditScreen.confirmDiscardChanges(afterConfirm);
            return;
        }
        afterConfirm.run();
    }

    private void cancelPendingUiTasks() {
        if (services != null) {
            services.searchTaskRunner().cancelPending();
        }
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
        pendingScanScreen = screen;
        startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_BARCODE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_BARCODE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                String barcode = data.getStringExtra(ScannerActivity.EXTRA_BARCODE);
                if (pendingScanScreen == Screen.PRODUCT_EDIT) {
                    screen = Screen.PRODUCT_EDIT;
                    renderShell();
                    if (activeProductEditScreen != null) {
                        activeProductEditScreen.addScannedBarcode(barcode);
                    }
                } else {
                    screen = Screen.CHECKOUT;
                    renderShell();
                    if (activeCheckoutSectionScreen != null) {
                        activeCheckoutSectionScreen.addScannedBarcode(barcode);
                    }
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            onImportFileSelected(data.getData());
        }
    }

    @Override
    public void onBackPressed() {
        if (screen == Screen.HOME) {
            super.onBackPressed();
            return;
        }
        navigateHome();
    }
}
