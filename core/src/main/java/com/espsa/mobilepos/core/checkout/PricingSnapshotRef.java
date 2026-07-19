package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.V2Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Immutable caller-provided pricing identity and complete product lookup for one cart. */
public final class PricingSnapshotRef {
    public static final String LOCAL_LIBRARY_SNAPSHOT_ID = "local-library";
    public static final String NO_PROMOTION_RULE_VERSION = "none";

    private final String pricingSnapshotId;
    private final String promotionRuleVersion;
    private final List<Product> products;
    private final Map<String, Product> productsById;
    private final Map<String, Product> productsByBarcode;
    private final Set<String> unavailableBarcodes;

    private PricingSnapshotRef(
            String pricingSnapshotId,
            String promotionRuleVersion,
            List<Product> products,
            Map<String, Product> productsById,
            Map<String, Product> productsByBarcode,
            Set<String> unavailableBarcodes
    ) {
        this.pricingSnapshotId = pricingSnapshotId;
        this.promotionRuleVersion = promotionRuleVersion;
        this.products = Collections.unmodifiableList(new ArrayList<Product>(products));
        this.productsById = Collections.unmodifiableMap(new LinkedHashMap<String, Product>(productsById));
        this.productsByBarcode = Collections.unmodifiableMap(new LinkedHashMap<String, Product>(productsByBarcode));
        this.unavailableBarcodes = Collections.unmodifiableSet(new HashSet<String>(unavailableBarcodes));
    }

    /**
     * Creates an explicit immutable lookup. Callers that know the active v2 snapshot must supply
     * its exact snapshotId, including when its product list is empty.
     */
    public static PricingSnapshotRef of(
            String pricingSnapshotId,
            String promotionRuleVersion,
            List<Product> catalogProducts
    ) {
        SnapshotProducts snapshot = validateProducts(catalogProducts);
        Map<String, Product> productsByBarcode = new LinkedHashMap<String, Product>();
        for (Product product : snapshot.products) {
            if (!product.barcode().isEmpty() && productsByBarcode.put(product.barcode(), product) != null) {
                throw new IllegalArgumentException("Duplicate non-empty barcode in pricing snapshot: " + product.barcode());
            }
        }
        return create(pricingSnapshotId, promotionRuleVersion, snapshot, productsByBarcode,
                Collections.<String>emptySet());
    }

    /**
     * Captures both the complete product objects and the repository's established barcode winner
     * at one order boundary. No live repository is retained after this method returns.
     */
    public static PricingSnapshotRef capture(
            String pricingSnapshotId,
            String promotionRuleVersion,
            ProductRepository repository
    ) {
        Objects.requireNonNull(repository, "repository");
        SnapshotProducts snapshot = validateProducts(repository.all());
        Set<String> barcodes = new HashSet<String>();
        for (Product product : snapshot.products) {
            if (!product.barcode().isEmpty()) {
                barcodes.add(product.barcode());
            }
        }
        Map<String, Product> productsByBarcode = new LinkedHashMap<String, Product>();
        Set<String> unavailableBarcodes = new HashSet<String>();
        for (String barcode : barcodes) {
            Optional<Product> winner = Objects.requireNonNull(repository.findByBarcode(barcode), "repository barcode lookup");
            if (!winner.isPresent()) {
                unavailableBarcodes.add(barcode);
            } else {
                Product resolved = winner.get();
                if (snapshot.productsById.get(resolved.id()) != resolved || !barcode.equals(resolved.barcode())) {
                    throw new IllegalArgumentException("Repository barcode lookup returned product outside captured catalog");
                }
                productsByBarcode.put(barcode, resolved);
            }
        }
        return create(pricingSnapshotId, promotionRuleVersion, snapshot, productsByBarcode, unavailableBarcodes);
    }

    /** Compatibility snapshot for legacy callers that only have the local repository. */
    public static PricingSnapshotRef localLibrary(ProductRepository repository) {
        return capture(LOCAL_LIBRARY_SNAPSHOT_ID, NO_PROMOTION_RULE_VERSION, repository);
    }

    /** Compatibility helper for direct legacy Cart construction without a repository lookup. */
    public static PricingSnapshotRef localLibrary(List<Product> catalogProducts) {
        return of(LOCAL_LIBRARY_SNAPSHOT_ID, NO_PROMOTION_RULE_VERSION, catalogProducts);
    }

    public String pricingSnapshotId() {
        return pricingSnapshotId;
    }

    public String promotionRuleVersion() {
        return promotionRuleVersion;
    }

    public List<Product> products() {
        return products;
    }

    public Optional<Product> findById(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(productsById.get(productId.trim()));
    }

    public Optional<Product> findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalizedBarcode = barcode.trim();
        if (unavailableBarcodes.contains(normalizedBarcode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(productsByBarcode.get(normalizedBarcode));
    }

    private static PricingSnapshotRef create(
            String pricingSnapshotId,
            String promotionRuleVersion,
            SnapshotProducts snapshot,
            Map<String, Product> productsByBarcode,
            Set<String> unavailableBarcodes
    ) {
        String snapshotId = requireText(pricingSnapshotId, "pricingSnapshotId");
        if (!LOCAL_LIBRARY_SNAPSHOT_ID.equals(snapshotId)) {
            V2Contract.validateSnapshotId(snapshotId);
        }
        String ruleVersion = requireText(promotionRuleVersion, "promotionRuleVersion");
        return new PricingSnapshotRef(snapshotId, ruleVersion, snapshot.products, snapshot.productsById,
                productsByBarcode, unavailableBarcodes);
    }

    private static SnapshotProducts validateProducts(List<Product> catalogProducts) {
        if (catalogProducts == null) {
            throw new IllegalArgumentException("Catalog products are required");
        }
        List<Product> products = new ArrayList<Product>(catalogProducts.size());
        Map<String, Product> productsById = new LinkedHashMap<String, Product>();
        for (Product product : catalogProducts) {
            Product current = Objects.requireNonNull(product, "Catalog contains null product");
            if (productsById.put(current.id(), current) != null) {
                throw new IllegalArgumentException("Duplicate product id in pricing snapshot: " + current.id());
            }
            products.add(current);
        }
        return new SnapshotProducts(products, productsById);
    }

    private static final class SnapshotProducts {
        private final List<Product> products;
        private final Map<String, Product> productsById;

        private SnapshotProducts(List<Product> products, Map<String, Product> productsById) {
            this.products = products;
            this.productsById = productsById;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
