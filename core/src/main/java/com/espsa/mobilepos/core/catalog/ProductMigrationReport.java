package com.espsa.mobilepos.core.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistable, fail-closed record of legacy provenance decisions still requiring a human. */
public final class ProductMigrationReport {
    private final List<ProductMigrationIssue> issues;

    public ProductMigrationReport(List<ProductMigrationIssue> issues) {
        List<ProductMigrationIssue> copy = new ArrayList<ProductMigrationIssue>(
                issues == null ? Collections.<ProductMigrationIssue>emptyList() : issues
        );
        Set<String> productIds = new HashSet<String>();
        for (ProductMigrationIssue issue : copy) {
            if (issue == null || !productIds.add(issue.productId())) {
                throw new IllegalArgumentException("Migration report requires unique non-null product issues");
            }
        }
        this.issues = Collections.unmodifiableList(copy);
    }

    public static ProductMigrationReport empty() { return new ProductMigrationReport(Collections.<ProductMigrationIssue>emptyList()); }

    public List<ProductMigrationIssue> issues() { return issues; }

    public boolean isEmpty() { return issues.isEmpty(); }
}
