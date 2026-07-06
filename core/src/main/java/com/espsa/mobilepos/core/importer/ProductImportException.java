package com.espsa.mobilepos.core.importer;

public final class ProductImportException extends Exception {
    public ProductImportException(String message) {
        super(message);
    }

    public ProductImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

