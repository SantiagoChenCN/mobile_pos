package com.espsa.mobilepos.ui;

public final class UiText {
    private UiText() {
    }

    public static String choose(AppLanguage language, String zh, String es) {
        return language == AppLanguage.ES ? es : zh;
    }
}

