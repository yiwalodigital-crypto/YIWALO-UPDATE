package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwalo.tunnel.vpn.YiwaloVpnService;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carte « Informations réseau » : IP locale, adresse du VPN, IP publique, type de réseau et état.
 * Les valeurs locales se mettent à jour toutes les 3 s ; l'IP publique est relue quand le réseau
 * ou l'état du VPN change, ou sur « Actualiser ». Toucher une adresse la copie.
 */
final class InfosReseauView extends LinearLayout {
    private static final long PERIODE = 3000;
    private static final long VALIDITE_IP = 60000;

    private static boolean replie = false;
    private static String ipCache = null;
    private static boolean ipCacheViaVpn = false;
    private static long ipCacheHeure = 0;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final LinearLayout corps;
    private final TextView chevron;
    private final TextView vLocale, vVpn, vPublique, vType, vEtat, vSim;
    private final View blocSim;

    private String brutLocale, brutVpn, brutPublique;
    private String signature = null;
    private String resume = "";
    private boolean horsLigne = false;
    private int genIp = 0;

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            majLocal();
            h.postDelayed(this, PERIODE);
        }
    };
    private final Runnable chargerIpTache = this::chargerIp;

    InfosReseauView(Context c) {
        this(c, false);
    }

    InfosReseauView(Context c, boolean page) {
        super(c);
        this.ctx = c;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);

        LinearLayout tete = new LinearLayout(c);
        tete.setOrientation(HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        TextView titre = Ui.texte(c, "INFORMATIONS RÉSEAU", 12f, Ui.DISCRET, true);
        titre.setLetterSpacing(0.08f);
        titre.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), 0, Ui.dp(c, 10));
        if (!page) titre.setOnClickListener(v -> basculer());
        tete.addView(titre, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView actu = Ui.texte(c, "Actualiser", 13f, c.getColor(R.color.bleu_clair), false);
        actu.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        actu.setOnClickListener(v -> actualiser());
        tete.addView(actu);
        chevron = Ui.texte(c, replie ? "▸" : "▾", 16f, Ui.DISCRET, false);
        chevron.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
        chevron.setOnClickListener(v -> basculer());
        if (page) chevron.setVisibility(GONE);
        tete.addView(chevron);
        addView(tete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        corps = Ui.carte(c);
        vLocale = ligne(corps, "Adresse IP locale", false, 0);
        vVpn = ligne(corps, "Adresse attribuée au VPN", true, 1);
        vPublique = ligne(corps, "Adresse IP publique", true, 2);
        vType = ligne(corps, "Type de réseau", true, -1);
        vEtat = ligne(corps, "État de la connexion", true, -1);

        LinearLayout sim = new LinearLayout(c);
        sim.setOrientation(VERTICAL);
        vSim = ligne(sim, "Réseaux détectés", true, -1);
        blocSim = sim;
        sim.setVisibility(GONE);
        corps.addView(sim, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        corps.setVisibility(page || !replie ? VISIBLE : GONE);
        addView(corps);
    }

    /** Ajoute une ligne « libellé / valeur ». copiable : 0 = IP locale, 1 = VPN, 2 = publique, -1 = non. */
    private TextView ligne(LinearLayout parent, String label, boolean separateur, final int copiable) {
        if (separateur) {
            View s = new View(ctx);
            s.setBackgroundColor(Ui.BORD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = Ui.dp(ctx, 14);
            lp.rightMargin = Ui.dp(ctx, 14);
            parent.addView(s, lp);
        }
        LinearLayout r = Ui.info(ctx, label, "—");
        TextView valeur = (TextView) r.getChildAt(1);
        valeur.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.55f));
        if (copiable >= 0) {
            r.setClickable(true);
            r.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(Ui.SOMBRE ? 0x33FFFFFF : 0x1F0F172A), null,
                    new ColorDrawable(0xFFFFFFFF)));
            r.setOnClickListener(v -> copier(copiable == 0 ? brutLocale
                    : copiable == 1 ? brutVpn : brutPublique));
        }
        parent.addView(r);
        return valeur;
    }

    private void basculer() {
        replie = !replie;
        corps.setVisibility(replie ? GONE : VISIBLE);
        chevron.setText(replie ? "▸" : "▾");
    }

    /** Résumé « type – état » pour le rapport de diagnostic (sans adresse IP). */
    String resume() {
        return resume;
    }

    void actualiser() {
        majLocal();
        chargerIp();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        majLocal();
        if (ipCache != null) {
            afficherIp(ipCache, ipCacheViaVpn);
        }
        if (ipCache == null || System.currentTimeMillis() - ipCacheHeure > VALIDITE_IP) {
            chargerIp();
        }
        h.postDelayed(tic, PERIODE);
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    // ---------- Données locales (instantanées)

    @SuppressWarnings("deprecation")
    private void majLocal() {
        String locale = null;
        try {
            locale = ReseauLocal.ipLocale(ctx);
        } catch (RuntimeException ignore) {
            // adresse locale indisponible
        }
        List<String> valides = new ArrayList<>();
        List<String> nonValides = new ArrayList<>();
        boolean vpnActif = false;
        String adrVpn = null;
        boolean ouvert = false;
        int etatCouleur = Ui.ROUGE;
        String etat = "Hors ligne";
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc == null) {
                        continue;
                    }
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        vpnActif = true;
                        if (adrVpn == null) {
                            adrVpn = premiereIpv4(cm.getLinkProperties(n));
                        }
                        continue;
                    }
                    if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        continue;
                    }
                    String t = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                            : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Données mobiles"
                            : nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : null;
                    if (t == null) {
                        continue;
                    }
                    List<String> cible = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            ? valides : nonValides;
                    if (!cible.contains(t)) {
                        cible.add(t);
                    }
                }
                Network actif = cm.getActiveNetwork();
                NetworkCapabilities nca = actif == null ? null : cm.getNetworkCapabilities(actif);
                if (nca != null) {
                    ouvert = true;
                    if (nca.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        etat = "Connecté (Internet validé)";
                        etatCouleur = Ui.VERT;
                    } else {
                        etat = "Connecté (Internet non validé)";
                        etatCouleur = Ui.ORANGE;
                    }
                }
            }
        } catch (RuntimeException ignore) {
            // état réseau illisible : on garde « Hors ligne »
        }
        if (adrVpn == null && vpnActif) {
            adrVpn = interfaceTun();
        }
        horsLigne = !ouvert;

        List<String> types = !valides.isEmpty() ? valides : nonValides;
        StringBuilder sb = new StringBuilder();
        for (String t : types) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(T.t(t));
        }
        if (sb.length() == 0) {
            sb.append(T.t("Aucun réseau"));
        }
        if (vpnActif) {
            sb.append(" + VPN");
        }
        String type = sb.toString();

        brutLocale = locale;
        brutVpn = adrVpn;
        vLocale.setText(locale != null ? locale : T.t("Indisponible"));
        vLocale.setTextColor(locale != null ? Ui.TEXTE : Ui.DISCRET);
        vVpn.setText(adrVpn != null ? adrVpn : T.t("Non connecté"));
        vVpn.setTextColor(adrVpn != null ? Ui.TEXTE : Ui.DISCRET);
        vType.setText(type);
        vEtat.setText(T.t(etat));
        vEtat.setTextColor(etatCouleur);
        resume = type + " – " + T.t(etat);

        String sims = TestConfigsView.RESEAUX_DETECTES;
        if (sims != null && !sims.isEmpty()) {
            vSim.setText(sims);
            blocSim.setVisibility(VISIBLE);
        } else {
            blocSim.setVisibility(GONE);
        }

        // un changement de réseau ou d'état du VPN relance la lecture de l'IP publique
        String sig = type + "|" + etat + "|" + adrVpn + "|" + SuiviVpn.etat().name();
        if (signature != null && !sig.equals(signature)) {
            h.removeCallbacks(chargerIpTache);
            h.postDelayed(chargerIpTache, 2500);
        }
        signature = sig;
    }

    private static String premiereIpv4(LinkProperties lp) {
        if (lp == null) {
            return null;
        }
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                return a.getHostAddress();
            }
        }
        return null;
    }

    private static String interfaceTun() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.getName().startsWith("tun")) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) {
            // interfaces illisibles
        }
        return null;
    }

    // ---------- IP publique (réseau)

    private void chargerIp() {
        h.removeCallbacks(chargerIpTache);
        final int g = ++genIp;
        if (horsLigne) {
            ipCache = null;
            afficherIp(null, false);
            return;
        }
        brutPublique = null;
        vPublique.setText(T.t("Recherche…"));
        vPublique.setTextColor(Ui.DISCRET);
        final boolean tunnel = SuiviVpn.etat() == EtatVpn.CONNECTE && YiwaloVpnService.portTest > 0;
        new Thread(() -> {
            String ip = null;
            boolean viaVpn = false;
            if (tunnel) {
                try {
                    ip = VerificationIpView.viaTunnel()[0];
                    viaVpn = true;
                } catch (Exception e) {
                    ip = null;
                }
            }
            if (ip == null) {
                try {
                    ip = VerificationIpView.ipwho()[0];
                } catch (Exception e) {
                    ip = null;
                }
            }
            if (ip == null) {
                try {
                    ip = VerificationIpView.ipapi()[0];
                } catch (Exception e) {
                    ip = null;
                }
            }
            if (ip != null && (ip.isEmpty() || "—".equals(ip))) {
                ip = null;
            }
            final String r = ip;
            final boolean via = viaVpn;
            h.post(() -> {
                if (g != genIp) {
                    return; // une lecture plus récente a pris le relais
                }
                ipCache = r;
                ipCacheViaVpn = via;
                ipCacheHeure = System.currentTimeMillis();
                afficherIp(r, via);
            });
        }, "yiwalo-infos-ip").start();
    }

    private void afficherIp(String ip, boolean viaVpn) {
        brutPublique = ip;
        if (ip == null) {
            vPublique.setText(T.t(horsLigne ? "Hors ligne" : "Indisponible"));
            vPublique.setTextColor(Ui.DISCRET);
            return;
        }
        boolean vpnConnecte = SuiviVpn.etat() == EtatVpn.CONNECTE;
        String suffixe = viaVpn ? " (via VPN)"
                : vpnConnecte ? " (" + T.t("directe") + ")" : "";
        vPublique.setText(ip + suffixe);
        vPublique.setTextColor(Ui.TEXTE);
    }

    private void copier(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("YIWALO TUNNEL", valeur));
            T.toast(ctx, "Copié", Toast.LENGTH_SHORT).show();
        }
    }
}
