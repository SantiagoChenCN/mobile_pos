package com.espsa.mobilepos.ui;

import com.espsa.mobilepos.core.model.Quantity;

import java.util.Objects;
import java.util.function.Consumer;

public final class QuantityText {
    private QuantityText() {
    }

    public static String format(Quantity quantity) {
        return Objects.requireNonNull(quantity, "quantity").canonicalText();
    }

    public static Quantity parse(String input) {
        return Quantity.of(NumberTextParser.normalizeUnsignedDecimal(input));
    }

    public static Quantity parseOrNull(String input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean applyIfValid(String input, Consumer<Quantity> callback) {
        Objects.requireNonNull(callback, "callback");
        Quantity quantity = parseOrNull(input);
        if (quantity == null) {
            return false;
        }
        callback.accept(quantity);
        return true;
    }
}
