package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.app.SearchTaskRunner;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.ProductSearchResultDialog;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.util.List;
import java.util.Optional;

public final class ProductEditScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final ScanGateway scanGateway;
    private FrameLayout content;
    private ProductFormScreen activeForm;
    private AlertDialog searchLoadingDialog;

    public ProductEditScreen(Context context, AppServices services, AppLanguage language, ScanGateway scanGateway) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.scanGateway = scanGateway;
    }

    public View render() {
        content = new FrameLayout(context);
        renderSearch();
        return content;
    }

    public boolean hasUnsavedChanges() {
        return activeForm != null && activeForm.hasUnsavedChanges();
    }

    public void confirmDiscardChanges(Runnable afterDiscard) {
        if (!hasUnsavedChanges()) {
            afterDiscard.run();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "商品还没保存", "Cambios sin guardar"))
                .setMessage(UiText.choose(language, "要放弃这次修改并返回吗？", "Quiere descartar los cambios?"))
                .setNegativeButton(UiText.choose(language, "继续编辑", "Seguir editando"), null)
                .setPositiveButton(UiText.choose(language, "放弃修改", "Descartar"), (dialog, which) -> afterDiscard.run())
                .show();
    }

    public void addScannedBarcode(String barcode) {
        lookupBarcode(clean(barcode));
    }

    private void renderSearch() {
        activeForm = null;
        content.removeAllViews();
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "商品编辑 / Product Editing", "Editar productos"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(barcodePanel(), Views.matchWrap());
        page.addView(Views.divider(context));
        page.addView(keywordPanel(), Views.matchWrap());
        content.addView(page);
    }

    private View barcodePanel() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 12, 0, 14);

        EditText barcode = new EditText(context);
        barcode.setHint(UiText.choose(language, "条码或短码，例如 001", "Codigo o codigo corto"));
        barcode.setSingleLine(true);
        barcode.setInputType(InputType.TYPE_CLASS_NUMBER);
        panel.addView(barcode, Views.matchWrap());

        LinearLayout actions = Views.horizontal(context);
        Button lookup = Views.button(context, UiText.choose(language, "查找 / 新建", "Buscar / crear"));
        lookup.setOnClickListener(v -> lookupBarcode(clean(barcode.getText().toString())));
        actions.addView(lookup, Views.weight(1));

        Button scan = Views.button(context, UiText.choose(language, "扫码", "Camara"));
        scan.setOnClickListener(v -> scanGateway.requestBarcodeScan());
        actions.addView(scan, Views.weight(1));
        panel.addView(actions, Views.matchWrap());
        return panel;
    }

    private View keywordPanel() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 14, 0, 0);

        EditText keyword = new EditText(context);
        keyword.setHint(UiText.choose(language, "商品名关键词", "Palabra clave"));
        keyword.setSingleLine(true);
        keyword.setInputType(InputType.TYPE_CLASS_TEXT);
        panel.addView(keyword, Views.matchWrap());

        Button search = Views.button(context, UiText.choose(language, "搜索商品", "Buscar producto"));
        search.setOnClickListener(v -> handleKeywordSearch(clean(keyword.getText().toString()), search));
        panel.addView(search, Views.matchWrap());
        return panel;
    }

    private void lookupBarcode(String barcode) {
        if (barcode.isEmpty()) {
            Toast.makeText(context, UiText.choose(language, "请输入条码", "Escriba un codigo"), Toast.LENGTH_SHORT).show();
            return;
        }
        Optional<Product> product = services.productEditing().findByBarcode(barcode);
        if (product.isPresent()) {
            openEdit(product.get());
        } else {
            openCreate(barcode);
        }
    }

    private void handleKeywordSearch(String query, Button searchButton) {
        if (query.isEmpty()) {
            Toast.makeText(context, UiText.choose(language, "请输入商品关键词", "Escriba una palabra clave"), Toast.LENGTH_SHORT).show();
            return;
        }
        showKeywordSearchLoading(searchButton);
        services.searchTaskRunner().runLatest(
                () -> services.productEditing().searchByKeyword(query),
                new SearchTaskRunner.SearchCallback<List<Product>>() {
                    @Override
                    public void onResult(List<Product> result) {
                        hideKeywordSearchLoading(searchButton);
                        showKeywordSearchResults(result);
                    }

                    @Override
                    public void onError(RuntimeException error) {
                        hideKeywordSearchLoading(searchButton);
                        Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void showKeywordSearchLoading(Button searchButton) {
        searchButton.setEnabled(false);
        LinearLayout panel = Views.horizontal(context);
        panel.setPadding(dp(18), dp(12), dp(18), dp(12));
        ProgressBar progress = new ProgressBar(context);
        panel.addView(progress);
        TextView label = Views.text(context, UiText.choose(language, "搜索中...", "Buscando..."), 16, StyleGuide.INK);
        label.setPadding(dp(12), 0, 0, 0);
        panel.addView(label, Views.weight(1));
        searchLoadingDialog = new AlertDialog.Builder(context)
                .setView(panel)
                .setCancelable(false)
                .show();
    }

    private void hideKeywordSearchLoading(Button searchButton) {
        searchButton.setEnabled(true);
        if (searchLoadingDialog != null && searchLoadingDialog.isShowing()) {
            searchLoadingDialog.dismiss();
        }
        searchLoadingDialog = null;
    }

    private void showKeywordSearchResults(List<Product> results) {
        if (results.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle(UiText.choose(language, "搜索结果", "Resultado"))
                    .setMessage(UiText.choose(language, "没有匹配商品", "Sin resultados"))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (results.size() == 1) {
            openEdit(results.get(0));
            return;
        }
        ProductSearchResultDialog.show(context, language, results, this::openEdit);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void openCreate(String barcode) {
        activeForm = ProductFormScreen.create(context, services, language, barcode, this::renderSearch, this::openEdit);
        showForm();
    }

    private void openEdit(Product product) {
        activeForm = ProductFormScreen.edit(context, services, language, product, this::renderSearch, this::openEdit);
        showForm();
    }

    private void showForm() {
        content.removeAllViews();
        content.addView(activeForm.render());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
