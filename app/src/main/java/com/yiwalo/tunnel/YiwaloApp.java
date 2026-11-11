package com.yiwalo.tunnel;

import android.app.Application;

/** Charge la langue choisie dès le démarrage du processus (service VPN compris). */
public class YiwaloApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        T.init(this);
    }
}
