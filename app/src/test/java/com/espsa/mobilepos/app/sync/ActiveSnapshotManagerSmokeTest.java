package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.catalog.ProductCatalogService;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.checkout.PricingSnapshotRef;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;

/** CB-02 host boundary tests. Android SQLite remains covered by the verified-store tests. */
public final class ActiveSnapshotManagerSmokeTest {
    private static final String ACTIVE = "ms2011-20260718T120000Z-0123456789ab";
    private static final String PENDING = "ms2011-20260718T120001Z-abcdefabcdef";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        assertRecoveredZeroProductSnapshotRetainsIdentity();
        assertEmptyAndNonEmptyBoundaries();
        assertUnchangedNotificationRetainsEmptyCart();
        assertCompletionAndCancellationBoundaryOrder();
        assertFailuresRetainCurrentObjects();
        assertSingleProcessSwitch();
        assertAppServicesBoundaryWiring();
        System.out.println("Active snapshot manager smoke test passed: " + assertions + " assertions");
    }

    private static void assertRecoveredZeroProductSnapshotRetainsIdentity() {
        Fixture fixture = new Fixture(ACTIVE, null, Collections.<Product>emptyList());
        Cart cart = fixture.manager.startCartForRecoveredActiveOrLocal();
        assertEquals("zero-product active snapshot keeps exact id", ACTIVE, cart.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("zero-product active uses no promotion version", PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, cart.pricingSnapshotRef().promotionRuleVersion());
        assertEquals("zero-product active cart remains empty", 0, cart.pricingSnapshotRef().products().size());
    }

    private static void assertEmptyAndNonEmptyBoundaries() throws Exception {
        Product oldProduct = sync(7, "old", ACTIVE);
        Product newProduct = sync(8, "new", PENDING);
        Fixture fixture = new Fixture(ACTIVE, PENDING, Arrays.asList(newProduct));
        fixture.repository.replaceAll(Arrays.asList(oldProduct));
        Cart oldCart = fixture.checkout.startCart(PricingSnapshotRef.capture(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, fixture.repository));
        fixture.checkout.addProductByBarcode(oldCart, "old", com.espsa.mobilepos.core.model.Quantity.one());
        Cart nonEmpty = fixture.manager.activatePendingForEmptyCart(oldCart);
        assertSame("nonempty cart is retained", oldCart, nonEmpty);
        assertEquals("nonempty cart does not activate pending", 0, fixture.gateway.activations);
        assertEquals("nonempty cart retains old lookup", ACTIVE, oldCart.pricingSnapshotRef().pricingSnapshotId());

        Cart empty = fixture.checkout.startCart(PricingSnapshotRef.capture(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, fixture.repository));
        Cart switched = fixture.manager.activatePendingForEmptyCart(empty);
        assertEquals("empty cart activates pending", PENDING, switched.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("pending activated once", 1, fixture.gateway.activations);
        assertTrue("new catalog is applied", fixture.repository.findById("ms2011:8").isPresent());
        assertTrue("old cart reference survives catalog replacement", oldCart.pricingSnapshotRef().findByBarcode("old").isPresent());
    }

    private static void assertCompletionAndCancellationBoundaryOrder() {
        Fixture completed = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(8, "new", PENDING)));
        Cart completedCart = completed.checkout.startCart(PricingSnapshotRef.of(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, Collections.singletonList(sync(7, "old", ACTIVE))));
        Cart afterCompletion = completed.manager.onOrderFinishedOrCancelled(completedCart);
        assertEquals("completion activates before replacement cart", PENDING, afterCompletion.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("completion activates pending once", 1, completed.gateway.activations);

        Fixture cancelled = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(9, "cancel", PENDING)));
        Cart cancelledCart = cancelled.checkout.startCart();
        Cart afterCancellation = cancelled.manager.onOrderFinishedOrCancelled(cancelledCart);
        assertEquals("cancellation activates before replacement cart", PENDING, afterCancellation.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("cancellation activates pending once", 1, cancelled.gateway.activations);
    }

    private static void assertUnchangedNotificationRetainsEmptyCart() {
        Fixture fixture = new Fixture(ACTIVE, null, Collections.<Product>emptyList());
        Cart current = fixture.checkout.startCart(PricingSnapshotRef.of(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, Collections.<Product>emptyList()));
        Cart returned = fixture.manager.activatePendingForEmptyCart(current);
        assertSame("unchanged notification retains empty cart", current, returned);
        assertEquals("unchanged notification performs no immutable read", 0, fixture.gateway.reads);
        assertEquals("unchanged notification performs no catalog apply", 0, fixture.catalogGateway.applies);
        assertEquals("unchanged notification performs no activation", 0, fixture.gateway.activations);
    }

    private static void assertFailuresRetainCurrentObjects() {
        Product oldProduct = sync(7, "old", ACTIVE);
        Fixture readFailure = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(8, "new", PENDING)));
        readFailure.repository.replaceAll(Collections.singletonList(oldProduct));
        readFailure.gateway.failRead = true;
        Cart oldCart = readFailure.checkout.startCart(PricingSnapshotRef.capture(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, readFailure.repository));
        Cart returned = readFailure.manager.activatePendingForEmptyCart(oldCart);
        assertSame("candidate read failure retains cart", oldCart, returned);
        assertEquals("candidate read failure retains active", ACTIVE, readFailure.gateway.active);
        assertTrue("candidate read failure retains catalog", readFailure.repository.findById("ms2011:7").isPresent());

        Fixture stateFailure = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(8, "new", PENDING)));
        stateFailure.repository.replaceAll(Collections.singletonList(oldProduct));
        stateFailure.gateway.failActivation = true;
        Cart current = stateFailure.checkout.startCart(PricingSnapshotRef.capture(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, stateFailure.repository));
        Cart stateReturned = stateFailure.manager.activatePendingForEmptyCart(current);
        assertSame("state write failure retains cart", current, stateReturned);
        assertEquals("state write failure retains active", ACTIVE, stateFailure.gateway.active);
        assertTrue("state write failure retains catalog", stateFailure.repository.findById("ms2011:7").isPresent());
        assertEquals("state write failure restores applied candidate", 0, stateFailure.repository.findAllByBarcode("new").size());

        Fixture applyFailure = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(8, "new", PENDING)));
        applyFailure.repository.replaceAll(Collections.singletonList(oldProduct));
        applyFailure.catalogGateway.failApply = true;
        Cart applyCurrent = applyFailure.checkout.startCart(PricingSnapshotRef.capture(ACTIVE, PricingSnapshotRef.NO_PROMOTION_RULE_VERSION, applyFailure.repository));
        Cart applyReturned = applyFailure.manager.activatePendingForEmptyCart(applyCurrent);
        assertSame("catalog apply failure retains cart", applyCurrent, applyReturned);
        assertEquals("catalog apply failure retains active", ACTIVE, applyFailure.gateway.active);
        assertTrue("catalog apply failure retains catalog", applyFailure.repository.findById("ms2011:7").isPresent());
    }

    private static void assertSingleProcessSwitch() throws Exception {
        Fixture fixture = new Fixture(ACTIVE, PENDING, Collections.singletonList(sync(8, "new", PENDING)));
        fixture.gateway.blockActivation = true;
        Cart empty = fixture.checkout.startCart();
        AtomicReference<Cart> first = new AtomicReference<Cart>();
        AtomicReference<Cart> second = new AtomicReference<Cart>();
        Thread one = new Thread(() -> first.set(fixture.manager.activatePendingForEmptyCart(empty)));
        Thread two = new Thread(() -> second.set(fixture.manager.activatePendingForEmptyCart(empty)));
        one.start();
        assertTrue("first switch entered", fixture.gateway.activationEntered.await(1, TimeUnit.SECONDS));
        two.start();
        fixture.gateway.releaseActivation.countDown();
        one.join(1000); two.join(1000);
        assertFalse("first switch completes", one.isAlive());
        assertFalse("second switch completes", two.isAlive());
        assertEquals("single process only activates once", 1, fixture.gateway.activations);
        assertEquals("first result uses pending", PENDING, first.get().pricingSnapshotRef().pricingSnapshotId());
        assertEquals("second result sees active snapshot", PENDING, second.get().pricingSnapshotRef().pricingSnapshotId());
    }

    private static void assertAppServicesBoundaryWiring() throws Exception {
        String source = new String(Files.readAllBytes(new File("app/src/main/java/com/espsa/mobilepos/app/AppServices.java").toPath()), StandardCharsets.UTF_8);
        assertTrue("application coordinator listener is registered", source.contains("computerSyncCoordinator.addListener(this::onComputerSyncState)"));
        assertTrue("listener enables only empty cart boundary", source.contains("activeSnapshotManager.activatePendingForEmptyCart(currentCart)"));
        assertTrue("current cart getter has no activation I/O", !source.contains("public synchronized Cart currentCart() {\n        if"));
        assertTrue("current cart getter returns the existing reference", source.contains("public synchronized Cart currentCart() {\n        return currentCart;"));
        assertTrue("completion and cancellation share backend boundary", source.contains("onOrderFinishedOrCancelled()"));
    }

    private static Product sync(long gid, String barcode, String snapshotId) {
        String key = "ms2011:" + gid;
        return new Product(key, barcode, "Product " + gid, "", "un", Money.of("2099.99"), null, 0, false,
                ProductOrigin.MS2011_SYNC, key, snapshotId, false);
    }

    private static final class Fixture {
        final InMemoryProductRepository repository = new InMemoryProductRepository();
        final CheckoutService checkout = new CheckoutService(repository, new DefaultPriceCalculator(), new InMemorySaleRepository());
        final ProductCatalogService catalog = new ProductCatalogService(repository);
        final Gateway gateway;
        final CatalogGateway catalogGateway;
        final ActiveSnapshotManager manager;
        Fixture(String active, String pending, List<Product> pendingProducts) {
            gateway = new Gateway(active, pending, pendingProducts);
            catalogGateway = new CatalogGateway(catalog);
            gateway.catalogGateway = catalogGateway;
            manager = new ActiveSnapshotManager(gateway, catalogGateway, repository, checkout);
        }
    }

    private static final class Gateway implements ActiveSnapshotManager.SnapshotGateway {
        String active; String pending; final List<Product> pendingProducts; int activations; int reads; boolean failRead; boolean failActivation; boolean blockActivation; CatalogGateway catalogGateway;
        final CountDownLatch activationEntered = new CountDownLatch(1); final CountDownLatch releaseActivation = new CountDownLatch(1);
        Gateway(String active, String pending, List<Product> pendingProducts) { this.active=active; this.pending=pending; this.pendingProducts=pendingProducts; }
        @Override public ActiveSnapshotManager.SnapshotIds snapshotIds() { return ActiveSnapshotManager.SnapshotIds.readable(active, pending); }
        @Override public String recoveredActiveSnapshotId() { return active; }
        @Override public List<Product> readVerified(String snapshotId) throws Exception { reads++; if (failRead) throw new java.io.IOException("fixture read"); if (PENDING.equals(snapshotId)) return pendingProducts; return Collections.<Product>emptyList(); }
        @Override public void activatePendingVerified(String snapshotId) throws Exception { if (failActivation) throw new java.io.IOException("fixture state write"); if (!snapshotId.equals(pending)) throw new java.io.IOException("wrong pending"); if (catalogGateway == null || catalogGateway.applies == 0) throw new java.io.IOException("catalog must be ready before durable activation"); activationEntered.countDown(); if (blockActivation && !releaseActivation.await(1, TimeUnit.SECONDS)) throw new java.io.IOException("fixture timeout"); activations++; active=pending; pending=null; }
    }

    private static final class CatalogGateway implements ActiveSnapshotManager.CatalogGateway {
        final ProductCatalogService catalog; boolean failApply; int applies;
        CatalogGateway(ProductCatalogService catalog) { this.catalog = catalog; }
        @Override public com.espsa.mobilepos.core.catalog.ProductCatalogService.CatalogRollback apply(com.espsa.mobilepos.core.catalog.ProductCatalogCandidate candidate) { if (failApply) throw new IllegalStateException("fixture catalog apply"); applies++; return catalog.applyVerifiedCandidate(candidate); }
        @Override public void restore(com.espsa.mobilepos.core.catalog.ProductCatalogService.CatalogRollback rollback) { catalog.restore(rollback); }
    }

    private interface Checked { void run() throws Exception; }
    private static void assertSame(String name, Object expected, Object actual) { assertions++; if (expected != actual) throw new AssertionError(name); }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertFalse(String name, boolean value) { assertTrue(name, !value); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + " expected=" + expected + " actual=" + actual); }
}
