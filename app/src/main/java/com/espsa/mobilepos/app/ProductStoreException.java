package com.espsa.mobilepos.app;

public final class ProductStoreException extends Exception {
    public ProductStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProductStoreException(String message) {
        super(message);
    }
}
