package com.espsa.mobilepos.core.ledger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySaleRepository implements SaleRepository {
    private final Map<String, Sale> sales = new LinkedHashMap<String, Sale>();

    @Override
    public Sale save(Sale sale) {
        sales.put(sale.id(), sale);
        return sale;
    }

    @Override
    public Optional<Sale> findById(String saleId) {
        return Optional.ofNullable(sales.get(saleId));
    }

    @Override
    public List<Sale> findByDate(LocalDate date, ZoneId zoneId) {
        ZoneId effectiveZone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        List<Sale> result = new ArrayList<Sale>();
        for (Sale sale : sales.values()) {
            LocalDate saleDate = sale.createdAt().atZone(effectiveZone).toLocalDate();
            if (saleDate.equals(date)) {
                result.add(sale);
            }
        }
        return result;
    }

    @Override
    public Sale voidSale(String saleId) {
        Sale sale = sales.get(saleId);
        if (sale == null) {
            throw new IllegalArgumentException("Sale not found: " + saleId);
        }
        Sale voided = sale.voided();
        sales.put(saleId, voided);
        return voided;
    }
}

