package com.espsa.mobilepos.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.espsa.mobilepos.ui.AppLanguage;

public final class UserPreferencesStore {
    private static final String PREFERENCES_NAME = "mobile_pos_preferences";
    private static final String LANGUAGE_KEY = "language";

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

    private SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
