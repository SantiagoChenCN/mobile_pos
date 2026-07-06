package com.espsa.mobilepos.core.model;

public final class Discount {
    public static final Discount NONE = new Discount(DiscountType.NONE, 0);

    private final DiscountType type;
    private final long value;

    private Discount(DiscountType type, long value) {
        if (type == null) {
            throw new IllegalArgumentException("Discount type is required");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Discount value cannot be negative");
        }
        if (type == DiscountType.PERCENT && value > 10000) {
            throw new IllegalArgumentException("Percent discount uses basis points and cannot exceed 10000");
        }
        this.type = type;
        this.value = value;
    }

    public static Discount percent(int basisPoints) {
        if (basisPoints == 0) {
            return NONE;
        }
        return new Discount(DiscountType.PERCENT, basisPoints);
    }

    public static Discount fixedAmount(Money amount) {
        if (amount == null || amount.amount() == 0) {
            return NONE;
        }
        return new Discount(DiscountType.FIXED_AMOUNT, amount.amount());
    }

    public DiscountType type() {
        return type;
    }

    public long value() {
        return value;
    }

    public Money calculateAmount(Money base) {
        if (base == null || type == DiscountType.NONE) {
            return Money.ZERO;
        }
        if (type == DiscountType.PERCENT) {
            return base.percentOff((int) value);
        }
        return Money.of(Math.min(base.amount(), value));
    }
}

