package com.espsa.mobilepos.core.exporter;

import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.ledger.SaleLine;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

public final class CsvSalesExportAdapter implements SalesExportPort {
    @Override
    public void exportSales(List<Sale> sales, LocalDate fromDate, LocalDate toDate, OutputStream outputStream) throws SalesExportException {
        if (outputStream == null) {
            throw new SalesExportException("Output stream is required");
        }

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.println("sales");
        writer.println("sale_id,created_at,status,payment_method,subtotal,cart_discount_type,cart_discount_value,cart_discount_amount,total");
        if (sales != null) {
            for (Sale sale : sales) {
                writer.println(join(
                        sale.id(),
                        sale.createdAt().toString(),
                        sale.status().name(),
                        sale.paymentMethod().name(),
                        Long.toString(sale.subtotal().amount()),
                        sale.cartDiscount().type().name(),
                        Long.toString(sale.cartDiscount().value()),
                        Long.toString(sale.cartDiscountAmount().amount()),
                        Long.toString(sale.total().amount())
                ));
            }
        }

        writer.println();
        writer.println("sale_items");
        writer.println("sale_id,barcode,name,category,quantity,original_unit_price,applied_unit_price,gross_subtotal,line_discount_type,line_discount_value,line_discount_amount,final_subtotal,automatic_promotion,manual_price,manual_product");
        if (sales != null) {
            for (Sale sale : sales) {
                for (SaleLine line : sale.lines()) {
                    writer.println(join(
                            sale.id(),
                            line.barcode(),
                            line.name(),
                            line.category(),
                            Integer.toString(line.quantity()),
                            Long.toString(line.originalUnitPrice().amount()),
                            Long.toString(line.appliedUnitPrice().amount()),
                            Long.toString(line.grossSubtotal().amount()),
                            line.lineDiscount().type().name(),
                            Long.toString(line.lineDiscount().value()),
                            Long.toString(line.lineDiscountAmount().amount()),
                            Long.toString(line.finalSubtotal().amount()),
                            Boolean.toString(line.automaticPromotionApplied()),
                            Boolean.toString(line.manualPriceApplied()),
                            Boolean.toString(line.manualPriceProduct())
                    ));
                }
            }
        }
        writer.flush();
        if (writer.checkError()) {
            throw new SalesExportException("Failed to write CSV export");
        }
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values[i]));
        }
        return builder.toString();
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        boolean needsQuote = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        if (!needsQuote) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
