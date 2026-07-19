package com.espsa.mobilepos.ui;

import com.espsa.mobilepos.core.model.Money;

import java.util.Objects;

public final class MoneyText {
    private MoneyText() {
    }

    public static String format(Money money) {
        return Objects.requireNonNull(money, "money").canonicalText();
    }

    public static String currency(Money money) {
        return "$" + format(money);
    }
}
