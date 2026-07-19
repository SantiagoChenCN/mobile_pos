package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.model.V2Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fully validated in-memory merge; the repository is untouched until this object exists. */
public final class ProductCatalogCandidate {
    private final List<Product> products;
    private final List<ProductBarcodeConflict> barcodeConflicts;

    private ProductCatalogCandidate(List<Product> products, List<ProductBarcodeConflict> barcodeConflicts) {
        this.products = Collections.unmodifiableList(new ArrayList<Product>(products));
        this.barcodeConflicts = Collections.unmodifiableList(new ArrayList<ProductBarcodeConflict>(barcodeConflicts));
    }

    public static ProductCatalogCandidate merge(List<Product> existing, List<Product> syncProducts) {
        if (existing == null || syncProducts == null) throw new IllegalArgumentException("Catalog inputs are required");
        Map<String, Product> syncByKey = new HashMap<String, Product>();
        Map<String, Product> syncByBarcode = new HashMap<String, Product>();
        Set<String> syncGids = new HashSet<String>();
        for (Product product : syncProducts) {
            validateSyncProduct(product);
            if (syncByKey.put(product.sourceProductKey(), product) != null) throw new IllegalArgumentException("Duplicate sync sourceProductKey");
            if (!product.barcode().isEmpty() && syncByBarcode.put(product.barcode(), product) != null) {
                throw new IllegalArgumentException("Duplicate synchronized barcode");
            }
            syncGids.add(product.sourceProductKey().substring("ms2011:".length()));
        }

        List<Product> merged = new ArrayList<Product>();
        Set<String> ids = new HashSet<String>();
        for (Product product : existing) {
            if (product == null) throw new IllegalArgumentException("Existing catalog contains null product");
            if (product.origin() == ProductOrigin.MS2011_SYNC) continue;
            if (product.origin() == ProductOrigin.LEGACY_IMPORT && syncGids.contains(product.id())) continue;
            addUnique(merged, ids, product);
        }
        for (Product product : syncProducts) addUnique(merged, ids, product);
        return new ProductCatalogCandidate(merged, findLocalConflicts(merged));
    }

    public List<Product> products() { return products; }
    public List<ProductBarcodeConflict> barcodeConflicts() { return barcodeConflicts; }

    /**
     * products.json is a local/legacy store, never a cache of an immutable v2 object.  The
     * result is built before the persistence port is invoked and rejects damaged input rather
     * than silently retaining an unknown element.
     */
    public static List<Product> localPersistenceProducts(List<Product> products) {
        if (products == null) throw new IllegalArgumentException("Catalog products are required");
        List<Product> localProducts = new ArrayList<Product>();
        for (Product product : products) {
            if (product == null) throw new IllegalArgumentException("Catalog contains null product");
            if (product.origin() == ProductOrigin.LOCAL || product.origin() == ProductOrigin.LEGACY_IMPORT) {
                localProducts.add(product);
            }
        }
        return Collections.unmodifiableList(localProducts);
    }

    private static void addUnique(List<Product> products, Set<String> ids, Product product) {
        if (!ids.add(product.id())) throw new IllegalArgumentException("Duplicate product id in candidate: " + product.id());
        products.add(product);
    }

    private static void validateSyncProduct(Product product) {
        if (product == null || product.origin() != ProductOrigin.MS2011_SYNC
                || product.sourceSnapshotId().isEmpty() || product.sourceProductKey().isEmpty()
                || !product.id().equals(product.sourceProductKey()) || product.promotionPrice() != null
                || product.promotionMinQuantity() != 0) {
            throw new IllegalArgumentException("Invalid synchronized product candidate");
        }
        try {
            V2Contract.validateSnapshotId(product.sourceSnapshotId());
            String gidText = product.sourceProductKey().substring("ms2011:".length());
            long gid = Long.parseLong(gidText);
            if (!V2Contract.sourceProductKey(gid).equals(product.sourceProductKey())) {
                throw new IllegalArgumentException("Non-canonical synchronized product key");
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid synchronized product key", error);
        }
    }

    private static List<ProductBarcodeConflict> findLocalConflicts(List<Product> products) {
        Map<String, Product> syncByBarcode = new HashMap<String, Product>();
        Map<String, List<Product>> localsByBarcode = new HashMap<String, List<Product>>();
        for (Product product : products) {
            if (product.barcode().isEmpty()) continue;
            if (product.origin() == ProductOrigin.MS2011_SYNC) {
                syncByBarcode.put(product.barcode(), product);
            } else if (product.origin() == ProductOrigin.LOCAL) {
                List<Product> locals = localsByBarcode.get(product.barcode());
                if (locals == null) { locals = new ArrayList<Product>(); localsByBarcode.put(product.barcode(), locals); }
                locals.add(product);
            }
        }
        List<ProductBarcodeConflict> conflicts = new ArrayList<ProductBarcodeConflict>();
        for (Map.Entry<String, List<Product>> entry : localsByBarcode.entrySet()) {
            Product sync = syncByBarcode.get(entry.getKey());
            if (sync != null) conflicts.add(new ProductBarcodeConflict(entry.getKey(), sync, entry.getValue()));
        }
        return conflicts;
    }
}
