package com.github.penfeizhou.animation.demo;

import android.app.Application;

import com.moorgen.sdk.common.logger.LogManager;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.getInstance().enableDebug(BuildConfig.DEBUG);
    }
}
