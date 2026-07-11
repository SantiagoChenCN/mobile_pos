package com.espsa.mobilepos.ui.sync;

import com.espsa.mobilepos.app.sync.ComputerSyncFailureReason;

public final class ComputerSyncErrorPresentation {
    private final String title;
    private final String message;
    private final String suggestion;
    private final ComputerSyncFailureReason failureReason;

    public ComputerSyncErrorPresentation(
            String title,
            String message,
            String suggestion,
            ComputerSyncFailureReason failureReason
    ) {
        this.title = clean(title);
        this.message = clean(message);
        this.suggestion = clean(suggestion);
        this.failureReason = failureReason == null ? ComputerSyncFailureReason.UNKNOWN : failureReason;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public String suggestion() {
        return suggestion;
    }

    public ComputerSyncFailureReason failureReason() {
        return failureReason;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
