package com.espsa.mobilepos.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.TextScale;

public final class UserPreferencesStore {
    private static final String PREFERENCES_NAME = "mobile_pos_preferences";
    private static final String LANGUAGE_KEY = "language";
    private static final String TEXT_SCALE_KEY = "textScale";

    public AppLanguage loadLanguage(Context context) {
        String value = preferences(context).getString(LANGUAGE_KEY, AppLanguage.ZH.name());
        try {
            return AppLanguage.valueOf(value);
        } catch (Exception ex) {
            return AppLanguage.ZH;
        }
    }

    public void saveLanguage(Context context, AppLanguage language) {
        preferences(context)
                .edit()
                .putString(LANGUAGE_KEY, (language == null ? AppLanguage.ZH : language).name())
                .apply();
    }

    public TextScale loadTextScale(Context context) {
        String value = preferences(context).getString(TEXT_SCALE_KEY, TextScale.NORMAL.name());
        try {
            return TextScale.valueOf(value);
        } catch (Exception ex) {
            return TextScale.NORMAL;
        }
    }

    public void saveTextScale(Context context, TextScale textScale) {
        preferences(context)
                .edit()
                .putString(TEXT_SCALE_KEY, (textScale == null ? TextScale.NORMAL : textScale).name())
                .apply();
    }

    private SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
