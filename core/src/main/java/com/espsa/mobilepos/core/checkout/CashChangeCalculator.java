package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.model.Money;

import java.util.Objects;

public final class CashChangeCalculator {
    public CashChangeResult calculate(Money total, Money received) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(received, "received");
        if (received.compareTo(total) < 0) {
            throw new IllegalArgumentException("Cash received is less than total");
        }
        return new CashChangeResult(total, received, received.minusCapped(total));
    }
}
