package com.espsa.mobilepos.core.checkout;

public final class ProductNotFoundException extends Exception {
    public ProductNotFoundException(String barcode) {
        super("Product not found for barcode: " + barcode);
    }
}

