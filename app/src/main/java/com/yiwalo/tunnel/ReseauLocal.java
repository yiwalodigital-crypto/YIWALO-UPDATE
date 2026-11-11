package com.yiwalo.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public final class ReseauLocal {

    private ReseauLocal() {
    }

    /**
     * Adresse locale du téléphone sur son réseau Wi-Fi, mobile ou Ethernet
     * (les réseaux VPN sont ignorés). Retourne null si elle est introuvable.
     */
    @SuppressWarnings("deprecation")
    public static String ipLocale(Context contexte) {
        // d'abord un réseau qui a vraiment Internet, sinon comme avant
        String ip = ipLocale(contexte, true);
        return ip != null ? ip : ipLocale(contexte, false);
    }

    @SuppressWarnings("deprecation")
    private static String ipLocale(Context contexte, boolean valideSeulement) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) contexte.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return null;
            }
            String ipv6 = null;
            for (Network reseau : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(reseau);
                if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    continue;
                }
                boolean physique = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                if (!physique || (valideSeulement && !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))) {
                    continue;
                }
                LinkProperties proprietes = cm.getLinkProperties(reseau);
                if (proprietes == null) {
                    continue;
                }
                for (LinkAddress la : proprietes.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) {
                        continue;
                    }
                    if (a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                    if (a instanceof Inet6Address && ipv6 == null) {
                        String h = a.getHostAddress();
                        int pourcent = h.indexOf('%');
                        ipv6 = pourcent >= 0 ? h.substring(0, pourcent) : h;
                    }
                }
            }
            return ipv6;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
