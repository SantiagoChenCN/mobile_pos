package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

/** Explicit barcode result; checkout and UI integration remain deferred to S10. */
public final class ProductBarcodeLookup {
    public enum Status { FOUND, STOPPED, NOT_FOUND }

    private final Status status;
    private final Product product;

    private ProductBarcodeLookup(Status status, Product product) {
        this.status = status;
        this.product = product;
    }

    public static ProductBarcodeLookup notFound() { return new ProductBarcodeLookup(Status.NOT_FOUND, null); }
    public static ProductBarcodeLookup found(Product product) { return new ProductBarcodeLookup(Status.FOUND, product); }
    public static ProductBarcodeLookup stopped(Product product) { return new ProductBarcodeLookup(Status.STOPPED, product); }
    public Status status() { return status; }
    public Product product() { return product; }
}
