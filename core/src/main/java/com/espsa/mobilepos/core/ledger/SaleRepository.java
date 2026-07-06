package com.espsa.mobilepos.core.ledger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public interface SaleRepository {
    Sale save(Sale sale);

    Optional<Sale> findById(String saleId);

    List<Sale> findByDate(LocalDate date, ZoneId zoneId);

    Sale voidSale(String saleId);
}

