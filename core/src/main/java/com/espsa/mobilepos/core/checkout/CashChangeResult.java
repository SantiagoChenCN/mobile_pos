package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.model.Money;

import java.util.Objects;

public final class CashChangeResult {
    private final Money total;
    private final Money received;
    private final Money change;

    public CashChangeResult(Money total, Money received, Money change) {
        this.total = Objects.requireNonNull(total, "total");
        this.received = Objects.requireNonNull(received, "received");
        this.change = Objects.requireNonNull(change, "change");
    }

    public Money total() {
        return total;
    }

    public Money received() {
        return received;
    }

    public Money change() {
        return change;
    }
}
