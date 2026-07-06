package com.espsa.mobilepos.core.exporter;

import com.espsa.mobilepos.core.ledger.Sale;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

public interface SalesExportPort {
    void exportSales(List<Sale> sales, LocalDate fromDate, LocalDate toDate, OutputStream outputStream) throws SalesExportException;
}

