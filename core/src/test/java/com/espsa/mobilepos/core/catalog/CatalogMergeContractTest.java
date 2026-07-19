package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.checkout.ProductNotFoundException;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;
import com.espsa.mobilepos.core.model.Quantity;

import java.util.Arrays;
import java.util.List;

/** MB-06 host contract coverage; it deliberately does not connect Android SQLite or checkout UI. */
public final class CatalogMergeContractTest {
    private static int assertions;

    public static void main(String[] args) {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        Product local = product("local-1", "same", "Local collision", ProductOrigin.LOCAL, null, 0, false);
        Product legacyByBarcode = product("import-x", "same", "Legacy barcode evidence only", ProductOrigin.LEGACY_IMPORT, Money.of("3"), 2, false);
        Product legacyById = product("7", "old-seven", "Legacy exact GID", ProductOrigin.LEGACY_IMPORT, Money.of("4"), 2, false);
        Product legacyNormal = product("legacy-2", "legacy", "Legacy retained", ProductOrigin.LEGACY_IMPORT, Money.of("5"), 3, false);
        repository.replaceAll(Arrays.asList(local, legacyByBarcode, legacyById, legacyNormal));
        ProductCatalogService catalog = new ProductCatalogService(repository);

        Product activeSync = sync(7, "same", "Sync authoritative", "", "", "2099.99", false);
        Product stoppedSync = sync(8, "stopped", "Stopped source", "", "", "12.50", true);
        ProductCatalogCandidate candidate = catalog.replaceWithV2SyncProducts(Arrays.asList(activeSync, stoppedSync));

        assertEquals("candidate contains preserved local, preserved legacy, and rebuilt sync", 5, candidate.products().size());
        assertFalse("exact legacy numeric GID is replaced", repository.findById("7").isPresent());
        assertTrue("barcode does not create sync identity", repository.findById("import-x").isPresent());
        assertEquals("barcode collision selects sync", "ms2011:7", repository.findByBarcode("same").get().id());
        assertEquals("local collision remains visible", 1, catalog.barcodeConflicts().size());
        assertEquals("conflict records local product", "local-1", catalog.barcodeConflicts().get(0).localProducts().get(0).id());
        assertEquals("active sync lookup", ProductBarcodeLookup.Status.FOUND, catalog.lookupBarcode("same").status());
        assertEquals("stopped sync lookup", ProductBarcodeLookup.Status.STOPPED, catalog.lookupBarcode("stopped").status());
        assertFalse("normal repository lookup refuses stopped authoritative sync", repository.findByBarcode("stopped").isPresent());
        CheckoutService checkout = new CheckoutService(repository, new DefaultPriceCalculator(), new InMemorySaleRepository());
        expectNotFound("legacy checkout cannot add stopped authoritative sync", () -> checkout.addProductByBarcode(checkout.startCart(), "stopped", Quantity.one()));
        assertEquals("not-found lookup", ProductBarcodeLookup.Status.NOT_FOUND, catalog.lookupBarcode("missing").status());
        assertTrue("normal search hides stopped sync", catalog.searchByName("Stopped source").isEmpty());
        assertEquals("stopped remains retained for explicit barcode state", 1, repository.findAllByBarcode("stopped").size());
        assertEquals("sync empty category remains empty", "", activeSync.category());
        assertEquals("sync empty unit remains empty", "", activeSync.unitName());
        assertNull("sync simple promotion is not enabled", activeSync.promotionPrice());
        assertEquals("sync promotion threshold is zero", 0, activeSync.promotionMinQuantity());
        assertTrue("legacy simple promotion remains compatible", repository.findById("legacy-2").get().hasQuantityPromotion());
        List<Product> persistence = ProductCatalogCandidate.localPersistenceProducts(repository.all());
        assertEquals("local persistence keeps LOCAL and LEGACY only", 3, persistence.size());
        assertTrue("local persistence retains LOCAL", containsId(persistence, "local-1"));
        assertTrue("local persistence retains LEGACY", containsId(persistence, "import-x"));
        assertFalse("local persistence excludes synchronized product", containsId(persistence, "ms2011:7"));
        expectFailure("local persistence rejects null list", () -> ProductCatalogCandidate.localPersistenceProducts(null));
        expectFailure("local persistence rejects null element", () -> ProductCatalogCandidate.localPersistenceProducts(Arrays.asList(local, null)));
        expectUnsupported("local persistence result is immutable", () -> persistence.add(local));

        int before = repository.count();
        Product duplicate = sync(7, "other", "Duplicate identity", "cat", "un", "1", false);
        expectFailure("duplicate source key fails before repository replacement", () -> catalog.replaceWithV2SyncProducts(Arrays.asList(activeSync, duplicate)));
        assertEquals("failed candidate keeps repository untouched", before, repository.count());
        assertTrue("failed candidate keeps local product", repository.findById("local-1").isPresent());
        Product duplicateBarcode = sync(9, "same", "Duplicate barcode", "cat", "un", "1", false);
        expectFailure("duplicate synchronized barcode fails before replacement", () -> catalog.replaceWithV2SyncProducts(Arrays.asList(activeSync, duplicateBarcode)));
        assertEquals("duplicate barcode keeps repository untouched", before, repository.count());
        Product invalidSnapshot = new Product("ms2011:10", "ten", "Bad snapshot", "", "", Money.of("1"), null, 0, false,
                ProductOrigin.MS2011_SYNC, "ms2011:10", "not-a-snapshot", false);
        expectFailure("invalid sync snapshot id fails before replacement", () -> catalog.replaceWithV2SyncProducts(Arrays.asList(invalidSnapshot)));
        assertEquals("invalid snapshot keeps repository untouched", before, repository.count());

        ProductCatalogCandidate rollbackCandidate = ProductCatalogCandidate.merge(repository.all(), Arrays.asList(sync(10, "rollback", "Rollback sync", "", "", "1", false)));
        java.util.List<Product> beforeRollback = repository.all();
        java.util.List<ProductBarcodeConflict> conflictsBeforeRollback = catalog.barcodeConflicts();
        ProductCatalogService.CatalogRollback rollback = catalog.applyVerifiedCandidate(rollbackCandidate);
        assertTrue("verified candidate apply replaces catalog", repository.findById("ms2011:10").isPresent());
        catalog.restore(rollback);
        assertEquals("rollback restores products", beforeRollback, repository.all());
        assertEquals("rollback restores barcode conflicts", conflictsBeforeRollback, catalog.barcodeConflicts());

        FailingReplaceRepository failingRepository = new FailingReplaceRepository();
        failingRepository.replaceAll(Arrays.asList(local));
        ProductCatalogService failingCatalog = new ProductCatalogService(failingRepository);
        ProductCatalogCandidate failingCandidate = ProductCatalogCandidate.merge(failingRepository.all(), Arrays.asList(sync(11, "atomic", "Atomic replacement", "", "", "1", false)));
        failingRepository.failNextReplace = true;
        expectRuntimeFailure("repository replacement failure is fail-closed", () -> failingCatalog.applyVerifiedCandidate(failingCandidate));
        assertEquals("replacement failure restores previous products", Arrays.asList(local), failingRepository.all());
        assertTrue("replacement failure does not expose candidate", !failingRepository.findById("ms2011:11").isPresent());

        Product invalidPromotionSync = new Product("ms2011:9", "p", "bad", "", "", Money.of("1"), Money.of("0.5"), 2, false,
                ProductOrigin.MS2011_SYNC, "ms2011:9", "ms2011-20260718T120000Z-0123456789ab", false);
        expectFailure("sync candidate cannot carry old simple promotion", () -> ProductCatalogCandidate.merge(repository.all(), Arrays.asList(invalidPromotionSync)));

        System.out.println("Catalog merge contract test passed: " + assertions + " assertions");
    }

