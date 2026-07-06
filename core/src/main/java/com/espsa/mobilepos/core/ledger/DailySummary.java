package com.espsa.mobilepos.core.ledger;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.SaleStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DailySummary {
    private final LocalDate date;
    private final int saleCount;
    private final int voidedCount;
    private final Money total;
    private final Money voidedTotal;
    private final Map<PaymentMethod, Money> totalsByPaymentMethod;

    private DailySummary(LocalDate date, int saleCount, int voidedCount, Money total, Money voidedTotal, Map<PaymentMethod, Money> totalsByPaymentMethod) {
        this.date = date;
        this.saleCount = saleCount;
        this.voidedCount = voidedCount;
        this.total = total;
        this.voidedTotal = voidedTotal;
        this.totalsByPaymentMethod = totalsByPaymentMethod;
    }

    public static DailySummary fromSales(LocalDate date, List<Sale> sales) {
        int saleCount = 0;
        int voidedCount = 0;
        Money total = Money.ZERO;
        Money voidedTotal = Money.ZERO;
        Map<PaymentMethod, Money> byPayment = new EnumMap<PaymentMethod, Money>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            byPayment.put(method, Money.ZERO);
        }

        if (sales != null) {
            for (Sale sale : sales) {
                if (sale.status() == SaleStatus.VOIDED) {
                    voidedCount++;
                    voidedTotal = voidedTotal.plus(sale.total());
                } else {
                    saleCount++;
                    total = total.plus(sale.total());
                    byPayment.put(sale.paymentMethod(), byPayment.get(sale.paymentMethod()).plus(sale.total()));
                }
            }
        }

        return new DailySummary(date, saleCount, voidedCount, total, voidedTotal, byPayment);
    }

    public LocalDate date() {
        return date;
    }

    public int saleCount() {
        return saleCount;
    }

    public int voidedCount() {
        return voidedCount;
    }

    public Money total() {
        return total;
    }

    public Money voidedTotal() {
        return voidedTotal;
    }

    public Money totalFor(PaymentMethod method) {
        Money value = totalsByPaymentMethod.get(method);
        return value == null ? Money.ZERO : value;
    }
}
