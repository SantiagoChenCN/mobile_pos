package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.catalog.ProductCatalogCandidate;
import com.espsa.mobilepos.core.catalog.ProductCatalogService;
import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.checkout.PricingSnapshotRef;
import com.espsa.mobilepos.core.model.Product;

import java.util.List;

/** Switches immutable v2 candidates only at the approved cart/order boundaries. */
public final class ActiveSnapshotManager {
    interface SnapshotGateway {
        SnapshotIds snapshotIds();
        String recoveredActiveSnapshotId();
        List<Product> readVerified(String snapshotId) throws Exception;
        void activatePendingVerified(String snapshotId) throws Exception;
    }

    interface CatalogGateway {
        ProductCatalogService.CatalogRollback apply(ProductCatalogCandidate candidate);
        void restore(ProductCatalogService.CatalogRollback rollback);
    }

    static final class SnapshotIds {
        private final boolean readable;
        private final String activeSnapshotId;
        private final String pendingSnapshotId;

        private SnapshotIds(boolean readable, String activeSnapshotId, String pendingSnapshotId) {
            this.readable = readable;
            this.activeSnapshotId = activeSnapshotId;
            this.pendingSnapshotId = pendingSnapshotId;
        }

        static SnapshotIds readable(String activeSnapshotId, String pendingSnapshotId) {
            return new SnapshotIds(true, activeSnapshotId, pendingSnapshotId);
        }

        static SnapshotIds unreadable() {
            return new SnapshotIds(false, null, null);
        }
    }

    private final SnapshotGateway gateway;
    private final CatalogGateway catalog;
    private final ProductRepository repository;
    private final CheckoutService checkout;

    public ActiveSnapshotManager(
            V2SnapshotStore store,
            V2ProductSnapshotReader reader,
            ProductCatalogService catalog,
            ProductRepository repository,
            CheckoutService checkout
    ) {
        this(new ProductionGateway(store, reader), new ProductionCatalogGateway(catalog), repository, checkout);
    }

    ActiveSnapshotManager(
            SnapshotGateway gateway,
            ProductCatalogService catalog,
            ProductRepository repository,
            CheckoutService checkout
    ) {
        this(gateway, new ProductionCatalogGateway(catalog), repository, checkout);
    }

    ActiveSnapshotManager(
            SnapshotGateway gateway,
            CatalogGateway catalog,
            ProductRepository repository,
            CheckoutService checkout
    ) {
        if (gateway == null || catalog == null || repository == null || checkout == null) {
            throw new IllegalArgumentException("Active snapshot dependencies are required");
        }
        this.gateway = gateway;
        this.catalog = catalog;
        this.repository = repository;
        this.checkout = checkout;
    }

    /** Startup uses the recovered active/last-good identity, including a valid zero-product v2 snapshot. */
    public synchronized Cart startCartForRecoveredActiveOrLocal() {
        String activeSnapshotId = gateway.recoveredActiveSnapshotId();
        if (activeSnapshotId == null) {
            return checkout.startCart();
        }
        ProductCatalogService.CatalogRollback rollback = null;
        try {
            ProductCatalogCandidate candidate = prepareCandidate(activeSnapshotId);
            rollback = catalog.apply(candidate);
            return newCart(activeSnapshotId);
        } catch (Exception ignored) {
            restore(rollback);
            return checkout.startCart();
        }
    }

    /** An in-progress cart is immutable; an empty cart may safely enable a fully prepared pending pair. */
    public synchronized Cart activatePendingForEmptyCart(Cart currentCart) {
        if (currentCart == null || !currentCart.isEmpty()) {
            return currentCart;
        }
        SnapshotIds ids = gateway.snapshotIds();
        if (!ids.readable) {
            return currentCart;
        }
        if (ids.pendingSnapshotId != null) {
            return activatePreparedPendingOrRetain(currentCart, ids.pendingSnapshotId);
        }
        if (matchesCurrentIdentity(currentCart, ids.activeSnapshotId)) {
            return currentCart;
        }
        return startNewCartFromRecoveredActiveOrLocal(currentCart, ids.activeSnapshotId);
    }

