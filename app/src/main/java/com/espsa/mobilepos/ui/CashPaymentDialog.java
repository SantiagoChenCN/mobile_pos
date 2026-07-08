package com.espsa.mobilepos.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.core.checkout.CashChangeCalculator;
import com.espsa.mobilepos.core.checkout.CashChangeResult;
import com.espsa.mobilepos.core.model.Money;

public final class CashPaymentDialog {
    private CashPaymentDialog() {
    }

    public static AlertDialog show(
            Context context,
            AppLanguage language,
            Money total,
            CashChangeCalculator calculator,
            CashPaymentCallback callback
    ) {
        LinearLayout panel = Views.vertical(context);
        panel.setPadding(dp(context, 18), dp(context, 8), dp(context, 18), 0);

        TextView totalView = Views.text(context, UiText.choose(language, "应收：", "Total: ") + "$" + total.amount(), 20, StyleGuide.INK);
        totalView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        panel.addView(totalView, Views.matchWrap());

        EditText receivedInput = Views.editText(context);
        receivedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        receivedInput.setHint(UiText.choose(language, "客户付款金额", "Monto recibido"));
        receivedInput.setText(Long.toString(total.amount()));
        panel.addView(receivedInput, Views.matchWrap());

        TextView changeView = Views.text(context, "", 24, StyleGuide.TEAL);
        changeView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        changeView.setPadding(0, dp(context, 8), 0, 0);
        panel.addView(changeView, Views.matchWrap());

        TextView errorView = Views.text(context, "", 14, StyleGuide.RED);
        panel.addView(errorView, Views.matchWrap());

        CashChangeResult[] currentResult = new CashChangeResult[1];
        Runnable refresh = () -> currentResult[0] = updateChange(language, calculator, total, receivedInput, changeView, errorView);
        receivedInput.addTextChangedListener(new SimpleTextWatcher(refresh));
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "现金结账", "Pago en efectivo"))
                .setView(panel)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "确认结账", "Confirmar venta"), null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            currentResult[0] = updateChange(language, calculator, total, receivedInput, changeView, errorView);
            if (currentResult[0] != null) {
                dialog.dismiss();
                callback.onConfirmed(currentResult[0]);
            }
        });
        return dialog;
    }

    private static CashChangeResult updateChange(
            AppLanguage language,
            CashChangeCalculator calculator,
            Money total,
            EditText receivedInput,
            TextView changeView,
            TextView errorView
    ) {
        Long received = parseAmount(receivedInput.getText().toString());
        if (received == null) {
            changeView.setText("");
            errorView.setText(UiText.choose(language, "请输入付款金额", "Ingrese monto recibido"));
            return null;
        }
        try {
            CashChangeResult result = calculator.calculate(total, Money.of(received));
            changeView.setText(UiText.choose(language, "找零：", "Cambio: ") + "$" + result.change().amount());
            errorView.setText("");
            return result;
        } catch (IllegalArgumentException ex) {
            changeView.setText(UiText.choose(language, "找零：", "Cambio: ") + "-");
            errorView.setText(UiText.choose(language, "付款金额不足", "Pago insuficiente"));
            return null;
        }
    }

    private static Long parseAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public interface CashPaymentCallback {
        void onConfirmed(CashChangeResult result);
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        private final Runnable afterChange;

        private SimpleTextWatcher(Runnable afterChange) {
            this.afterChange = afterChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            afterChange.run();
        }
    }
}
