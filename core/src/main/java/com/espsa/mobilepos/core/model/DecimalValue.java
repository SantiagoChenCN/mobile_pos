package com.espsa.mobilepos.core.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DecimalValue implements Comparable<DecimalValue> {
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^(?:0|[0-9]+)(?:\\.[0-9]+)?$");
    private static final int MAX_CANONICAL_LENGTH = 32;

    public enum Kind {
        MONEY(15, 4),
        QUANTITY(11, 4);

        private final int maxIntegerDigits;
        private final int maxFractionDigits;

        Kind(int maxIntegerDigits, int maxFractionDigits) {
            this.maxIntegerDigits = maxIntegerDigits;
            this.maxFractionDigits = maxFractionDigits;
        }
    }

    private final String rawText;
    private final String canonicalText;
    private final BigDecimal value;
    private final Kind kind;

    private DecimalValue(String rawText, String canonicalText, BigDecimal value, Kind kind) {
        this.rawText = rawText;
        this.canonicalText = canonicalText;
        this.value = value;
        this.kind = kind;
    }

    public static DecimalValue parse(String rawText, Kind kind) {
        Objects.requireNonNull(kind, "kind");
        if (rawText == null || !PLAIN_DECIMAL.matcher(rawText).matches()) {
            throw new IllegalArgumentException("Decimal text must be an unsigned plain decimal");
        }
        BigDecimal value = new BigDecimal(rawText);
        String canonical = canonicalText(value);
        int point = canonical.indexOf('.');
        String integerPart = point < 0 ? canonical : canonical.substring(0, point);
        String fractionPart = point < 0 ? "" : canonical.substring(point + 1);
        if (integerPart.length() > kind.maxIntegerDigits) {
            throw new IllegalArgumentException("Decimal integer digits exceed the business limit");
        }
        if (fractionPart.length() > kind.maxFractionDigits) {
            throw new IllegalArgumentException("Decimal fraction digits exceed the business limit");
        }
        if (canonical.length() > MAX_CANONICAL_LENGTH) {
            throw new IllegalArgumentException("Canonical decimal text is too long");
        }
        return new DecimalValue(rawText, canonical, value, kind);
    }

    public String rawText() {
        return rawText;
    }

    public String canonicalText() {
        return canonicalText;
    }

    public BigDecimal value() {
        return value;
    }

    public Kind kind() {
        return kind;
    }

    public DecimalValue multiplyExact(DecimalValue other, Kind resultKind) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(resultKind, "resultKind");
        return parse(canonicalText(value.multiply(other.value)), resultKind);
    }

    @Override
    public int compareTo(DecimalValue other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value);
    }

    private static String canonicalText(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Decimal value cannot be negative");
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
