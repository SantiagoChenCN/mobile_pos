package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.Collections;

public final class ProductCatalogService {
    private final ProductRepository productRepository;
    private List<ProductBarcodeConflict> barcodeConflicts = Collections.emptyList();

    public ProductCatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<Product> findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    public ProductBarcodeLookup lookupBarcode(String barcode) {
        Optional<Product> product = findByBarcodeIncludingStopped(barcode);
        if (!product.isPresent()) return ProductBarcodeLookup.notFound();
        return product.get().stopped() ? ProductBarcodeLookup.stopped(product.get()) : ProductBarcodeLookup.found(product.get());
    }

    public List<Product> searchByName(String query, int limit) {
        return productRepository.searchByName(query, limit);
    }

    public List<Product> searchByName(String query) {
        return productRepository.searchByName(query, Integer.MAX_VALUE);
    }

    public void applyImport(ProductImportResult importResult) {
        productRepository.replaceAll(importResult.products());
        barcodeConflicts = Collections.emptyList();
    }

    /** Validation completes before the single replaceAll call. */
    public ProductCatalogCandidate replaceWithV2SyncProducts(List<Product> syncProducts) {
        ProductCatalogCandidate candidate = ProductCatalogCandidate.merge(productRepository.all(), syncProducts);
        applyVerifiedCandidate(candidate);
        return candidate;
    }

    /** Applies the already complete immutable candidate without re-running merge validation. */
    public CatalogRollback applyVerifiedCandidate(ProductCatalogCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Verified product catalog candidate is required");
        }
        CatalogRollback rollback = new CatalogRollback(productRepository.all(), barcodeConflicts);
        try {
            productRepository.replaceAll(candidate.products());
            barcodeConflicts = candidate.barcodeConflicts();
            return rollback;
        } catch (RuntimeException failure) {
            try {
                productRepository.replaceAll(rollback.products);
                barcodeConflicts = rollback.barcodeConflicts;
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
                throw new IllegalStateException("Catalog apply and rollback both failed", failure);
            }
            throw failure;
        }
    }

    /** Restores only a rollback token created immediately before a candidate application. */
    public void restore(CatalogRollback rollback) {
        if (rollback == null) {
            throw new IllegalArgumentException("Catalog rollback token is required");
        }
        productRepository.replaceAll(rollback.products);
        barcodeConflicts = rollback.barcodeConflicts;
    }

    public List<ProductBarcodeConflict> barcodeConflicts() { return barcodeConflicts; }

    private Optional<Product> findByBarcodeIncludingStopped(String barcode) {
        for (Product product : productRepository.findAllByBarcode(barcode)) {
            if (product.origin() == com.espsa.mobilepos.core.model.ProductOrigin.MS2011_SYNC) {
                return Optional.of(product);
            }
        }
        return productRepository.findByBarcode(barcode);
    }

    public int productCount() {
        return productRepository.count();
    }

    public static final class CatalogRollback {
        private final List<Product> products;
        private final List<ProductBarcodeConflict> barcodeConflicts;

        private CatalogRollback(List<Product> products, List<ProductBarcodeConflict> barcodeConflicts) {
            this.products = Collections.unmodifiableList(new java.util.ArrayList<Product>(products));
            this.barcodeConflicts = Collections.unmodifiableList(new java.util.ArrayList<ProductBarcodeConflict>(barcodeConflicts));
        }
    }
}