    private static Product sync(long gid, String barcode, String name, String category, String unit, String price, boolean stopped) {
        String key = "ms2011:" + gid;
        return new Product(key, barcode, name, category, unit, Money.of(price), null, 0, false,
                ProductOrigin.MS2011_SYNC, key, "ms2011-20260718T120000Z-0123456789ab", stopped);
    }

    private static Product product(String id, String barcode, String name, ProductOrigin origin, Money promotion, int minimum, boolean stopped) {
        return new Product(id, barcode, name, "", "un", Money.of("10"), promotion, minimum, false, origin, "", "", stopped);
    }

    private interface Checked { void run() throws Exception; }
    private static boolean containsId(java.util.List<Product> products, String id) { for (Product product : products) if (product.id().equals(id)) return true; return false; }
    private static void expectFailure(String name, Checked checked) { assertions++; try { checked.run(); throw new AssertionError(name); } catch (IllegalArgumentException expected) { } catch (Exception unexpected) { throw new AssertionError(name, unexpected); } }
    private static void expectRuntimeFailure(String name, Checked checked) { assertions++; try { checked.run(); throw new AssertionError(name); } catch (IllegalStateException expected) { } catch (Exception unexpected) { throw new AssertionError(name, unexpected); } }
    private static void expectUnsupported(String name, Checked checked) { assertions++; try { checked.run(); throw new AssertionError(name); } catch (UnsupportedOperationException expected) { } catch (Exception unexpected) { throw new AssertionError(name, unexpected); } }
    private static void expectNotFound(String name, Checked checked) { assertions++; try { checked.run(); throw new AssertionError(name); } catch (ProductNotFoundException expected) { } catch (Exception unexpected) { throw new AssertionError(name, unexpected); } }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertFalse(String name, boolean value) { assertTrue(name, !value); }
    private static void assertNull(String name, Object value) { assertions++; if (value != null) throw new AssertionError(name); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }

    private static final class FailingReplaceRepository implements ProductRepository {
        private final InMemoryProductRepository delegate = new InMemoryProductRepository();
        private boolean failNextReplace;
        @Override public List<Product> all() { return delegate.all(); }
        @Override public java.util.Optional<Product> findById(String id) { return delegate.findById(id); }
        @Override public java.util.Optional<Product> findByBarcode(String barcode) { return delegate.findByBarcode(barcode); }
        @Override public List<Product> findAllByBarcode(String barcode) { return delegate.findAllByBarcode(barcode); }
        @Override public List<Product> searchByName(String query, int limit) { return delegate.searchByName(query, limit); }
        @Override public void replaceAll(List<Product> products) { if (failNextReplace) { failNextReplace = false; throw new IllegalStateException("fixture replacement failure"); } delegate.replaceAll(products); }
        @Override public void upsert(Product product) { delegate.upsert(product); }
        @Override public java.util.Optional<Product> deleteById(String id) { return delegate.deleteById(id); }
        @Override public boolean barcodeExists(String barcode, String excludedId) { return delegate.barcodeExists(barcode, excludedId); }
        @Override public boolean exactNameExists(String name, String excludedId) { return delegate.exactNameExists(name, excludedId); }
        @Override public int count() { return delegate.count(); }
    }
}
