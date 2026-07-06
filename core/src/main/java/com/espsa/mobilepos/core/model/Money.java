package com.espsa.mobilepos.core.model;

import java.util.Objects;

public final class Money implements Comparable<Money> {
    public static final Money ZERO = new Money(0);

    private final long amount;

    private Money(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        if (amount == 0) {
            return ZERO;
        }
        return new Money(amount);
    }

    public long amount() {
        return amount;
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        return Money.of(amount + other.amount);
    }

    public Money minusCapped(Money other) {
        Objects.requireNonNull(other, "other");
        return Money.of(Math.max(0, amount - other.amount));
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return Money.of(amount * quantity);
    }

    public Money percentOff(int basisPoints) {
        if (basisPoints < 0 || basisPoints > 10000) {
            throw new IllegalArgumentException("Discount basis points must be between 0 and 10000");
        }
        long discount = Math.round((amount * basisPoints) / 10000.0d);
        return Money.of(discount);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(amount, other.amount);
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
        return amount == other.amount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(amount);
    }

    @Override
    public String toString() {
        return Long.toString(amount);
    }
}

