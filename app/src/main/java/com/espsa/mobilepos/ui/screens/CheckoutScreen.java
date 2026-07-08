package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.app.SearchTaskRunner;
import com.espsa.mobilepos.core.checkout.CashChangeCalculator;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.checkout.ProductNotFoundException;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.pricing.CartPriceResult;
import com.espsa.mobilepos.core.pricing.LinePriceResult;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.CashPaymentDialog;
import com.espsa.mobilepos.ui.KeyboardActions;
import com.espsa.mobilepos.ui.ProductSearchResultDialog;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.util.List;

public final class CheckoutScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final ScanGateway scanGateway;
    private final Runnable onSaleSaved;
    private final CashChangeCalculator cashChangeCalculator = new CashChangeCalculator();
    private Cart cart;
    private LinearLayout cartContainer;
    private TextView totalText;
    private Spinner paymentSpinner;

    public CheckoutScreen(Context context, AppServices services, AppLanguage language, ScanGateway scanGateway, Runnable onSaleSaved) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.scanGateway = scanGateway;
        this.onSaleSaved = onSaleSaved;
        this.cart = services.currentCart();
    }

    public View render() {
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 8);

        TextView title = Views.text(context, UiText.choose(language, "收银 / Caja", "Caja"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(entryPanel(), Views.matchWrap());

        ScrollView scroll = new ScrollView(context);
        cartContainer = Views.vertical(context);
        scroll.addView(cartContainer);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        page.addView(totalPanel(), Views.matchWrap());
        refreshCart();
        return page;
    }

    private View entryPanel() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 12, 0, 8);

        EditText barcode = Views.editText(context);
        barcode.setHint(UiText.choose(language, "扫条码或输入条码", "Escanear o escribir codigo"));
        barcode.setSingleLine(true);
        barcode.setInputType(InputType.TYPE_CLASS_TEXT);
        panel.addView(barcode, Views.matchWrap());

        LinearLayout actions = Views.horizontal(context);
        Button add = Views.button(context, UiText.choose(language, "加入", "Agregar"));
        add.setOnClickListener(v -> addBarcode(barcode));
        actions.addView(add, Views.weight(1));

        Button search = Views.button(context, UiText.choose(language, "搜索", "Buscar"));
        search.setOnClickListener(v -> handleSearchClick(barcode.getText().toString(), search));
        actions.addView(search, Views.weight(1));
        KeyboardActions.bindSearchAction(barcode, () -> handleSearchClick(barcode.getText().toString(), search));

        Button manual = Views.button(context, UiText.choose(language, "手动价格", "Precio manual"));
        manual.setOnClickListener(v -> showManualPriceDialog());
        actions.addView(manual, Views.weight(1));

        Button scan = Views.button(context, UiText.choose(language, "扫码", "Camara"));
        scan.setOnClickListener(v -> scanGateway.requestBarcodeScan());
        actions.addView(scan, Views.weight(1));

        panel.addView(actions, Views.matchWrap());
        return panel;
    }

    public void addScannedBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return;
        }
        try {
            services.checkout().addProductByBarcode(cart, barcode.trim(), 1);
            refreshCart();
            Toast.makeText(context, barcode.trim(), Toast.LENGTH_SHORT).show();
        } catch (ProductNotFoundException ex) {
            showNotFoundDialog();
        }
    }

    private View totalPanel() {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(0, 8, 0, 0);

        totalText = Views.text(context, "$0", 34, StyleGuide.INK);
        StyleGuide.total(totalText);
        panel.addView(totalText, Views.matchWrap());

        LinearLayout discountRow = Views.horizontal(context);
        Button percent = Views.button(context, UiText.choose(language, "整单折扣%", "Desc. %"));
        percent.setOnClickListener(v -> showCartPercentDiscountDialog());
        discountRow.addView(percent, Views.weight(1));

        Button fixed = Views.button(context, UiText.choose(language, "整单减价", "Desc. $"));
        fixed.setOnClickListener(v -> showCartFixedDiscountDialog());
        discountRow.addView(fixed, Views.weight(1));

        Button clearDiscount = Views.button(context, UiText.choose(language, "撤回整单优惠", "Quitar desc."));
        clearDiscount.setOnClickListener(v -> {
            cart.setCartDiscount(Discount.NONE);
            refreshCart();
        });
        discountRow.addView(clearDiscount, Views.weight(1));
        panel.addView(discountRow, Views.matchWrap());

        paymentSpinner = new Spinner(context);
        paymentSpinner.setAdapter(Views.spinnerAdapter(context, paymentLabels()));
        panel.addView(paymentSpinner, Views.matchWrap());

        Button checkout = Views.button(context, UiText.choose(language, "结账保存", "Guardar venta"));
        checkout.setTextSize(StyleGuide.scaledSp(20));
        checkout.setOnClickListener(v -> checkout());
        panel.addView(checkout, Views.matchWrap());
        return panel;
    }

    private String[] paymentLabels() {
        return new String[]{
                UiText.choose(language, "现金", "Efectivo"),
                "Mercado Pago",
                UiText.choose(language, "借记卡", "Debito"),
                UiText.choose(language, "信用卡", "Credito"),
                "transferencia"
        };
    }

    private PaymentMethod selectedPaymentMethod() {
        int selected = paymentSpinner.getSelectedItemPosition();
        if (selected == 1) {
            return PaymentMethod.MERCADO_PAGO;
        }
        if (selected == 2) {
            return PaymentMethod.DEBIT_CARD;
        }
        if (selected == 3) {
            return PaymentMethod.CREDIT_CARD;
        }
        if (selected == 4) {
            return PaymentMethod.TRANSFERENCIA;
        }
        return PaymentMethod.CASH;
    }

    private void addBarcode(EditText barcodeInput) {
        String barcode = barcodeInput.getText().toString().trim();
        if (barcode.isEmpty()) {
            return;
        }
        try {
            services.checkout().addProductByBarcode(cart, barcode, 1);
            barcodeInput.setText("");
            refreshCart();
        } catch (ProductNotFoundException ex) {
            showNotFoundDialog();
        }
    }

    private void refreshCart() {
        cartContainer.removeAllViews();
        CartPriceResult price = services.checkout().preview(cart);
        if (price.lines().isEmpty()) {
            TextView empty = Views.text(context, UiText.choose(language, "还没有商品", "Sin productos"), 18, StyleGuide.MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            cartContainer.addView(empty, Views.matchWrap());
        } else {
            for (LinePriceResult line : price.lines()) {
                cartContainer.addView(lineView(line), Views.matchWrap());
                cartContainer.addView(Views.divider(context));
            }
        }
        totalText.setText("$" + price.total().amount());
    }

    private View lineView(LinePriceResult linePrice) {
        CartLine line = linePrice.line();
        LinearLayout row = Views.horizontal(context);
        row.setPadding(0, 10, 0, 10);

        LinearLayout details = Views.vertical(context);

        TextView name = Views.text(context, line.product().name(), 18, StyleGuide.INK);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        details.addView(name, Views.matchWrap());

        TextView meta = Views.text(
                context,
                line.product().barcode() + "  x" + line.quantity() + "  $" + linePrice.appliedUnitPrice().amount() + "  = $" + linePrice.finalSubtotal().amount(),
                14,
                StyleGuide.MUTED
        );
        details.addView(meta, Views.matchWrap());

        String markers = lineMarkers(linePrice);
        if (!markers.isEmpty()) {
            TextView marker = Views.text(context, markers, 13, StyleGuide.TEAL);
            details.addView(marker, Views.matchWrap());
        }
        row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button menu = Views.button(context, UiText.choose(language, "操作", "Mas"));
        menu.setTextSize(StyleGuide.scaledSp(14));
        menu.setMinWidth(dp(72));
        menu.setOnClickListener(v -> showLineActionMenu(linePrice));
        row.addView(menu, new LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private String lineMarkers(LinePriceResult linePrice) {
        StringBuilder markers = new StringBuilder();
        if (linePrice.manualPriceApplied()) {
            markers.append(UiText.choose(language, "已改价", "Precio editado"));
        }
        if (linePrice.lineDiscountAmount().amount() > 0) {
            if (markers.length() > 0) {
                markers.append("  ");
            }
            markers.append(UiText.choose(language, "已优惠", "Desc. aplicado"));
        }
        if (linePrice.automaticPromotionApplied()) {
            if (markers.length() > 0) {
                markers.append("  ");
            }
            markers.append(UiText.choose(language, "促销", "Promo"));
        }
        return markers.toString();
    }

    private void showLineActionMenu(LinePriceResult linePrice) {
        CartLine line = linePrice.line();
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(dp(8), dp(8), dp(8), dp(4));

        TextView name = Views.text(context, line.product().name(), 18, StyleGuide.INK);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        panel.addView(name, Views.matchWrap());

        TextView meta = Views.text(
                context,
                line.product().barcode() + "  x" + line.quantity() + "  $" + linePrice.appliedUnitPrice().amount() + "  = $" + linePrice.finalSubtotal().amount(),
                14,
                StyleGuide.MUTED
        );
        panel.addView(meta, Views.matchWrap());

        final AlertDialog[] dialogRef = new AlertDialog[1];
        LinearLayout quantityRow = Views.horizontal(context);
        quantityRow.addView(lineActionButton("-", () -> changeLineQuantity(line, -1), dialogRef), Views.weight(1));
        quantityRow.addView(lineActionButton("+", () -> changeLineQuantity(line, 1), dialogRef), Views.weight(1));
        panel.addView(quantityRow, Views.matchWrap());

        LinearLayout editRow = Views.horizontal(context);
        editRow.addView(lineActionButton(UiText.choose(language, "改价", "Precio"), () -> showLinePriceDialog(line), dialogRef), Views.weight(1));
        editRow.addView(lineActionButton(UiText.choose(language, "删除", "Borrar"), () -> {
            cart.removeLine(line.id());
            refreshCart();
        }, dialogRef), Views.weight(1));
        panel.addView(editRow, Views.matchWrap());

        LinearLayout discountRow = Views.horizontal(context);
        discountRow.addView(lineActionButton(UiText.choose(language, "折扣%", "Desc. %"), () -> showLinePercentDiscountDialog(line), dialogRef), Views.weight(1));
        discountRow.addView(lineActionButton(UiText.choose(language, "减价", "Desc. $"), () -> showLineFixedDiscountDialog(line), dialogRef), Views.weight(1));
        panel.addView(discountRow, Views.matchWrap());

        Button clearLine = lineActionButton(UiText.choose(language, "撤回改动", "Restaurar"), () -> {
            cart.replaceLine(line.withoutManualAdjustments());
            refreshCart();
        }, dialogRef);
        panel.addView(clearLine, Views.matchWrap());

        dialogRef[0] = new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "商品操作", "Item"))
                .setView(panel)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .show();
    }

    private Button lineActionButton(String label, Runnable action, AlertDialog[] dialogRef) {
        Button button = Views.button(context, label);
        button.setOnClickListener(v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            action.run();
        });
        return button;
    }

    private void changeLineQuantity(CartLine line, int delta) {
        int newQuantity = line.quantity() + delta;
        if (newQuantity <= 0) {
            cart.removeLine(line.id());
        } else {
            cart.replaceLine(line.withQuantity(newQuantity));
        }
        refreshCart();
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void showNotFoundDialog() {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "未找到商品", "Producto no encontrado"))
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "手动输入价格", "Precio manual"), (dialog, which) -> showManualPriceDialog())
                .show();
    }

    private void showManualPriceDialog() {
        EditText price = Views.editText(context);
        price.setInputType(InputType.TYPE_CLASS_NUMBER);
        price.setHint("0");
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "手动价格 almacén", "Precio manual almacen"))
                .setView(price)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "加入", "Agregar"), (dialog, which) -> {
                    long amount = parseAmount(price.getText().toString());
                    if (amount > 0) {
                        services.checkout().addManualAlmacenItem(cart, Money.of(amount), 1);
                        refreshCart();
                    }
                })
                .show();
    }

    private void showLinePriceDialog(CartLine line) {
        EditText price = Views.editText(context);
        price.setInputType(InputType.TYPE_CLASS_NUMBER);
        price.setHint(Long.toString(line.product().salePrice().amount()));
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "修改这一行单价", "Cambiar precio"))
                .setView(price)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setNeutralButton(UiText.choose(language, "恢复原价", "Precio original"), (dialog, which) -> {
                    cart.replaceLine(line.withoutManualUnitPrice());
                    refreshCart();
                })
                .setPositiveButton(UiText.choose(language, "保存", "Guardar"), (dialog, which) -> {
                    long amount = parseAmount(price.getText().toString());
                    if (amount > 0) {
                        cart.replaceLine(line.withManualUnitPrice(Money.of(amount)));
                        refreshCart();
                    }
                })
                .show();
    }

    private void showCartPercentDiscountDialog() {
        showPercentDialog(UiText.choose(language, "整单百分比优惠", "Descuento total %"), basisPoints -> {
            cart.setCartDiscount(Discount.percent(basisPoints));
            refreshCart();
        });
    }

    private void showCartFixedDiscountDialog() {
        showAmountDialog(UiText.choose(language, "整单固定减价", "Descuento total $"), amount -> {
            cart.setCartDiscount(Discount.fixedAmount(Money.of(amount)));
            refreshCart();
        });
    }

    private void showLinePercentDiscountDialog(CartLine line) {
        showPercentDialog(UiText.choose(language, "这一行百分比优惠", "Descuento de item %"), basisPoints -> {
            cart.replaceLine(line.withLineDiscount(Discount.percent(basisPoints)));
            refreshCart();
        });
    }

    private void showLineFixedDiscountDialog(CartLine line) {
        showAmountDialog(UiText.choose(language, "这一行固定减价", "Descuento de item $"), amount -> {
            cart.replaceLine(line.withLineDiscount(Discount.fixedAmount(Money.of(amount))));
            refreshCart();
        });
    }

    private void showPercentDialog(String title, PercentCallback callback) {
        EditText input = Views.editText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("10");
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "保存", "Guardar"), (dialog, which) -> {
                    int basisPoints = parsePercentBasisPoints(input.getText().toString());
                    if (basisPoints > 0) {
                        callback.apply(basisPoints);
                    }
                })
                .show();
    }

    private void showAmountDialog(String title, AmountCallback callback) {
        EditText input = Views.editText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("100");
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "保存", "Guardar"), (dialog, which) -> {
                    long amount = parseAmount(input.getText().toString());
                    if (amount > 0) {
                        callback.apply(amount);
                    }
                })
                .show();
    }

    private void handleSearchClick(String query, Button searchButton) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            Toast.makeText(context, UiText.choose(language, "请输入商品关键词", "Escriba una palabra clave"), Toast.LENGTH_SHORT).show();
            return;
        }

        showSearchLoading(searchButton);
        services.searchTaskRunner().runLatest(
                () -> services.catalog().searchByName(trimmed),
                new SearchTaskRunner.SearchCallback<List<Product>>() {
                    @Override
                    public void onResult(List<Product> result) {
                        hideSearchLoading(searchButton);
                        showSearchResults(result);
                    }

                    @Override
                    public void onError(RuntimeException error) {
                        hideSearchLoading(searchButton);
                        Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void showSearchLoading(Button searchButton) {
        searchButton.setEnabled(false);
        searchButton.setText(UiText.choose(language, "搜索中...", "Buscando..."));
        Toast.makeText(context, UiText.choose(language, "搜索中...", "Buscando..."), Toast.LENGTH_SHORT).show();
    }

    private void hideSearchLoading(Button searchButton) {
        searchButton.setEnabled(true);
        searchButton.setText(UiText.choose(language, "搜索", "Buscar"));
    }

    private void showSearchResults(List<Product> results) {
        if (results.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle(UiText.choose(language, "搜索结果", "Resultado"))
                    .setMessage(UiText.choose(language, "没有匹配商品", "Sin resultados"))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        ProductSearchResultDialog.show(context, language, results, this::addSearchResultToCart);
    }

    private void addSearchResultToCart(Product product) {
        cart.addProduct(product, 1);
        refreshCart();
    }

    private void checkout() {
        PaymentMethod paymentMethod = selectedPaymentMethod();
        if (paymentMethod == PaymentMethod.CASH) {
            showCashPaymentDialog();
            return;
        }
        completeCheckout(paymentMethod);
    }

    private void showCashPaymentDialog() {
        CartPriceResult price = services.checkout().preview(cart);
        CashPaymentDialog.show(
                context,
                language,
                price.total(),
                cashChangeCalculator,
                result -> completeCheckout(PaymentMethod.CASH)
        );
    }

    private void completeCheckout(PaymentMethod paymentMethod) {
        try {
            Sale sale = services.checkout().checkout(cart, paymentMethod);
            Toast.makeText(context, UiText.choose(language, "已保存：", "Guardado: ") + sale.id(), Toast.LENGTH_LONG).show();
            cart = services.resetCart();
            refreshCart();
            onSaleSaved.run();
        } catch (RuntimeException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private long parseAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int parsePercentBasisPoints(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            String normalized = raw.trim().replace(',', '.');
            double percent = Double.parseDouble(normalized);
            if (percent <= 0 || percent > 100) {
                return 0;
            }
            return (int) Math.round(percent * 100);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private interface PercentCallback {
        void apply(int basisPoints);
    }

    private interface AmountCallback {
        void apply(long amount);
    }
}
