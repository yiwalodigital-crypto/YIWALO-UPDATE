package com.yiwalo.tunnel.ip;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import java.net.Inet4Address;
import java.net.InetAddress;

public final class ReseauMobile {

    private ReseauMobile() {
    }

    /** Vrai si le réseau est un réseau mobile (ni le Wi-Fi, ni un VPN). */
    public static boolean estMobile(ConnectivityManager cm, Network reseau) {
        NetworkCapabilities nc = cm.getNetworkCapabilities(reseau);
        return nc != null
                && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    /** Adresse IPv4 attribuée à ce réseau, ou null. */
    public static String ipv4(ConnectivityManager cm, Network reseau) {
        LinkProperties proprietes = cm.getLinkProperties(reseau);
        if (proprietes == null) {
            return null;
        }
        for (LinkAddress la : proprietes.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                return a.getHostAddress();
            }
        }
        return null;
    }

    /** Adresse IPv4 du réseau mobile actuel, ou null s'il n'y en a pas. */
    @SuppressWarnings("deprecation")
    public static String ipv4Mobile(ConnectivityManager cm) {
        for (Network reseau : cm.getAllNetworks()) {
            if (estMobile(cm, reseau)) {
                String ip = ipv4(cm, reseau);
                if (ip != null) {
                    return ip;
                }
            }
        }
        return null;
    }

    /** Nom de l'opérateur du réseau (ex. Orange BF), ou null. */
    public static String operateur(Context contexte) {
        try {
            TelephonyManager tm =
                    (TelephonyManager) contexte.getSystemService(Context.TELEPHONY_SERVICE);
            String nom = tm == null ? null : tm.getNetworkOperatorName();
            return (nom == null || nom.trim().isEmpty()) ? null : nom.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
