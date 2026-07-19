package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies only explicit, report-backed legacy provenance choices. */
public final class ProductMigrationService {
    public List<Product> applySelections(
            List<Product> products,
            ProductMigrationReport report,
            List<ProductMigrationSelection> selections
    ) {
        if (products == null || report == null || selections == null) {
            throw new IllegalArgumentException("products, report and selections are required");
        }
        Map<String, ProductOrigin> selectedById = selectionsById(report, selections);
        Map<String, Product> productById = new HashMap<String, Product>();
        for (Product product : products) {
            if (product == null || productById.put(product.id(), product) != null) {
                throw new IllegalArgumentException("Products must have unique non-null identities");
            }
        }
        for (String productId : selectedById.keySet()) {
            Product product = productById.get(productId);
            if (product == null || product.origin() != ProductOrigin.LEGACY_IMPORT) {
                throw new IllegalArgumentException("Selection must target an existing LEGACY_IMPORT product");
            }
        }
        List<Product> updated = new ArrayList<Product>();
        for (Product product : products) {
            ProductOrigin selected = selectedById.get(product.id());
            updated.add(selected == null ? product : product.resolveLegacyOrigin(selected));
        }
        return updated;
    }

    public ProductMigrationReport remainingReport(
            ProductMigrationReport report,
            List<ProductMigrationSelection> selections
    ) {
        if (report == null || selections == null) {
            throw new IllegalArgumentException("report and selections are required");
        }
        Set<String> resolved = selectionsById(report, selections).keySet();
        List<ProductMigrationIssue> remaining = new ArrayList<ProductMigrationIssue>();
        for (ProductMigrationIssue issue : report.issues()) {
            if (!resolved.contains(issue.productId())) {
                remaining.add(issue);
            }
        }
        return new ProductMigrationReport(remaining);
    }

    private Map<String, ProductOrigin> selectionsById(
            ProductMigrationReport report,
            List<ProductMigrationSelection> selections
    ) {
        Set<String> reportIds = new HashSet<String>();
        for (ProductMigrationIssue issue : report.issues()) {
            reportIds.add(issue.productId());
        }
        Map<String, ProductOrigin> selectedById = new HashMap<String, ProductOrigin>();
        for (ProductMigrationSelection selection : selections) {
            if (selection == null
                    || !reportIds.contains(selection.productId())
                    || selectedById.put(selection.productId(), selection.selectedOrigin()) != null) {
                throw new IllegalArgumentException("Selection must target one unique reported product");
            }
        }
        return selectedById;
    }
}
