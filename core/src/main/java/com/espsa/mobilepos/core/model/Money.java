package com.espsa.mobilepos.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Money implements Comparable<Money> {
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal value;

    private Money(BigDecimal value) {
        DecimalValue decimal = DecimalValue.parse(value.toPlainString(), DecimalValue.Kind.MONEY);
        this.value = decimal.value();
    }

    public static Money of(String amount) {
        DecimalValue decimal = DecimalValue.parse(amount, DecimalValue.Kind.MONEY);
        return decimal.value().signum() == 0 ? ZERO : new Money(decimal.value());
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.signum() == 0 ? ZERO : new Money(amount);
    }

    public BigDecimal value() {
        return value;
    }

    public String canonicalText() {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        return Money.of(value.add(other.value));
    }

    public Money minusCapped(Money other) {
        Objects.requireNonNull(other, "other");
        if (value.compareTo(other.value) <= 0) {
            return ZERO;
        }
        return Money.of(value.subtract(other.value));
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return Money.of(value.multiply(BigDecimal.valueOf(quantity)));
    }

    public Money percentOff(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "percentage");
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Discount percentage must be between 0 and 100");
        }
        return Money.of(value.multiply(percentage).movePointLeft(2));
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Money)) {
            return false;
        }
        Money other = (Money) obj;
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
