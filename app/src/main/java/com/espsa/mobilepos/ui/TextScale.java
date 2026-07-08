package com.espsa.mobilepos.ui;

public enum TextScale {
    SMALL(0.90f, "小", "Pequeno"),
    NORMAL(1.00f, "标准", "Normal"),
    LARGE(1.15f, "大", "Grande"),
    EXTRA_LARGE(1.30f, "特大", "Extra grande");

    private final float multiplier;
    private final String zh;
    private final String es;

    TextScale(float multiplier, String zh, String es) {
        this.multiplier = multiplier;
        this.zh = zh;
        this.es = es;
    }

    public float multiplier() {
        return multiplier;
    }

    public String label(AppLanguage language) {
        return language == AppLanguage.ZH ? zh : es;
    }
}
