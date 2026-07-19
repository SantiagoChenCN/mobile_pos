package com.espsa.mobilepos.core.importer;

import com.espsa.mobilepos.core.exporter.CsvSalesExportAdapter;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.ledger.SaleLine;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;
import com.espsa.mobilepos.core.model.SaleStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ExactMoneyImportExportContractTest {
    public static void main(String[] args) throws Exception {
        CsvProductImportAdapter csvImporter = new CsvProductImportAdapter();
        ProductImportResult imported = csvImporter.importProducts(
                new ByteArrayInputStream((
                        "barcode,name,price,category,unit\n"
                                + "1001,Decimal product,2099.9900,almacen,un\n"
                                + "1002,Legacy integer,1500,almacen,un\n"
                ).getBytes(StandardCharsets.UTF_8)),
                "products.csv"
        );
        assertMoney("2099.99", imported.products().get(0).salePrice(), "CSV decimal import");
        assertMoney("1500", imported.products().get(1).salePrice(), "CSV legacy integer import");

        Map<String, String> row = new HashMap<String, String>();
        row.put("GID", "7");
        row.put("GBarcode", "7001");
        row.put("GNameX", "Mingsheng decimal");
        row.put("RTypeName", "almacen");
        row.put("UName", "un");
        row.put("GSalePrice", "2099.9900");
        row.put("GHuiPrice", "1499.5000");
        row.put("GHuiPriceCount", "2.0000");
        Product mapped = new MingshengProductMapper().fromGoodListRow(row);
        assertMoney("2099.99", mapped.salePrice(), "Mingsheng decimal sale price");
        assertMoney("1499.5", mapped.promotionPrice(), "Mingsheng decimal promotion price");
        assertTrue(mapped.promotionMinQuantity() == 2, "legacy integer quantity remains exact");

        SaleLine line = new SaleLine(
                mapped.id(), mapped.barcode(), mapped.name(), mapped.category(), Quantity.one(),
                Money.of("2099.99"), Money.of("2099.99"), Money.of("2099.99"),
                Discount.percent("10"), Money.of("209.999"), Money.of("1889.991"),
                false, false, false
        );
        Sale sale = new Sale(
                "sale-decimal", Instant.parse("2026-07-17T12:00:00Z"), PaymentMethod.CASH,
                Money.of("1889.991"), Discount.fixedAmount(Money.of("0.001")),
                Money.of("0.001"), Money.of("1889.99"), SaleStatus.NORMAL, Arrays.asList(line)
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CsvSalesExportAdapter().exportSales(Arrays.asList(sale), null, null, output);
        String csv = output.toString(StandardCharsets.UTF_8.name());
        assertTrue(csv.contains("1889.991,FIXED_AMOUNT,0.001,0.001,1889.99"),
                "sale CSV writes canonical original-currency decimals");
        assertTrue(csv.contains("2099.99,2099.99,2099.99,PERCENT,10,209.999,1889.991"),
                "sale item CSV writes exact decimals");

        expectFailure(() -> {
            Map<String, String> bad = new HashMap<String, String>(row);
            bad.put("GHuiPriceCount", "2.5");
            new MingshengProductMapper().fromGoodListRow(bad);
        }, "fractional legacy promotion quantity must not be rounded");

        System.out.println("Exact money import/export contract test passed");
    }

    private static void assertMoney(String expected, Money actual, String label) {
        if (actual == null || !expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectFailure(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ArithmeticException expected) {
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
