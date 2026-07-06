package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.ui.AppLanguage;
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
        search.setOnClickListener(v -> searchKeyword(clean(keyword.getText().toString())));
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

    private void searchKeyword(String query) {
        if (query.isEmpty()) {
            Toast.makeText(context, UiText.choose(language, "请输入商品关键词", "Escriba una palabra clave"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<Product> results = services.productEditing().searchByKeyword(query);
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
        showMultipleResults(results);
    }

    private void showMultipleResults(List<Product> products) {
        LinearLayout list = Views.vertical(context);
        list.setPadding(8, 8, 8, 8);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (Product product : products) {
            Button item = Views.button(context, product.name()
                    + "\n" + product.barcode()
                    + "  $" + product.salePrice().amount()
                    + "  " + product.category()
                    + "  " + product.unitName());
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setOnClickListener(v -> {
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                openEdit(product);
            });
            list.addView(item, Views.matchWrap());
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(760, Math.max(360, products.size() * 112))
        ));
        dialogRef[0] = new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "选择商品", "Elegir producto") + " (" + products.size() + ")")
                .setView(scroll)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .show();
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
