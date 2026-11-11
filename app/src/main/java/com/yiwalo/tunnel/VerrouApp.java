package com.yiwalo.tunnel;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** État du verrouillage de l'application (écran de verrouillage du téléphone). */
final class VerrouApp {
    private static final String PREFS = "yiwalo_securite";
    private static final String CLE = "verrou";
    private static final long DELAI_MS = 30000L;

    private static boolean deverrouille = false;
    private static boolean enCours = false;
    private static long sortie = 0;

    private VerrouApp() { }

    static boolean actif(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CLE, false);
    }

    static void definir(Context c, boolean v) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CLE, v).apply();
        if (v) {
            deverrouille = true;
            sortie = SystemClock.elapsedRealtime();
        }
    }

    static boolean ecranSecurise(Context c) {
        try {
            KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isDeviceSecure();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Appelé quand l'écran principal devient visible. */
    static void verifier(Activity a) {
        if (!actif(a) || enCours) return;
        if (deverrouille && SystemClock.elapsedRealtime() - sortie <= DELAI_MS) return;
        deverrouille = false;
        enCours = true;
        a.startActivity(new Intent(a, VerrouActivity.class));
    }

    static void noterSortie() {
        if (!enCours) sortie = SystemClock.elapsedRealtime();
    }

    static void affichage() {
        enCours = true;
    }

    static void succes() {
        deverrouille = true;
        enCours = false;
        sortie = SystemClock.elapsedRealtime();
    }

    static void abandon() {
        if (!deverrouille) enCours = false;
    }

    static void desactiver(Context c) {
        definir(c, false);
        deverrouille = true;
        enCours = false;
    }
}
