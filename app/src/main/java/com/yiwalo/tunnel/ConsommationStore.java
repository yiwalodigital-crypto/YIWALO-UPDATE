package com.yiwalo.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.Build;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Historique persistant de la consommation de données (jour par jour). */
final class ConsommationStore {
    private static final String PREFS = "conso_historique";
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private ConsommationStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String cle(String prefixe, String date) {
        return prefixe + "_" + date;
    }

    /** À appeler avec le delta d'octets reçu/envoyé depuis le dernier appel. */
    static synchronized void ajouter(Context c, long deltaRx, long deltaTx) {
        if (deltaRx < 0) deltaRx = 0;
        if (deltaTx < 0) deltaTx = 0;
        if (deltaRx == 0 && deltaTx == 0) return;
        SharedPreferences p = prefs(c);
        String aujourdhui = FMT.format(new Date());
        SharedPreferences.Editor e = p.edit();
        e.putLong(cle("rx", aujourdhui), p.getLong(cle("rx", aujourdhui), 0) + deltaRx);
        e.putLong(cle("tx", aujourdhui), p.getLong(cle("tx", aujourdhui), 0) + deltaTx);
        e.putLong("total_rx", p.getLong("total_rx", 0) + deltaRx);
        e.putLong("total_tx", p.getLong("total_tx", 0) + deltaTx);
        e.apply();
        verifierQuota(c);
    }

    private static long dernierRx = -1, dernierTx = -1;

    /**
     * Lit les compteurs de l'application et enregistre la différence depuis le dernier appel.
     * À appeler régulièrement (écran de consommation ET service VPN) : un seul endroit compte.
     */
    static synchronized void echantillonner(Context c) {
        // compteurs des données mobiles de TOUT le téléphone (pas seulement de l'application)
        long rx = TrafficStats.getMobileRxBytes();
        long tx = TrafficStats.getMobileTxBytes();
        if (rx < 0 || tx < 0) return;
        if (dernierRx >= 0) ajouter(c, rx - dernierRx, tx - dernierTx);
        dernierRx = rx;
        dernierTx = tx;
    }

    // ---------------------------------------------------------------- Quotas

    private static final String PREFS_QUOTA = "conso_quota";
    private static final SimpleDateFormat FMT_MOIS = new SimpleDateFormat("yyyy-MM", Locale.US);

    private static SharedPreferences quota(Context c) {
        return c.getSharedPreferences(PREFS_QUOTA, Context.MODE_PRIVATE);
    }

    /** periode = "jour" ou "mois". unite = 0 pour Mo, 1 pour Go. texte vide = pas de limite. */
    static void definirLimite(Context c, String periode, String texte, int unite) {
        quota(c).edit()
                .putString("val_" + periode, texte == null ? "" : texte.trim())
                .putInt("unite_" + periode, unite)
                .putInt("niveau_" + periode, 0)
                .apply();
    }

    static String limiteTexte(Context c, String periode) {
        return quota(c).getString("val_" + periode, "");
    }

    static int limiteUnite(Context c, String periode) {
        return quota(c).getInt("unite_" + periode, 0);
    }