    /** Shared completion/cancellation boundary: switch first, then create the next cart. */
    public synchronized Cart onOrderFinishedOrCancelled(Cart currentCart) {
        if (currentCart == null) {
            throw new IllegalArgumentException("Current cart is required");
        }
        SnapshotIds ids = gateway.snapshotIds();
        if (!ids.readable) {
            return currentCart;
        }
        if (ids.pendingSnapshotId != null) {
            return activatePreparedPendingOrRetain(currentCart, ids.pendingSnapshotId);
        }
        return startNewCartFromRecoveredActiveOrLocal(currentCart, ids.activeSnapshotId);
    }

    private Cart activatePreparedPendingOrRetain(Cart currentCart, String pendingSnapshotId) {
        ProductCatalogService.CatalogRollback rollback = null;
        try {
            ProductCatalogCandidate candidate = prepareCandidate(pendingSnapshotId);
            rollback = catalog.apply(candidate);
            Cart nextCart = newCart(pendingSnapshotId);
            // Durable state is the final commit; all catalog/cart work has already succeeded.
            gateway.activatePendingVerified(pendingSnapshotId);
            return nextCart;
        } catch (Exception ignored) {
            restore(rollback);
            return currentCart;
        }
    }

    private Cart startNewCartFromRecoveredActiveOrLocal(Cart currentCart, String activeSnapshotId) {
        if (activeSnapshotId == null) {
            return checkout.startCart();
        }
        String recovered = gateway.recoveredActiveSnapshotId();
        if (recovered == null) {
            return currentCart;
        }
        ProductCatalogService.CatalogRollback rollback = null;
        try {
            ProductCatalogCandidate candidate = prepareCandidate(recovered);
            rollback = catalog.apply(candidate);
            return newCart(recovered);
        } catch (Exception ignored) {
            restore(rollback);
            return currentCart;
        }
    }

    private boolean matchesCurrentIdentity(Cart currentCart, String activeSnapshotId) {
        String cartSnapshotId = currentCart.pricingSnapshotRef().pricingSnapshotId();
        return activeSnapshotId == null
                ? PricingSnapshotRef.LOCAL_LIBRARY_SNAPSHOT_ID.equals(cartSnapshotId)
                : activeSnapshotId.equals(cartSnapshotId);
    }

    private ProductCatalogCandidate prepareCandidate(String snapshotId) throws Exception {
        return ProductCatalogCandidate.merge(repository.all(), gateway.readVerified(snapshotId));
    }

    private Cart newCart(String activeSnapshotId) {
        return checkout.startCart(PricingSnapshotRef.capture(
                activeSnapshotId,
                PricingSnapshotRef.NO_PROMOTION_RULE_VERSION,
                repository
        ));
    }

    private void restore(ProductCatalogService.CatalogRollback rollback) {
        if (rollback != null) {
            catalog.restore(rollback);
        }
    }

    private static final class ProductionGateway implements SnapshotGateway {
        private final V2SnapshotStore store;
        private final V2ProductSnapshotReader reader;

        private ProductionGateway(V2SnapshotStore store, V2ProductSnapshotReader reader) {
            if (store == null || reader == null) {
                throw new IllegalArgumentException("Verified v2 store and reader are required");
            }
            this.store = store;
            this.reader = reader;
        }

        @Override public SnapshotIds snapshotIds() {
            V2SnapshotStateStore.State state = store.readState();
            return state == null ? SnapshotIds.unreadable()
                    : SnapshotIds.readable(state.activeSnapshotId(), state.pendingSnapshotId());
        }

        @Override public String recoveredActiveSnapshotId() {
            V2SnapshotReader.RecoveryResult recovery = new V2SnapshotReader(store).recover();
            return recovery.hasValidSnapshot() ? recovery.activeSnapshotId() : null;
        }

        @Override public List<Product> readVerified(String snapshotId) throws Exception {
            return reader.readVerifiedSnapshot(snapshotId);
        }

        @Override public void activatePendingVerified(String snapshotId) throws Exception {
            store.activatePendingVerified(snapshotId);
        }
    }

    private static final class ProductionCatalogGateway implements CatalogGateway {
        private final ProductCatalogService catalog;
        private ProductionCatalogGateway(ProductCatalogService catalog) {
            if (catalog == null) throw new IllegalArgumentException("Product catalog is required");
            this.catalog = catalog;
        }
        @Override public ProductCatalogService.CatalogRollback apply(ProductCatalogCandidate candidate) {
            return catalog.applyVerifiedCandidate(candidate);
        }
        @Override public void restore(ProductCatalogService.CatalogRollback rollback) { catalog.restore(rollback); }
    }
}
