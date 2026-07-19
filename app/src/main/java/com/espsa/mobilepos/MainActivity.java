package com.espsa.mobilepos;

import android.app.Activity;
import android.app.AlertDialog;
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
import com.espsa.mobilepos.core.importer.ImportFormat;
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
    private ImportFormat pendingImportFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        services = ((MobilePosApplication) getApplication()).services();
        language = services.preferencesStore().loadLanguage(this);
        StyleGuide.setTextScale(services.preferencesStore().loadTextScale(this));
        renderShell();
    }

    @Override
    protected void onResume() {
        super.onResume();
        services.computerSyncCoordinator().startForeground();
    }

    @Override
    protected void onPause() {
        services.computerSyncCoordinator().stopForeground();
        super.onPause();
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
    public void requestImportFile(ImportFormat format) {
        pendingImportFormat = format == null ? ImportFormat.MINGSHENG_DB : format;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        configureImportFilePicker(intent, pendingImportFormat);
        startActivityForResult(intent, IMPORT_FILE_REQUEST);
    }

    @Override
    public void onImportFileSelected(Uri uri) {
        ImportFormat format = pendingImportFormat == null ? ImportFormat.MINGSHENG_DB : pendingImportFormat;
        try {
            ProductImportResult result = services.importProducts(this, uri, format);
            showImportSuccess(result);
            renderShell();
        } catch (ProductImportException ex) {
            showImportFailure(ex.getMessage());
        } finally {
            pendingImportFormat = null;
        }
    }

    private void configureImportFilePicker(Intent intent, ImportFormat format) {
        if (format == ImportFormat.GENERIC_CSV) {
            intent.setType("text/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain"
            });
            return;
        }
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream",
                "application/vnd.sqlite3",
                "application/x-sqlite3"
        });
    }

    private void showImportSuccess(ProductImportResult result) {
        String message = UiText.choose(language, "商品", "Productos") + ": " + result.productCount()
                + "\n" + UiText.choose(language, "促销", "Promociones") + ": " + result.promotionCount()
                + "\n" + UiText.choose(language, "警告", "Advertencias") + ": " + result.warnings().size()
                + "\n" + UiText.choose(language, "文件", "Archivo") + ": " + emptyText(result.sourceFileName());
        new AlertDialog.Builder(this)
                .setTitle(UiText.choose(language, "导入成功", "Importacion completa"))
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showImportFailure(String message) {
        new AlertDialog.Builder(this)
                .setTitle(UiText.choose(language, "导入失败", "No se pudo importar"))
                .setMessage(emptyText(message))
                .setPositiveButton("OK", null)
                .show();
    }

    private String emptyText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
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
        if (requestCode == IMPORT_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                onImportFileSelected(data.getData());
            } else {
                pendingImportFormat = null;
                Toast.makeText(this, UiText.choose(language, "未选择文件", "Sin archivo seleccionado"), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
