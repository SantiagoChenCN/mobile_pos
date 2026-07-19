package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.core.editing.ProductChange;
import com.espsa.mobilepos.core.editing.ProductCreateResult;
import com.espsa.mobilepos.core.editing.ProductDraft;
import com.espsa.mobilepos.core.editing.ProductPersistenceException;
import com.espsa.mobilepos.core.editing.ProductUpdateResult;
import com.espsa.mobilepos.core.editing.ProductValidationResult;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.MoneyText;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.util.List;
import java.util.Optional;

public final class ProductFormScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final Product originalProduct;
    private final String initialBarcode;
    private final Runnable onDone;
    private final ProductOpener productOpener;
    private EditText barcodeInput;
    private EditText nameInput;
    private EditText salePriceInput;
    private EditText promotionPriceInput;
    private EditText promotionQuantityInput;
    private Spinner categorySpinner;
    private Spinner unitSpinner;
    private ProductDraft initialDraft;

    private ProductFormScreen(
            Context context,
            AppServices services,
            AppLanguage language,
            Product originalProduct,
            String initialBarcode,
            Runnable onDone,
            ProductOpener productOpener
    ) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.originalProduct = originalProduct;
        this.initialBarcode = initialBarcode == null ? "" : initialBarcode;
        this.onDone = onDone;
        this.productOpener = productOpener;
    }

    public static ProductFormScreen create(
            Context context,
            AppServices services,
            AppLanguage language,
            String initialBarcode,
            Runnable onDone,
            ProductOpener productOpener
    ) {
        return new ProductFormScreen(context, services, language, null, initialBarcode, onDone, productOpener);
    }

    public static ProductFormScreen edit(
            Context context,
            AppServices services,
            AppLanguage language,
            Product product,
            Runnable onDone,
            ProductOpener productOpener
    ) {
        return new ProductFormScreen(context, services, language, product, "", onDone, productOpener);
    }

    public View render() {
        ScrollView scroll = new ScrollView(context);
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 16);

        TextView title = Views.text(context, title(), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "条码", "Codigo")));
        barcodeInput = input(InputType.TYPE_CLASS_NUMBER);
        barcodeInput.setText(originalProduct == null ? initialBarcode : originalProduct.barcode());
        page.addView(barcodeInput, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "商品名", "Nombre")));
        nameInput = input(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(originalProduct == null ? "" : originalProduct.name());
        page.addView(nameInput, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "售价", "Precio")));
        salePriceInput = input(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        salePriceInput.setText(originalProduct == null ? "" : MoneyText.format(originalProduct.salePrice()));
        page.addView(salePriceInput, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "分类", "Categoria")));
        categorySpinner = spinner(services.productEditing().categoryOptions(originalProduct), originalProduct == null ? "" : originalProduct.category());
        page.addView(categorySpinner, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "单位", "Unidad")));
        unitSpinner = spinner(services.productEditing().unitOptions(originalProduct), originalProduct == null ? "" : originalProduct.unitName());
        page.addView(unitSpinner, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "促销价", "Precio promocional")));
        promotionPriceInput = input(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        promotionPriceInput.setText(originalProduct == null || originalProduct.promotionPrice() == null
                ? ""
                : MoneyText.format(originalProduct.promotionPrice()));
        page.addView(promotionPriceInput, Views.matchWrap());

        page.addView(fieldLabel(UiText.choose(language, "促销数量", "Cantidad promocional")));
        promotionQuantityInput = input(InputType.TYPE_CLASS_NUMBER);
        promotionQuantityInput.setText(originalProduct == null || originalProduct.promotionMinQuantity() <= 0
                ? ""
                : Integer.toString(originalProduct.promotionMinQuantity()));
        page.addView(promotionQuantityInput, Views.matchWrap());

        page.addView(actionButtons(), Views.matchWrap());
        scroll.addView(page);
        initialDraft = readDraft();
        return scroll;
    }

    public boolean hasUnsavedChanges() {
        if (initialDraft == null || barcodeInput == null) {
            return false;
        }
        return !draftKey(initialDraft).equals(draftKey(readDraft()));
    }

    private String title() {
        return originalProduct == null
                ? UiText.choose(language, "新建商品", "Crear producto")
                : UiText.choose(language, "编辑商品", "Editar producto");
    }

    private TextView fieldLabel(String label) {
        TextView view = Views.text(context, label, 13, StyleGuide.MUTED);
        view.setPadding(0, 12, 0, 0);
        return view;
    }

    private EditText input(int inputType) {
        EditText input = Views.editText(context);
        input.setSingleLine(true);
        input.setInputType(inputType);
        return input;
    }

    private Spinner spinner(List<String> options, String selected) {
        Spinner spinner = new Spinner(context);
        spinner.setAdapter(Views.spinnerAdapter(context, options));
        int index = options.indexOf(selected == null ? "" : selected);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        return spinner;
    }

    private View actionButtons() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 18, 0, 0);

        Button save = Views.button(context, UiText.choose(language, "保存", "Guardar"));
        save.setOnClickListener(v -> handleSave());
        panel.addView(save, Views.matchWrap());

        if (originalProduct != null) {
            Button delete = Views.button(context, UiText.choose(language, "删除商品", "Borrar producto"));
            delete.setTextColor(StyleGuide.RED);
            delete.setOnClickListener(v -> confirmDeleteFirst());
            panel.addView(delete, Views.matchWrap());
        }

        Button cancel = Views.button(context, UiText.choose(language, "返回搜索", "Volver a buscar"));
        cancel.setOnClickListener(v -> confirmDiscardAndReturn());
        panel.addView(cancel, Views.matchWrap());
        return panel;
    }

    private ProductDraft readDraft() {
        return new ProductDraft(
                value(barcodeInput),
                value(nameInput),
                spinnerValue(categorySpinner),
                spinnerValue(unitSpinner),
                value(salePriceInput),
                value(promotionPriceInput),
                value(promotionQuantityInput)
        );
    }

    private void handleSave() {
        ProductDraft draft = readDraft();
        if (originalProduct == null) {
            handleCreateSave(draft);
        } else {
            handleUpdateSave(draft);
        }
    }

    private void handleCreateSave(ProductDraft draft) {
        Optional<Product> existing = services.productEditing().findByBarcode(draft.barcode());
        if (existing.isPresent()) {
            new AlertDialog.Builder(context)
                    .setTitle(UiText.choose(language, "条码已存在", "Codigo existente"))
                    .setMessage(existing.get().name())
                    .setNegativeButton(UiText.choose(language, "取消新建", "Cancelar"), null)
                    .setPositiveButton(UiText.choose(language, "编辑已有商品", "Editar existente"), (dialog, which) -> productOpener.open(existing.get()))
                    .show();
            return;
        }
        ProductValidationResult validation = services.productEditing().validateForCreate(draft);
        if (!validation.valid()) {
            showValidationErrors(validation);
            return;
        }
        if (services.productEditing().hasSameNameProduct(draft.name(), null)) {
            new AlertDialog.Builder(context)
                    .setTitle(UiText.choose(language, "商品名重复", "Nombre repetido"))
                    .setMessage(UiText.choose(language, "仍然保存这个新商品吗？", "Guardar igualmente este producto?"))
                    .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                    .setPositiveButton(UiText.choose(language, "继续保存", "Guardar"), (dialog, which) -> createProduct(draft))
                    .show();
            return;
        }
        createProduct(draft);
    }

    private void createProduct(ProductDraft draft) {
        try {
            ProductCreateResult result = services.productEditing().createProduct(draft);
            if (!result.success()) {
                showValidationErrors(result.validation());
                return;
            }
            Toast.makeText(context, UiText.choose(language, "商品已保存", "Producto guardado"), Toast.LENGTH_LONG).show();
            onDone.run();
        } catch (ProductPersistenceException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleUpdateSave(ProductDraft draft) {
        ProductValidationResult validation = services.productEditing().validateForUpdate(originalProduct.id(), draft);
        if (!validation.valid()) {
            showValidationErrors(validation);
            return;
        }
        List<ProductChange> changes = services.productEditing().diff(originalProduct, predictedProduct(validation));
        if (hasCriticalChanges(changes)) {
            new AlertDialog.Builder(context)
                    .setTitle(UiText.choose(language, "确认价格或条码变化", "Confirmar cambios importantes"))
                    .setMessage(formatChanges(changes))
                    .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                    .setPositiveButton(UiText.choose(language, "确认保存", "Guardar"), (dialog, which) -> updateProduct(draft))
                    .show();
            return;
        }
        updateProduct(draft);
    }

    private Product predictedProduct(ProductValidationResult validation) {
        return new Product(
                originalProduct.id(),
                validation.parsedDraft().barcode(),
                validation.parsedDraft().name(),
                validation.parsedDraft().category(),
                validation.parsedDraft().unitName(),
                validation.parsedDraft().salePrice(),
                validation.parsedDraft().promotionPrice(),
                validation.parsedDraft().promotionMinQuantity(),
                originalProduct.isManualPriceProduct()
        );
    }

    private void updateProduct(ProductDraft draft) {
        try {
            ProductUpdateResult result = services.productEditing().updateProduct(originalProduct.id(), draft);
            if (!result.success()) {
                showValidationErrors(result.validation());
                return;
            }
            showChangeSummary(result.changes());
        } catch (ProductPersistenceException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showChangeSummary(List<ProductChange> changes) {
        String message = changes.isEmpty()
                ? UiText.choose(language, "没有字段变化", "Sin cambios")
                : formatChanges(changes);
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "商品已保存", "Producto guardado"))
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> onDone.run())
                .show();
    }

    private void confirmDeleteFirst() {
        String message = originalProduct.name()
                + "\n" + originalProduct.barcode()
                + "\n" + MoneyText.currency(originalProduct.salePrice())
                + "\n\n"
                + UiText.choose(language, "删除后不能通过搜索或条码加入收银。", "Luego no se podra agregar por busqueda o codigo.");
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "删除商品？", "Borrar producto?"))
                .setMessage(message)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "继续", "Continuar"), (dialog, which) -> confirmDeleteSecond())
                .show();
    }

    private void confirmDeleteSecond() {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "确认删除这个本地商品？", "Confirmar borrado local?"))
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "删除", "Borrar"), (dialog, which) -> deleteProduct())
                .show();
    }

    private void deleteProduct() {
        try {
            if (services.productEditing().deleteProduct(originalProduct.id()).success()) {
                Toast.makeText(context, UiText.choose(language, "商品已删除", "Producto borrado"), Toast.LENGTH_LONG).show();
                onDone.run();
            }
        } catch (ProductPersistenceException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDiscardAndReturn() {
        if (!hasUnsavedChanges()) {
            onDone.run();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "商品还没保存", "Cambios sin guardar"))
                .setMessage(UiText.choose(language, "要放弃这次修改吗？", "Descartar los cambios?"))
                .setNegativeButton(UiText.choose(language, "继续编辑", "Seguir editando"), null)
                .setPositiveButton(UiText.choose(language, "放弃修改", "Descartar"), (dialog, which) -> onDone.run())
                .show();
    }

    private void showValidationErrors(ProductValidationResult validation) {
        StringBuilder message = new StringBuilder();
        List<String> errors = language == AppLanguage.ZH ? validation.errorsZh() : validation.errorsEs();
        for (String error : errors) {
            if (message.length() > 0) {
                message.append("\n");
            }
            message.append(error);
        }
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "请检查商品信息", "Revise los datos"))
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean hasCriticalChanges(List<ProductChange> changes) {
        for (ProductChange change : changes) {
            if ("条码".equals(change.fieldLabelZh())
                    || "售价".equals(change.fieldLabelZh())
                    || "促销".equals(change.fieldLabelZh())) {
                return true;
            }
        }
        return false;
    }

    private String formatChanges(List<ProductChange> changes) {
        return language == AppLanguage.ZH
                ? services.productEditing().formatChangesZh(changes)
                : services.productEditing().formatChangesEs(changes);
    }

    private String draftKey(ProductDraft draft) {
        return draft.barcode()
                + "\n" + draft.name()
                + "\n" + draft.category()
                + "\n" + draft.unitName()
                + "\n" + draft.salePriceText()
                + "\n" + draft.promotionPriceText()
                + "\n" + draft.promotionMinQuantityText();
    }

    private String value(EditText input) {
        return input == null ? "" : input.getText().toString().trim();
    }

    private String spinnerValue(Spinner spinner) {
        return spinner == null || spinner.getSelectedItem() == null ? "" : spinner.getSelectedItem().toString().trim();
    }

    public interface ProductOpener {
        void open(Product product);
    }
}
