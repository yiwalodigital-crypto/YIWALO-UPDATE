package com.yiwalo.tunnel.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Réglages du partage de connexion (proxy HTTP ouvert sur le point d’accès Wi-Fi). */
public final class PartageConnexion {
    public static final int PORT = 8080;
    private static volatile int portActuel = PORT;

    /** Port réellement utilisé par le partage (8080, ou le suivant s'il est occupé). */
    public static int portAffiche() {
        return portActuel;
    }

    private static int choisirPort() {
        for (int port = PORT; port < PORT + 10; port++) {
            try (java.net.ServerSocket s = new java.net.ServerSocket(port)) {
                return port;
            } catch (java.io.IOException ignore) {
                // port occupé : on essaie le suivant
            }
        }
        return PORT;
    }

    private PartageConnexion() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences("yiwalo_reglages", Context.MODE_PRIVATE);
    }

    public static boolean actif(Context c) {
        return prefs(c).getBoolean("partage_actif", false);
    }

    public static void definirActif(Context c, boolean v) {
        prefs(c).edit().putBoolean("partage_actif", v).apply();
    }

    public static boolean motDePasseActif(Context c) {
        return prefs(c).getBoolean("partage_auth", false);
    }

    public static void definirMotDePasseActif(Context c, boolean v) {
        prefs(c).edit().putBoolean("partage_auth", v).apply();
    }

    public static String utilisateur() {
        return "yiwalo-tunnel";
    }

    public static String motDePasse(Context c) {
        SharedPreferences p = prefs(c);
        String m = p.getString("partage_mdp", "");
        if (m.isEmpty()) {
            String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
            SecureRandom r = new SecureRandom();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            m = sb.toString();
            p.edit().putString("partage_mdp", m).apply();
        }
        return m;
    }

    /** Transmet le réglage au générateur Xray (appelé avant la génération de la config). */
    public static void appliquer(Context c) {
        if (!actif(c)) {
            GenerateurXray.partagePort = 0;
            return;
        }
        boolean mdp = motDePasseActif(c);
        GenerateurXray.partageUser = mdp ? utilisateur() : "";
        GenerateurXray.partagePass = mdp ? motDePasse(c) : "";
        portActuel = choisirPort();
        GenerateurXray.partagePort = portActuel;
    }

    /** Vrai si le point d'accès Wi-Fi est allumé (adresse privée hors Wi-Fi client). */
    public static boolean pointAccesActif(Context c) {
        String propre = ipWifiClient(c);
        for (String a : adresses()) {
            if (!a.equals(propre)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static String ipWifiClient(Context c) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || wm.getConnectionInfo() == null) {
                return "";
            }
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) {
                return "";
            }
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                    + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return "";
        }
    }

    /** Adresses IPv4 privées du téléphone (celle du point d’accès en fait partie). */
    public static List<String> adresses() {
        List<String> liste = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String nom = ni.getName().toLowerCase(Locale.ROOT);
                if (!ni.isUp() || ni.isLoopback() || nom.startsWith("tun")
                        || nom.startsWith("xray") || nom.startsWith("rmnet")
                        || nom.startsWith("ccmni") || nom.startsWith("dummy")
                        || nom.startsWith("v4-") || nom.startsWith("clat")) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        String ip = a.getHostAddress();
                        if (!liste.contains(ip)) {
                            liste.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // liste vide : l’écran demandera d’allumer le point d’accès
        }
        return liste;
    }
}
