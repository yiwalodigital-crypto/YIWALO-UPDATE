package com.yiwalo.tunnel.ip;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
public final class AvionIp {
    private AvionIp() { }
    public static boolean automatiquePossible(Context c) {
        return AssistantAvion.actif(c) || ModeAvionAccessibilite.pret();
    }
    /** Coupe puis remet le reseau (mode avion). Retourne false si aucune methode n'est activee. */
    public static boolean renouveler(Context c, Runnable fin) {
        Context app = c.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        if (AssistantAvion.actif(app) && AssistantAvion.basculer(true)) {
            h.postDelayed(() -> AssistantAvion.basculer(false), 3000);
        } else if (ModeAvionAccessibilite.basculer(app)) {
            h.postDelayed(() -> ModeAvionAccessibilite.basculer(app), 4500);
        } else {
            return false;
        }
        if (fin != null) h.postDelayed(fin, 12000);
        return true;
    }
}
