package com.espsa.mobilepos;

import android.app.Application;

import com.espsa.mobilepos.app.AppServices;

public final class MobilePosApplication extends Application {
    private AppServices services;

    @Override
    public void onCreate() {
        super.onCreate();
        services = AppServices.create(this);
    }

    public AppServices services() {
        return services;
    }
}
