package com.espsa.mobilepos.core.editing;

public final class ProductChange {
    private final String fieldLabelZh;
    private final String fieldLabelEs;
    private final String oldValue;
    private final String newValue;

    public ProductChange(String fieldLabelZh, String fieldLabelEs, String oldValue, String newValue) {
        this.fieldLabelZh = fieldLabelZh == null ? "" : fieldLabelZh;
        this.fieldLabelEs = fieldLabelEs == null ? "" : fieldLabelEs;
        this.oldValue = oldValue == null ? "" : oldValue;
        this.newValue = newValue == null ? "" : newValue;
    }

    public String fieldLabelZh() {
        return fieldLabelZh;
    }

    public String fieldLabelEs() {
        return fieldLabelEs;
    }

    public String oldValue() {
        return oldValue;
    }

    public String newValue() {
        return newValue;
    }
}
