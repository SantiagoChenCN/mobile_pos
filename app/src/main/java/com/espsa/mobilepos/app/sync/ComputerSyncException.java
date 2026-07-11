package com.espsa.mobilepos.app.sync;

public final class ComputerSyncException extends Exception {
    private final ComputerSyncFailureReason reason;

    public ComputerSyncException(String message) {
        this(ComputerSyncFailureReason.UNKNOWN, message, null);
    }

    public ComputerSyncException(String message, Throwable cause) {
        this(ComputerSyncFailureReason.UNKNOWN, message, cause);
    }

    public ComputerSyncException(ComputerSyncFailureReason reason, String message) {
        this(reason, message, null);
    }

    public ComputerSyncException(
            ComputerSyncFailureReason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason == null ? ComputerSyncFailureReason.UNKNOWN : reason;
    }

    public ComputerSyncFailureReason reason() {
        return reason;
    }
}
