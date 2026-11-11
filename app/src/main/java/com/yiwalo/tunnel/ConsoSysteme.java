package com.yiwalo.tunnel;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Process;
import android.telephony.TelephonyManager;
import java.util.Calendar;

/**
 * Consommation réelle de TOUT le téléphone (toutes les applications, Wi-Fi et données mobiles),
 * lue depuis les statistiques réseau d'Android, les mêmes que l'écran « Utilisation des données »
 * des paramètres. Nécessite l'accès « Utilisation » accordé par l'utilisateur.
 */
final class ConsoSysteme {
    static final int TOUT = 0;
    static final int MOBILE = 1;
    static final int WIFI = 2;

    private static long cleJour = -1;
    private static int cleMode = -1;
    private static long[][] ancien;

    private ConsoSysteme() { }

    @SuppressWarnings("deprecation")
    static boolean permis(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int m = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
            if (m == AppOpsManager.MODE_DEFAULT) {
                return c.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static long minuit(int joursAvant) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -joursAvant);
        return cal.getTimeInMillis();
    }

    private static long debutSemaine() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int decalage = dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY;
        return minuit(decalage);
    }

    private static long debutMois() {
        Calendar cal = Calendar.getInstance();
        return minuit(cal.get(Calendar.DAY_OF_MONTH) - 1);
    }

    @SuppressWarnings("deprecation")
    private static String abonne(Context c) {
        if (Build.VERSION.SDK_INT >= 29) return null; // null = tous les réseaux mobiles
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            return tm == null ? null : tm.getSubscriberId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long[] requete(NetworkStatsManager nsm, int type, String id,
                                  long debut, long fin) {
        try {
            NetworkStats.Bucket b = nsm.querySummaryForDevice(type, id, debut, fin);
            return new long[]{b.getRxBytes(), b.getTxBytes()};
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }

    /** {reçu, envoyé} entre deux dates, pour tout le téléphone. */
    static long[] octets(Context c, long debut, long fin, int mode) {
        if (debut >= fin) return new long[]{0, 0};
        NetworkStatsManager nsm = (NetworkStatsManager) c.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (nsm == null) throw new SecurityException("Statistiques réseau indisponibles");
        long rx = 0;
        long tx = 0;
        if (mode != WIFI) {
            long[] m = requete(nsm, ConnectivityManager.TYPE_MOBILE, abonne(c), debut, fin);
            rx += m[0];
            tx += m[1];
        }
        if (mode != MOBILE) {
            long[] w = requete(nsm, ConnectivityManager.TYPE_WIFI, null, debut, fin);
            rx += w[0];
            tx += w[1];
        }
        return new long[]{rx, tx};
    }

    private static long[] somme(long[] a, long[] b) {
        return new long[]{a[0] + b[0], a[1] + b[1]};
    }

    /**
     * {aujourd'hui, hier, semaine, mois, 30 jours}. Les jours passés sont calculés une seule fois
     * par jour ; seule la journée en cours est relue à chaque appel.
     */
    static synchronized long[][] releve(Context c, int mode) {
        long m0 = minuit(0);
        if (ancien == null || cleJour != m0 || cleMode != mode) {
            ancien = new long[][]{
                    octets(c, minuit(1), m0, mode),
                    octets(c, debutSemaine(), m0, mode),
                    octets(c, debutMois(), m0, mode),
                    octets(c, minuit(29), m0, mode)};
            cleJour = m0;
            cleMode = mode;
        }
        long[] auj = octets(c, m0, System.currentTimeMillis() + 60000L, mode);
        return new long[][]{auj, ancien[0], somme(ancien[1], auj), somme(ancien[2], auj),
                somme(ancien[3], auj)};
    }
}
