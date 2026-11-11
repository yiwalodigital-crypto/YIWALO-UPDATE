package com.yiwalo.tunnel;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SuiviVpn {

    private static EtatVpn etat = EtatVpn.DECONNECTE;
    private static String message = "";
    private static long latenceMs = -1;
    private static long debut = 0;

    private static final List<Runnable> ECOUTEURS = new CopyOnWriteArrayList<>();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private SuiviVpn() {
    }

    public static synchronized EtatVpn etat() {
        return etat;
    }

    public static synchronized String message() {
        return message;
    }

    /** Latence mesurée en ms, ou -1 si aucune mesure n'a été effectuée. */
    public static synchronized long latenceMs() {
        return latenceMs;
    }

    /** Début de la connexion (SystemClock.elapsedRealtime), ou 0 si non connecté. */
    public static synchronized long debut() {
        return debut;
    }

    private static long cumul = 0;

    /** Durée totale de connexion, coupures de reconnexion exclues. */
    public static synchronized long dureeMs() {
        return cumul + (debut > 0 ? SystemClock.elapsedRealtime() - debut : 0);
    }

    private static long baseRx = -1;
    private static long baseTx = -1;

    public static synchronized void definirBase(long rx, long tx) {
        baseRx = rx;
        baseTx = tx;
    }

    public static synchronized long baseRx() {
        return baseRx;
    }

    public static synchronized long baseTx() {
        return baseTx;
    }

    private static long cumulRx = 0;
    private static long cumulTx = 0;

    /** Total reçu (segments précédents + segment en cours), -1 si non supporté. */
    public static synchronized long totalRx() {
        long n = cumulRx;
        if (etat == EtatVpn.CONNECTE && baseRx >= 0) {
            long a = TrafficStats.getUidRxBytes(android.os.Process.myUid());
            if (a == TrafficStats.UNSUPPORTED) return -1;
            n += Math.max(0, a - baseRx);
        }
        return n;
    }

    public static synchronized long totalTx() {
        long n = cumulTx;
        if (etat == EtatVpn.CONNECTE && baseTx >= 0) {
            long a = TrafficStats.getUidTxBytes(android.os.Process.myUid());
            if (a == TrafficStats.UNSUPPORTED) return -1;
            n += Math.max(0, a - baseTx);
        }
        return n;
    }

    /** Garde les octets du segment qui se termine (coupure réseau). */
    private static void figerOctets() {
        int uid = android.os.Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid);
        long tx = TrafficStats.getUidTxBytes(uid);
        if (rx != TrafficStats.UNSUPPORTED && baseRx >= 0) cumulRx += Math.max(0, rx - baseRx);
        if (tx != TrafficStats.UNSUPPORTED && baseTx >= 0) cumulTx += Math.max(0, tx - baseTx);
        baseRx = -1;
        baseTx = -1;
    }

    public static synchronized void definirLatence(long ms) {
        latenceMs = ms;
    }

    public static synchronized void changer(EtatVpn nouvelEtat, String nouveauMessage) {
        etat = nouvelEtat;
        message = nouveauMessage == null ? "" : nouveauMessage;
        if (nouvelEtat == EtatVpn.CONNECTE) {
            debut = SystemClock.elapsedRealtime();
        } else {
            if (debut > 0) {
                cumul += SystemClock.elapsedRealtime() - debut;
                figerOctets();
            }
            if (nouvelEtat == EtatVpn.DECONNECTE) {
                cumul = 0;
                cumulRx = 0;
                cumulTx = 0;
                baseRx = -1;
                baseTx = -1;
            }
            debut = 0;
            if (nouvelEtat != EtatVpn.RECONNEXION) {
                latenceMs = -1;
            }
        }
        notifier();
    }

    public static void ajouterEcouteur(Runnable ecouteur) {
        ECOUTEURS.add(ecouteur);
    }

    public static void retirerEcouteur(Runnable ecouteur) {
        ECOUTEURS.remove(ecouteur);
    }

    private static void notifier() {
        for (Runnable r : ECOUTEURS) {
            PRINCIPAL.post(r);
        }
    }
}
