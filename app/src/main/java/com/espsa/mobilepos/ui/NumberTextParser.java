package com.espsa.mobilepos.ui;

import com.espsa.mobilepos.core.model.Money;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class NumberTextParser {
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^(?:0|[0-9]+)(?:\\.[0-9]+)?$");
    private static final int MAX_INPUT_LENGTH = 32;

    private NumberTextParser() {
    }

    public static Money parseMoney(String input) {
        return Money.of(normalizeUnsignedDecimal(input));
    }

    public static Money parseMoneyOrNull(String input) {
        try {
            return parseMoney(input);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static BigDecimal parsePercentageOrNull(String input) {
        try {
            String normalized = normalizeUnsignedDecimal(input);
            BigDecimal percentage = new BigDecimal(normalized);
            if (percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                return null;
            }
            return percentage.stripTrailingZeros();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String normalizeUnsignedDecimal(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Number is required");
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Number length is invalid");
        }
        if (trimmed.indexOf('.') >= 0 && trimmed.indexOf(',') >= 0) {
            throw new IllegalArgumentException("Use either point or comma as decimal separator");
        }
        String normalized = trimmed.replace(',', '.');
        if (!PLAIN_DECIMAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Number must be an unsigned plain decimal");
        }
        return normalized;
    }
}
