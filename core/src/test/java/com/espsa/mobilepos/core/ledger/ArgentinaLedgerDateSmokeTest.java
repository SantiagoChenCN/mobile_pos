package com.espsa.mobilepos.core.ledger;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.SaleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;

public final class ArgentinaLedgerDateSmokeTest {
    public static void main(String[] args) {
        InMemorySaleRepository repository = new InMemorySaleRepository();
        repository.save(new Sale(
                "sale-boundary",
                Instant.parse("2026-07-11T02:30:00Z"),
                PaymentMethod.TRANSFERENCIA,
                Money.of("100"),
                Discount.NONE,
                Money.ZERO,
                Money.of("100"),
                SaleStatus.NORMAL,
                Collections.<SaleLine>emptyList()
        ));

        LedgerService ledger = new LedgerService(
                repository,
                ZoneId.of("America/Argentina/Buenos_Aires")
        );
        assertTrue("UTC instant belongs to Argentina previous date", ledger
                .salesForDate(LocalDate.of(2026, 7, 10)).size() == 1);
        assertTrue("UTC instant does not belong to next Argentina date", ledger
                .salesForDate(LocalDate.of(2026, 7, 11)).isEmpty());

        System.out.println("Argentina ledger date smoke test passed");
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
