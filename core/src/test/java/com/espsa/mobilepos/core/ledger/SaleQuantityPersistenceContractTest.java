package com.espsa.mobilepos.core.ledger;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.exporter.CsvSalesExportAdapter;
import com.espsa.mobilepos.core.exporter.SalesExportException;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SaleQuantityPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Product weighted = new Product(
                "weighted-1", "2001", "Weighted product", "almacen", "kg",
                Money.of("2.5"), null, 0, false
        );
        InMemoryProductRepository products = new InMemoryProductRepository();
        products.replaceAll(Arrays.asList(weighted));
        CheckoutService checkout = new CheckoutService(
                products, new DefaultPriceCalculator(), new InMemorySaleRepository()
        );

        Cart cart = checkout.startCart();
        checkout.addProductByBarcode(cart, "2001", Quantity.of("1.2500"));
        Sale sale = checkout.checkout(cart, PaymentMethod.CASH);
        SaleLine checkedOutLine = sale.lines().get(0);
        assertQuantity("1.25", checkedOutLine.quantity(),
                "checkout snapshot preserves fractional quantity");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CsvSalesExportAdapter().exportSales(Arrays.asList(sale), null, null, output);
        String csv = output.toString(StandardCharsets.UTF_8.name());
        assertTrue(csv.contains(",1.25,2.5,2.5,3.125,"),
                "CSV writes canonical decimal quantity without truncation");

        SaleLine legacyInteger = saleLine(Quantity.of("2"));
        assertQuantity("2", legacyInteger.quantity(), "historical integer maps exactly to Quantity");
        assertTrue(SaleLine.class.getMethod("quantity").getReturnType() == Quantity.class,
                "SaleLine quantity accessor cannot expose a truncating int");

        SaleLine historicalDecimal = saleLine(Quantity.of("2.5000"));
        SaleLine beyondInt = saleLine(Quantity.of("2147483648"));
        assertQuantity("2.5", historicalDecimal.quantity(),
                "legal historical decimal quantity is preserved");
        assertQuantity("2147483648", beyondInt.quantity(),
                "quantity above int range is preserved");
        ByteArrayOutputStream wideOutput = new ByteArrayOutputStream();
        new CsvSalesExportAdapter().exportSales(
                Arrays.asList(saleWithLines(historicalDecimal, beyondInt)), null, null, wideOutput
        );
        String wideCsv = wideOutput.toString(StandardCharsets.UTF_8.name());
        assertTrue(wideCsv.contains(",2.5,2.5,2.5,3.125,"),
                "CSV preserves legal historical decimal quantity");
        assertTrue(wideCsv.contains(",2147483648,2.5,2.5,3.125,"),
                "CSV preserves quantity above int range without narrowing");

        expectSalesExportFailure(
                () -> new CsvSalesExportAdapter().exportSales(Arrays.asList(sale), null, null, null),
                "Output stream is required",
                "null output stream fails explicitly"
        );
        expectSalesExportFailure(
                () -> new CsvSalesExportAdapter().exportSales(
                        Arrays.asList(sale), null, null, new FailingOutputStream()
                ),
                "Failed to write CSV export",
                "write or flush failure is converted to SalesExportException"
        );

        expectIllegal(() -> saleLine(Quantity.of("0")), "zero quantity rejects");
        expectIllegal(() -> saleLine(Quantity.of("-1")), "negative quantity rejects");
        expectIllegal(() -> saleLine(Quantity.of("1.00001")), "scale above four rejects");
        expectIllegal(() -> saleLine(Quantity.of("100000000000.0000")),
                "integer digits above eleven reject");
        expectIllegal(() -> saleLine((Quantity) null), "null quantity rejects");

        System.out.println("Sale quantity persistence contract test passed");
    }

    private static SaleLine saleLine(Quantity quantity) {
        return new SaleLine(
                "p-1", "1001", "Product", "almacen", quantity,
                Money.of("2.5"), Money.of("2.5"), Money.of("3.125"),
                Discount.NONE, Money.ZERO, Money.of("3.125"),
                false, false, false
        );
    }

    private static Sale saleWithLines(SaleLine... lines) {
        return new Sale(
                "persisted-sale", java.time.Instant.parse("2026-07-18T12:00:00Z"), PaymentMethod.CASH,
                Money.of("6.25"), Discount.NONE, Money.ZERO, Money.of("6.25"),
                com.espsa.mobilepos.core.model.SaleStatus.NORMAL, Arrays.asList(lines)
        );
    }

    private static void assertQuantity(String expected, Quantity actual, String label) {
        if (actual == null || !expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectIllegal(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
        } catch (NullPointerException expected) {
        }
    }

    private static void expectSalesExportFailure(
            ExportAction action,
            String expectedMessage,
            String label
    ) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (SalesExportException expected) {
            if (!expectedMessage.equals(expected.getMessage())) {
                throw new AssertionError(
                        label + ": expected message=" + expectedMessage
                                + ", actual=" + expected.getMessage()
                );
            }
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private interface ExportAction {
        void run() throws SalesExportException;
    }

    private static final class FailingOutputStream extends OutputStream {
        @Override
        public void write(int value) throws IOException {
            throw new IOException("simulated write failure");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("simulated flush failure");
        }
    }
}
