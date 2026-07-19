package com.espsa.mobilepos.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Discount {
    public static final Discount NONE = new Discount(DiscountType.NONE, BigDecimal.ZERO);

    private final DiscountType type;
    private final BigDecimal value;

    private Discount(DiscountType type, BigDecimal value) {
        if (type == null) {
            throw new IllegalArgumentException("Discount type is required");
        }
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Discount value cannot be negative");
        }
        if (type == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percent discount cannot exceed 100");
        }
        this.type = type;
        this.value = normalized(value);
    }

    public static Discount percent(String percentage) {
        if (percentage == null) {
            throw new IllegalArgumentException("Discount percentage is required");
        }
        try {
            return percent(new BigDecimal(percentage));
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException("Discount percentage must be a plain decimal", exc);
        }
    }

    public static Discount percent(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "percentage");
        if (percentage.signum() == 0) {
            return NONE;
        }
        return new Discount(DiscountType.PERCENT, percentage);
    }

    public static Discount fixedAmount(Money amount) {
        if (amount == null || amount.isZero()) {
            return NONE;
        }
        return new Discount(DiscountType.FIXED_AMOUNT, amount.value());
    }

    public DiscountType type() {
        return type;
    }

    public BigDecimal decimalValue() {
        return value;
    }

    public String canonicalValue() {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public Money calculateAmount(Money base) {
        if (base == null || type == DiscountType.NONE) {
            return Money.ZERO;
        }
        if (type == DiscountType.PERCENT) {
            return base.percentOff(value);
        }
        Money fixed = Money.of(value);
        return base.compareTo(fixed) <= 0 ? base : fixed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Discount)) {
            return false;
        }
        Discount other = (Discount) obj;
        return type == other.type && value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + value.stripTrailingZeros().hashCode();
    }

    private static BigDecimal normalized(BigDecimal value) {
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.stripTrailingZeros();
    }
}
