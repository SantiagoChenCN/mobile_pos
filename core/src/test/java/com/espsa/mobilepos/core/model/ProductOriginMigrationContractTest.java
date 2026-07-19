package com.espsa.mobilepos.core.model;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.catalog.ProductMigrationIssue;
import com.espsa.mobilepos.core.catalog.ProductMigrationReport;
import com.espsa.mobilepos.core.catalog.ProductMigrationSelection;
import com.espsa.mobilepos.core.catalog.ProductMigrationService;
import com.espsa.mobilepos.core.editing.ProductDraft;
import com.espsa.mobilepos.core.editing.ProductEditingService;
import com.espsa.mobilepos.core.editing.ProductOptionProvider;
import com.espsa.mobilepos.core.editing.ProductPersistencePort;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProductOriginMigrationContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Product legacy = new Product("legacy-1", "7791", "Legacy", "almacen", "un", Money.of("12.50"), null, 0, false);
        assertEquals("old constructor defaults legacy", ProductOrigin.LEGACY_IMPORT, legacy.origin());
        assertEquals("legacy has no source key", "", legacy.sourceProductKey());

        Product local = new Product("local-1", "7792", "Local", "almacen", "un", Money.of("1"), null, 0, false,
                ProductOrigin.LOCAL, "", "", false);
        Product sync = new Product("7318", "7793", "Sync", "almacen", "un", Money.of("2"), null, 0, false,
                ProductOrigin.MS2011_SYNC, "ms2011:7318", "ms2011-20260718T120000Z-0123456789ab", true);
        assertTrue("stopped round trip model", sync.stopped());
        assertEquals("strict sync key retained", "ms2011:7318", sync.sourceProductKey());
        expectInvalid("barcode cannot grant sync identity", () -> new Product("bad", "7318", "Bad", "almacen", "un", Money.of("1"), null, 0, false,
                ProductOrigin.MS2011_SYNC, "7318", "", false));
        expectInvalid("local cannot claim source key", () -> new Product("bad", "", "Bad", "almacen", "un", Money.of("1"), null, 0, false,
                ProductOrigin.LOCAL, "ms2011:7318", "", false));

        ProductMigrationReport report = new ProductMigrationReport(Collections.singletonList(
                new ProductMigrationIssue(legacy.id(), legacy.barcode(), "ambiguous legacy provenance")
        ));
        assertEquals("only two human choices", 2, report.issues().get(0).allowedChoices().size());
        assertEquals("first choice local", ProductOrigin.LOCAL, report.issues().get(0).allowedChoices().get(0));
        expectInvalid("human cannot select sync", () -> new ProductMigrationSelection(legacy.id(), ProductOrigin.MS2011_SYNC));
        expectInvalid("duplicate report issue rejected", () -> new ProductMigrationReport(Arrays.asList(
                new ProductMigrationIssue(legacy.id(), legacy.barcode(), "first"),
                new ProductMigrationIssue(legacy.id(), legacy.barcode(), "duplicate")
        )));
        ProductMigrationService migration = new ProductMigrationService();
        List<ProductMigrationSelection> selections = Collections.singletonList(new ProductMigrationSelection(legacy.id(), ProductOrigin.LOCAL));
        List<Product> migrated = migration.applySelections(Arrays.asList(legacy, sync), report, selections);
        assertEquals("explicit selection changes only reported legacy", ProductOrigin.LOCAL, migrated.get(0).origin());
        assertEquals("sync remains sync", ProductOrigin.MS2011_SYNC, migrated.get(1).origin());
        assertTrue("resolved issue removed", migration.remainingReport(report, selections).isEmpty());
        expectInvalid("reported sync product cannot be reclassified", () -> migration.applySelections(
                Collections.singletonList(sync),
                new ProductMigrationReport(Collections.singletonList(
                        new ProductMigrationIssue(sync.id(), sync.barcode(), "malicious report")
                )),
                Collections.singletonList(new ProductMigrationSelection(sync.id(), ProductOrigin.LOCAL))
        ));
        expectInvalid("missing reported product cannot be resolved", () -> migration.applySelections(
                Collections.singletonList(legacy),
                new ProductMigrationReport(Collections.singletonList(
                        new ProductMigrationIssue("missing", "", "missing product")
                )),
                Collections.singletonList(new ProductMigrationSelection("missing", ProductOrigin.LOCAL))
        ));

        InMemoryProductRepository repository = new InMemoryProductRepository();
        repository.replaceAll(Arrays.asList(local, sync));
        ProductEditingService editing = new ProductEditingService(repository, new ProductPersistencePort() {
            @Override public void saveManualProducts(List<Product> products) { }
        }, new ProductOptionProvider(repository::all));
        assertTrue("sync update rejected", !editing.updateProduct(sync.id(), draft("7793", "Changed")).success());
        assertTrue("sync delete rejected", !editing.deleteProduct(sync.id()).success());
        assertTrue("sync remains after delete rejection", repository.findById(sync.id()).isPresent());
        assertTrue("local update remains compatible", editing.updateProduct(local.id(), draft("7792", "Local Changed")).success());
        assertEquals("local origin preserved", ProductOrigin.LOCAL, repository.findById(local.id()).get().origin());
        assertTrue("created product local", editing.createProduct(draft("7794", "Created")).product().origin() == ProductOrigin.LOCAL);

        System.out.println("Product origin migration contract test passed: " + assertions + " assertions");
    }

    private static ProductDraft draft(String barcode, String name) { return new ProductDraft(barcode, name, "almacen", "un", "10", "", ""); }
    private static void expectInvalid(String name, Runnable action) { assertions++; try { action.run(); throw new AssertionError(name); } catch (IllegalArgumentException expected) { } }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + " actual " + actual); }
}