    static long limiteOctets(Context c, String periode) {
        String t = limiteTexte(c, periode).replace(',', '.');
        if (t.isEmpty()) return 0;
        try {
            double v = Double.parseDouble(t);
            if (v <= 0) return 0;
            double mult = limiteUnite(c, periode) == 1 ? 1073741824d : 1048576d;
            return (long) (v * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Alertes de quota calculées avec les valeurs réelles lues dans Android. */
    static synchronized void verifierExterne(Context c, long utiliseJour, long utiliseMois) {
        long lim = limiteOctets(c, "jour");
        if (lim > 0) {
            verifierPeriode(c, "jour", FMT.format(new Date()), "du jour", utiliseJour, lim);
        }
        lim = limiteOctets(c, "mois");
        if (lim > 0) {
            verifierPeriode(c, "mois", FMT_MOIS.format(new Date()), "du mois", utiliseMois, lim);
        }
    }

    private static void verifierQuota(Context c) {
        long lim = limiteOctets(c, "jour");
        if (lim > 0) {
            long[] u = jour(c, 0);
            verifierPeriode(c, "jour", FMT.format(new Date()), "du jour", u[0] + u[1], lim);
        }
        lim = limiteOctets(c, "mois");
        if (lim > 0) {
            long[] u = mois(c);
            verifierPeriode(c, "mois", FMT_MOIS.format(new Date()), "du mois", u[0] + u[1], lim);
        }
    }

    private static void verifierPeriode(Context c, String periode, String idPeriode,
                                        String libelle, long utilise, long lim) {
        SharedPreferences q = quota(c);
        String cleId = "periode_" + periode;
        String cleNiveau = "niveau_" + periode;
        int deja = idPeriode.equals(q.getString(cleId, "")) ? q.getInt(cleNiveau, 0) : 0;
        int pct = (int) Math.min(100000L, utilise * 100L / lim);
        int[] niveaux = {100, 80, 50};
        for (int n : niveaux) {
            if (pct >= n) {
                if (n > deja) {
                    q.edit().putString(cleId, idPeriode).putInt(cleNiveau, n).apply();
                    notifier(c, periode, libelle, n, utilise, lim);
                }
                break;
            }
        }
    }

    private static void notifier(Context c, String periode, String libelle, int niveau,
                                 long utilise, long lim) {
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(
                        "quota", "Alertes de quota", NotificationManager.IMPORTANCE_HIGH));
            }
            String titre = niveau >= 100
                    ? "Quota " + libelle + " atteint"
                    : "Quota " + libelle + " : " + niveau + " % utilisé";
            String texte = ConsommationView.taille(utilise) + " / " + ConsommationView.taille(lim);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, "quota") : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(titre)
                    .setContentText(texte)
                    .setAutoCancel(true);
            Intent ouvrir = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if (ouvrir != null) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
                b.setContentIntent(PendingIntent.getActivity(c, 0, ouvrir, flags));
            }
            nm.notify("jour".equals(periode) ? 4101 : 4102, b.build());
        } catch (Exception ignore) {
            // pas de permission de notification : on ne plante pas
        }
    }

    /** decalage = 0 pour aujourd'hui, 1 pour hier, etc. */
    static long[] jour(Context c, int decalage) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -decalage);
        String date = FMT.format(cal.getTime());
        SharedPreferences p = prefs(c);
        return new long[] { p.getLong(cle("rx", date), 0), p.getLong(cle("tx", date), 0) };
    }

    /** Depuis lundi (inclus) jusqu'à aujourd'hui. */
    static long[] semaine(Context c) {
        Calendar cal = Calendar.getInstance();
        int jourSemaine = cal.get(Calendar.DAY_OF_WEEK);
        int decalageLundi = (jourSemaine == Calendar.SUNDAY) ? 6 : jourSemaine - Calendar.MONDAY;
        long rx = 0, tx = 0;
        for (int i = 0; i <= decalageLundi; i++) {
            long[] j = jour(c, i);
            rx += j[0];
            tx += j[1];
        }
        return new long[] { rx, tx };
    }

    /** Depuis le 1er du mois jusqu'à aujourd'hui. */
    static long[] mois(Context c) {
        Calendar cal = Calendar.getInstance();
        int jourDuMois = cal.get(Calendar.DAY_OF_MONTH);
        long rx = 0, tx = 0;
        for (int i = 0; i < jourDuMois; i++) {
            long[] j = jour(c, i);
            rx += j[0];
            tx += j[1];
        }
        return new long[] { rx, tx };
    }

    static long[] total(Context c) {
        SharedPreferences p = prefs(c);
        return new long[] { p.getLong("total_rx", 0), p.getLong("total_tx", 0) };
    }
}
