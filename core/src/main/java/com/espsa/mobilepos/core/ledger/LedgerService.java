package com.espsa.mobilepos.core.ledger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class LedgerService {
    private final SaleRepository saleRepository;
    private final ZoneId zoneId;

    public LedgerService(SaleRepository saleRepository, ZoneId zoneId) {
        this.saleRepository = saleRepository;
        this.zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
    }

    public List<Sale> salesForDate(LocalDate date) {
        return saleRepository.findByDate(date, zoneId);
    }

    public DailySummary dailySummary(LocalDate date) {
        return DailySummary.fromSales(date, salesForDate(date));
    }
}

