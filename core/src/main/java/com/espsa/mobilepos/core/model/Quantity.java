package com.espsa.mobilepos.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Quantity implements Comparable<Quantity> {
    private static final Quantity ONE = new Quantity(BigDecimal.ONE);

    private final BigDecimal value;

    private Quantity(BigDecimal value) {
        DecimalValue decimal = DecimalValue.parse(value.toPlainString(), DecimalValue.Kind.QUANTITY);
        this.value = decimal.value();
    }

    public static Quantity one() {
        return ONE;
    }

    public static Quantity of(String value) {
        DecimalValue decimal = DecimalValue.parse(value, DecimalValue.Kind.QUANTITY);
        if (decimal.value().signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        return decimal.value().compareTo(BigDecimal.ONE) == 0 ? ONE : new Quantity(decimal.value());
    }

    public static Quantity of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return of(value.toPlainString());
    }

    public BigDecimal value() {
        return value;
    }

    public String canonicalText() {
        return value.stripTrailingZeros().toPlainString();
    }

    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "other");
        return of(value.add(other.value));
    }

    public boolean isInteger() {
        return value.stripTrailingZeros().scale() <= 0;
    }

    @Override
    public int compareTo(Quantity other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Quantity)) {
            return false;
        }
        Quantity other = (Quantity) obj;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return canonicalText();
    }
}
