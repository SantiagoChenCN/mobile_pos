package com.espsa.mobilepos.core.importer;

import java.util.Locale;

public enum ImportFormat {
    MINGSHENG_DB("mingsheng-db", new String[]{".db"}),
    GENERIC_CSV("generic-csv", new String[]{".csv"});

    private final String id;
    private final String[] extensions;

    ImportFormat(String id, String[] extensions) {
        this.id = id;
        this.extensions = extensions;
    }

    public String id() {
        return id;
    }

    public String[] extensions() {
        return extensions.clone();
    }

    public boolean acceptsFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
