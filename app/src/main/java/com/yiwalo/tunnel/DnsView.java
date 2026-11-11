package com.yiwalo.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Test de fuite DNS : compare le résolveur utilisé avec celui de l'opérateur. */
final class DnsView extends LinearLayout {
    private static final String SONDE = "whoami.akamai.net";

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile int gen;
    private boolean enCours;

    private final TextView bouton;
    private final TextView verdict;
    private final TextView explication;
    private final LinearLayout liste;

    DnsView(Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 24));
        addView(Ui.entete(ctx, "Test de fuite DNS", retour));

        LinearLayout saisie = Ui.carte(ctx);
        saisie.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        saisie.addView(Ui.texte(ctx,
                "Vérifie que tes requêtes DNS passent bien par le tunnel et non par le "
                        + "serveur DNS de ton opérateur. Connecte d’abord le tunnel.",
                13f, Ui.DISCRET, false));
        bouton = Ui.bouton(ctx, "Lancer le test", Ui.BLEU, this::basculer);
        saisie.addView(bouton);
        ViewGroup.LayoutParams bl = bouton.getLayoutParams();
        if (bl instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) bl).topMargin = Ui.dp(ctx, 14);
        }
        addView(saisie);

        LinearLayout res = Ui.carte(ctx);
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) res.getLayoutParams();
        rp.topMargin = Ui.dp(ctx, 12);
        res.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 12));
        verdict = Ui.texte(ctx, "—", 24f, Ui.TEXTE, true);
        res.addView(verdict);
        explication = Ui.texte(ctx, "", 13f, Ui.DISCRET, false);
        res.addView(explication, marge(6));
        liste = new LinearLayout(ctx);
        liste.setOrientation(VERTICAL);
        res.addView(liste, marge(8));
        addView(res);
    }

    private LinearLayout.LayoutParams marge(int haut) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(ctx, haut);
        return p;
    }

    private void basculer() {
        if (enCours) arreter(); else lancer();
    }

    private void arreter() {
        gen++;
        enCours = false;
        verdict.setText("—");
        verdict.setTextColor(Ui.TEXTE);
        explication.setText("Test arrêté.");
        bouton.setText("Relancer le test");
        bouton.setBackground(Ui.fond(ctx, Ui.BLEU, 12, 0));
    }

    private void lancer() {
        final int g = ++gen;
        enCours = true;
        liste.removeAllViews();
        verdict.setText("Test en cours…");
        verdict.setTextColor(Ui.TEXTE);
        explication.setText("Interrogation des serveurs DNS…");
        bouton.setText("Arrêter");
        bouton.setBackground(Ui.fond(ctx, Ui.ROUGE, 12, 0));
        new Thread(() -> analyser(g), "yiwalo-dns").start();
    }

    private void analyser(final int g) {
        try {
            final boolean vpn = "CONNECTE".equals(SuiviVpn.etat().name());
            boolean dansTunnel = false;
            ConnectivityManager cm = null;
            try {
                cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network actif = cm.getActiveNetwork();
                NetworkCapabilities nc = actif == null ? null : cm.getNetworkCapabilities(actif);
                dansTunnel = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            } catch (RuntimeException ignore) {
                // état du réseau indisponible
            }

            List<String> utilises = resolveurs(null);
            if (g != gen) return;

            TreeSet<String> operateur = new TreeSet<>();
            try {
                for (TestConfigsView.Reseau r : TestConfigsView.reseauxActifs(ctx)) {
                    if (g != gen) return;
                    if (aTransportVpn(cm, r.net)) continue;
                    operateur.addAll(resolveurs(r.net));
                }
            } catch (RuntimeException ignore) {
                // réseaux de l'opérateur indisponibles
            }
            if (g != gen) return;

            String ip = ipPublique();
            if (g != gen) return;

            final List<String[]> lignes = new ArrayList<>();
            lignes.add(new String[]{"Tunnel", vpn ? "Connecté" : "Non connecté"});
            String orgUtilise = "";
            int n = 0;
            for (String r : utilises) {
                if (n++ >= 2) break;
                String org = organisation(r);
                if (n == 1) orgUtilise = org;
                lignes.add(new String[]{"Résolveur utilisé",
                        r + (org.isEmpty() ? "" : " (" + org + ")")});
            }
            String orgOp = "";
            n = 0;
            for (String r : operateur) {
                if (n++ >= 2) break;
                String org = organisation(r);
                if (n == 1) orgOp = org;
                lignes.add(new String[]{"Résolveur de l’opérateur",
                        r + (org.isEmpty() ? "" : " (" + org + ")")});
            }
            if (ip != null) {
                String org = organisation(ip);
                lignes.add(new String[]{"IP publique",
                        ip + (org.isEmpty() ? "" : " (" + org + ")")});
            }
            if (g != gen) return;

            boolean commun = false;
            for (String r : utilises) {
                if (operateur.contains(r)) commun = true;
            }
            boolean memeOrg = !orgUtilise.isEmpty() && orgUtilise.equalsIgnoreCase(orgOp);

            String v;
            int c;
            String e;
            if (utilises.isEmpty()) {
                v = "Test impossible";
                c = Ui.ORANGE;
                e = "Aucun serveur DNS n’a répondu. Vérifie ta connexion Internet.";
            } else if (!vpn) {
                v = "Tunnel non connecté";
                c = Ui.ORANGE;
                e = "Connecte le tunnel puis relance le test. Pour l’instant, tes requêtes "
                        + "DNS passent par ton opérateur.";
            } else if (!dansTunnel) {
                v = "Test peu fiable";
                c = Ui.ORANGE;
                e = "L’application est exclue du tunnel : ce test ne reflète pas le DNS "
                        + "de tes autres applications.";
            } else if (operateur.isEmpty()) {
                v = "Comparaison impossible";
                c = Ui.ORANGE;
                e = "Le résolveur de ton opérateur n’a pas pu être mesuré.";
            } else if (commun || memeOrg) {
                v = "Fuite DNS détectée";
                c = Ui.ROUGE;
                e = "Tes requêtes DNS passent par le serveur de ton opérateur au lieu "
                        + "du tunnel.";
            } else {
                v = "DNS protégé";
                c = Ui.VERT;
                e = "Tes requêtes DNS passent par le tunnel : le résolveur utilisé n’est "
                        + "pas celui de ton opérateur.";
            }
            final String vF = v;
            final String eF = e;
            final int cF = c;
            h.post(() -> {
                if (g != gen) return;
                enCours = false;
                verdict.setText(vF);
                verdict.setTextColor(cF);
                explication.setText(eF);
                afficher(lignes);
                bouton.setText("Relancer le test");
                bouton.setBackground(Ui.fond(ctx, Ui.BLEU, 12, 0));
            });
        } catch (final Exception ex) {
            final String m = ex.getMessage() != null ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            h.post(() -> {
                if (g != gen) return;
                enCours = false;
                verdict.setText("Erreur");
                verdict.setTextColor(Ui.ROUGE);
                explication.setText(m);
                bouton.setText("Relancer le test");
                bouton.setBackground(Ui.fond(ctx, Ui.BLEU, 12, 0));
            });
        }
    }

    private void afficher(List<String[]> lignes) {
        liste.removeAllViews();
        for (int i = 0; i < lignes.size(); i++) {
            LinearLayout bloc = new LinearLayout(ctx);
            bloc.setOrientation(VERTICAL);
            bloc.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
            bloc.addView(Ui.texte(ctx, lignes.get(i)[0], 12f, Ui.DISCRET, false));
            bloc.addView(Ui.texte(ctx, lignes.get(i)[1], 15f, Ui.TEXTE, true));
            if (i > 0) liste.addView(Ui.separateur(ctx));
            liste.addView(bloc);
        }
    }

    private static boolean aTransportVpn(ConnectivityManager cm, Network n) {
        if (cm == null || n == null) return false;
        try {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Adresses des résolveurs DNS vues par Internet (réseau par défaut si net est null). */
    private static List<String> resolveurs(Network net) {
        TreeSet<String> set = new TreeSet<>();
        for (int i = 0; i < 3 && set.isEmpty(); i++) {
            try {
                InetAddress[] a = net == null ? InetAddress.getAllByName(SONDE)
                        : net.getAllByName(SONDE);
                for (InetAddress x : a) {
                    set.add(x.getHostAddress());
                }
            } catch (Exception ignore) {
                // nouvel essai
            }
        }
        return new ArrayList<>(set);
    }

    private static String lireUrl(String adresse, int delai) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(adresse).openConnection();
            c.setConnectTimeout(delai);
            c.setReadTimeout(delai);
            if (c.getResponseCode() != 200) return "";
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            try {
                String l = br.readLine();
                return l == null ? "" : l.trim();
            } finally {
                br.close();
            }
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String ipPublique() {
        String[] urls = {"https://api.ipify.org", "https://ifconfig.me/ip"};
        for (String u : urls) {
            String r = lireUrl(u, 5000);
            if (!r.isEmpty() && r.length() <= 45 && r.matches("[0-9a-fA-F:.]+")) return r;
        }
        return null;
    }

    private static String organisation(String ip) {
        String r = lireUrl("https://ipinfo.io/" + ip + "/org", 4000);
        if (r.isEmpty() || r.length() > 80 || r.startsWith("{") || r.startsWith("<")) {
            return "";
        }
        return r;
    }

    @Override protected void onDetachedFromWindow() {
        gen++;
        super.onDetachedFromWindow();
    }
}
