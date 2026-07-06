package com.espsa.mobilepos.core.exporter;

public final class SalesExportException extends Exception {
    public SalesExportException(String message) {
        super(message);
    }

    public SalesExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
