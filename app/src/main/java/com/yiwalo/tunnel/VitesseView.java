package com.yiwalo.tunnel;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.vpn.GenerateurXray;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONObject;

final class VitesseView extends LinearLayout {
    private static final int CYAN = 0xFF22D3EE;
    private static final String[] SOURCES = {
            "https://speed.cloudflare.com/__down?bytes=25000000",
            "https://proof.ovh.net/files/100Mb.dat",
            "https://cachefly.cachefly.net/100mb.test"};
    private static final String[] PINGS = {
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204"};
    private static final String[][] HTTP_SRC = {
            {"speedtest.tele2.net", "/100MB.zip"},
            {"cachefly.cachefly.net", "/100mb.test"}};
    /** Serveurs d'envoi : hôte, chemin, port (443 = TLS). */
    private static final String[][] UP_SRC = {
            {"speed.cloudflare.com", "/__up", "443"},
            {"speedtest.tele2.net", "/upload.php", "80"}};

    private final Context ctx;
    private final ConfigStore store;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile int gen;
    private volatile String erreurDl;
    private volatile String erreurUl;
    private volatile boolean tunnel;
    private volatile int rttMs;
    private volatile boolean vpnExclu;
    private final LinearLayout histo;
    private boolean enCours;
    private double lisse;
    private Process proc;
    private File cfgFile;
    private int sPort;
    private String sUser, sPass;
    private final TextView phase, erreurVue, bouton, vLat, vJit, vDown, vUp, vCnx, vVerdTitre, vVerdTexte;
    private final View pointCnx, barreVerdict;
    private final LinearLayout verdict;
    private final Etapes etapes;
    private final Jauge jauge;
    private volatile String cnxTexte = "";

    VitesseView(Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        this.store = new ConfigStore(ctx);
        setOrientation(VERTICAL);
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 24));
        addView(Ui.entete(ctx, "Test de vitesse", retour));

        // Carte principale : connexion, compteur, étapes et statut
        LinearLayout hero = Ui.carte(ctx);
        hero.setBackground(degradeFond(0xFF212C47, 0xFF151C2D, 22));
        hero.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14));
        hero.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout pilule = new LinearLayout(ctx);
        pilule.setOrientation(HORIZONTAL);
        pilule.setGravity(Gravity.CENTER_VERTICAL);
        pilule.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 14), Ui.dp(ctx, 6));
        pilule.setBackground(Ui.fond(ctx, 0xFF0F1626, 20, Ui.BORD));
        pointCnx = new View(ctx);
        pilule.addView(pointCnx, new LinearLayout.LayoutParams(Ui.dp(ctx, 8), Ui.dp(ctx, 8)));
        vCnx = Ui.texte(ctx, "—", 12.5f, Ui.TEXTE, true);
        vCnx.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
        pilule.addView(vCnx);
        hero.addView(pilule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        jauge = new Jauge(ctx);
        hero.addView(jauge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 230)));

        etapes = new Etapes(ctx);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 34));
        ep.leftMargin = Ui.dp(ctx, 14);
        ep.rightMargin = Ui.dp(ctx, 14);
        ep.topMargin = Ui.dp(ctx, 2);
        hero.addView(etapes, ep);

        phase = Ui.texte(ctx, "Prêt", 16f, Ui.TEXTE, true);
        phase.setGravity(Gravity.CENTER);
        phase.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        hero.addView(phase);
        erreurVue = Ui.texte(ctx, "", 12f, Ui.ROUGE, false);
        erreurVue.setGravity(Gravity.CENTER);
        erreurVue.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8), 0);
        erreurVue.setVisibility(GONE);
        hero.addView(erreurVue);
        addView(hero);

        // Téléchargement et envoi, puis ping et gigue
        LinearLayout debits = new LinearLayout(ctx);
        debits.setOrientation(HORIZONTAL);
        vDown = tuile(debits, "TÉLÉCHARGEMENT", Ui.VERT, 0, 24f);
        vUp = tuile(debits, "ENVOI", Ui.VIOLET, 1, 24f);
        addView(debits, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout tuiles = new LinearLayout(ctx);
        tuiles.setOrientation(HORIZONTAL);
        vLat = tuile(tuiles, "PING", CYAN, 0, 20f);
        vJit = tuile(tuiles, "GIGUE", Ui.ROSE, 1, 20f);
        addView(tuiles, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Bouton
        bouton = Ui.bouton(ctx, "▶  Démarrer le test", Ui.VERT, this::basculer);
        bouton.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 17), Ui.dp(ctx, 12), Ui.dp(ctx, 17));
        bouton.setTextSize(16.5f);
        bouton.setLetterSpacing(0.03f);
        styleBouton(false);
        addView(bouton);

        // Verdict (affiché à la fin du test)
        verdict = Ui.carte(ctx);
        verdict.setOrientation(HORIZONTAL);
        verdict.setGravity(Gravity.CENTER_VERTICAL);
        verdict.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 14));
        ((LinearLayout.LayoutParams) verdict.getLayoutParams()).topMargin = Ui.dp(ctx, 14);
        barreVerdict = new View(ctx);
        verdict.addView(barreVerdict, new LinearLayout.LayoutParams(
                Ui.dp(ctx, 4), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = Ui.dp(ctx, 14);
        vVerdTitre = Ui.texte(ctx, "", 16f, Ui.TEXTE, true);
        vVerdTexte = Ui.texte(ctx, "", 13f, Ui.DISCRET, false);
        vVerdTexte.setPadding(0, Ui.dp(ctx, 2), 0, 0);
        col.addView(vVerdTitre);
        col.addView(vVerdTexte);
        verdict.addView(col, cp);
        verdict.setVisibility(GONE);
        addView(verdict);

        // Historique
        histo = Ui.carte(ctx);
        LinearLayout.LayoutParams hp = (LinearLayout.LayoutParams) histo.getLayoutParams();
        hp.topMargin = Ui.dp(ctx, 6);
        addView(histo);
        majHistorique();
        majConnexion();
    }

    // ---------- Aides de style ----------

    private GradientDrawable degradeFond(int haut, int bas, int rayon) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{haut, bas});
        g.setCornerRadius(Ui.dp(ctx, rayon));
        g.setStroke(Ui.dp(ctx, 1), Ui.BORD);
        return g;
    }

    private GradientDrawable rond(int couleur) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(couleur);
        return g;
    }

    private void styleBouton(boolean arret) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                arret ? new int[]{0xFFEF4444, 0xFFF97316} : new int[]{0xFF06B6D4, 0xFF3B82F6});
        g.setCornerRadius(Ui.dp(ctx, 14));
        GradientDrawable masque = new GradientDrawable();
        masque.setColor(0xFFFFFFFF);
        masque.setCornerRadius(Ui.dp(ctx, 14));
        bouton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), g, masque));
    }

    /** Affiche « 42 ms » avec l’unité en plus petit. */
    private static void valeur(TextView v, String nombre, String unite, int couleur) {
        SpannableString s = new SpannableString(nombre + " " + unite);
        s.setSpan(new RelativeSizeSpan(0.6f), nombre.length(), s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(s);
        v.setTextColor(couleur);
    }

    private TextView tuile(LinearLayout parent, String titre, int accent, int index, float taille) {
        LinearLayout t = Ui.carte(ctx);
        t.setBackground(degradeFond(0xFF1E2840, 0xFF161D2E, 16));
        t.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) lp.leftMargin = Ui.dp(ctx, 10);
        t.setLayoutParams(lp);
        LinearLayout tete = new LinearLayout(ctx);
        tete.setOrientation(HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        View p = new View(ctx);
        p.setBackground(rond(accent));
        tete.addView(p, new LinearLayout.LayoutParams(Ui.dp(ctx, 7), Ui.dp(ctx, 7)));
        TextView l = Ui.texte(ctx, titre, 10.5f, Ui.DISCRET, true);
        l.setLetterSpacing(0.06f);
        l.setSingleLine(true);
        l.setPadding(Ui.dp(ctx, 6), 0, 0, 0);
        tete.addView(l);
        TextView v = Ui.texte(ctx, "—", taille, Ui.TEXTE, true);
        v.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        t.addView(tete);
        t.addView(v);
        parent.addView(t);
        return v;
    }

    private void definirCnx(String t) {
        definirCnx(t, Ui.VIOLET);
    }

    private void definirCnx(String t, int couleur) {
        cnxTexte = t;
        vCnx.setText(t);
        pointCnx.setBackground(rond(couleur));
    }

    /** Texte d’état + étape (0 repos, 1 latence, 2 téléchargement, 3 envoi, 4 fini). */
    private void etat(String s, int etape) {
        phase.setText(s);
        phase.setTextColor(etape == 4 ? Ui.VERT : Ui.TEXTE);
        etapes.etape(etape);
    }

    private void afficherVerdict(double d, double u, double lat) {
        if (true) { verdict.setVisibility(GONE); return; }
        int niv = d >= 50 ? 4 : d >= 25 ? 3 : d >= 8 ? 2 : d >= 3 ? 1 : 0;
        if (lat > 150 && niv > 0) niv--;
        String[] titres = {"Connexion faible", "Connexion moyenne", "Bonne connexion",
                "Très bonne connexion", "Connexion excellente"};
        String[] textes = {
                "Messagerie et pages légères.",
                "Navigation et musique ; vidéo HD limitée.",
                "Streaming HD et appels fluides.",
                "Full HD, visio et gros téléchargements.",
                "4K, appels vidéo et jeux en ligne."};
        int[] couleurs = {Ui.ROUGE, Ui.ORANGE, Ui.BLEU, CYAN, Ui.VERT};
        String t = textes[niv];
        if (lat > 150) t += " Latence élevée : jeux déconseillés.";
        else if (u > 0 && u < 1.5) t += " Envoi faible : visio et envois lents.";
        vVerdTitre.setText(titres[niv]);
        vVerdTitre.setTextColor(couleurs[niv]);
        vVerdTexte.setText(t);
        barreVerdict.setBackground(Ui.fond(ctx, couleurs[niv], 3, 0));
        verdict.setVisibility(VISIBLE);
    }

    /** Vert = très bon, orange = moyen, rouge = mauvais. */
    private static int couleurMs(double ms) {
        return ms <= 100 ? Ui.VERT : ms <= 300 ? Ui.ORANGE : Ui.ROUGE;
    }

    private static int couleurGigue(double ms) {
        return ms <= 30 ? Ui.VERT : ms <= 100 ? Ui.ORANGE : Ui.ROUGE;
    }

    // ---------- Historique des tests ----------

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("yiwalo_vitesse", Context.MODE_PRIVATE);
    }

    private void enregistrer(double lat, double d, double u) {
        try {
            JSONArray a = new JSONArray(prefs().getString("historique", "[]"));
            JSONArray n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("l", lat);
            o.put("d", d);
            o.put("u", u);
            Cnx cnx = analyserCnx();
            o.put("c", cnx.texte);
            o.put("k", cnx.type);
            n.put(o);
            for (int i = 0; i < a.length() && n.length() < 8; i++) n.put(a.getJSONObject(i));
            prefs().edit().putString("historique", n.toString()).apply();
        } catch (Exception ignore) {
            // historique non critique
        }
    }

    private void majHistorique() {
        histo.removeAllViews();
        try {
            JSONArray a = new JSONArray(prefs().getString("historique", "[]"));
            int nb = Math.min(a.length(), 3);
            if (nb == 0) {
                histo.setVisibility(GONE);
                return;
            }
            double meilleur = 0;
            for (int i = 0; i < a.length(); i++) {
                meilleur = Math.max(meilleur, a.getJSONObject(i).optDouble("d", 0));
            }
            LinearLayout entete = new LinearLayout(ctx);
            entete.setOrientation(HORIZONTAL);
            entete.setGravity(Gravity.CENTER_VERTICAL);
            entete.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 2));
            TextView titre = Ui.texte(ctx, "HISTORIQUE", 11f, Ui.DISCRET, true);
            titre.setLetterSpacing(0.12f);
            titre.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            entete.addView(titre);
            TextView effacer = Ui.texte(ctx, "EFFACER", 11f, Ui.ROSE, true);
            effacer.setLetterSpacing(0.08f);
            effacer.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10));
            effacer.setOnClickListener(v -> {
                prefs().edit().remove("historique").apply();
                majHistorique();
            });
            entete.addView(effacer);
            histo.addView(entete);
            SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            for (int i = 0; i < nb; i++) {
                JSONObject o = a.getJSONObject(i);
                double d = o.optDouble("d", 0);
                double l = o.optDouble("l", -1);
                double up = o.optDouble("u", -1);
                String via = o.optString("c", "");
                int cv = couleurCnx(o.optString("k", ""), via);
                if (o.optString("k", "").isEmpty()) via = libelleSimple(via);
                boolean top = a.length() > 1 && d >= meilleur && d > 0;

                LinearLayout ligne = new LinearLayout(ctx);
                ligne.setOrientation(HORIZONTAL);
                ligne.setGravity(Gravity.CENTER_VERTICAL);
                ligne.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12));

                LinearLayout gauche = new LinearLayout(ctx);
                gauche.setOrientation(VERTICAL);
                gauche.addView(Ui.texte(ctx, (top ? "★ " : "") + f.format(new Date(o.optLong("t"))),
                        14f, top ? Ui.ORANGE : Ui.TEXTE, true));
                LinearLayout lv = new LinearLayout(ctx);
                lv.setOrientation(HORIZONTAL);
                lv.setGravity(Gravity.CENTER_VERTICAL);
                View pt = new View(ctx);
                pt.setBackground(rond(cv));
                LinearLayout.LayoutParams ptp = new LinearLayout.LayoutParams(
                        Ui.dp(ctx, 7), Ui.dp(ctx, 7));
                ptp.rightMargin = Ui.dp(ctx, 6);
                lv.addView(pt, ptp);
                lv.addView(Ui.texte(ctx, via.isEmpty() ? "—" : via, 11.5f, cv, true));
                gauche.addView(lv);

                // barre relative au meilleur résultat
                float part = meilleur > 0 ? (float) Math.max(0.04, d / meilleur) : 0f;
                LinearLayout barre = new LinearLayout(ctx);
                barre.setBackground(Ui.fond(ctx, 0xFF0F1626, 3, 0));
                View plein = new View(ctx);
                GradientDrawable gp = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{CYAN, Ui.BLEU});
                gp.setCornerRadius(Ui.dp(ctx, 3));
                plein.setBackground(gp);
                barre.addView(plein, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, part));
                barre.addView(new View(ctx), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0f, 1f - part)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 5));
                bp.topMargin = Ui.dp(ctx, 8);
                gauche.addView(barre, bp);

                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                gl.rightMargin = Ui.dp(ctx, 16);
                ligne.addView(gauche, gl);

                LinearLayout droite = new LinearLayout(ctx);
                droite.setOrientation(VERTICAL);
                droite.setGravity(Gravity.END);
                TextView vit = Ui.texte(ctx, "", 15f, Ui.TEXTE, true);
                valeur(vit, "↓ " + fmt(d), "Mb/s", Ui.TEXTE);
                vit.setGravity(Gravity.END);
                droite.addView(vit);
                String bas = (up > 0 ? "↑ " + fmt(up) + " Mb/s" : "")
                        + (up > 0 && l > 0 ? "  ·  " : "")
                        + (l > 0 ? String.format(Locale.getDefault(), "%.0f ms", l) : "");
                if (bas.isEmpty()) bas = "—";
                TextView ms = Ui.texte(ctx, bas, 12f, l > 0 ? couleurMs(l) : Ui.DISCRET, true);
                ms.setGravity(Gravity.END);
                droite.addView(ms);
                ligne.addView(droite);

                if (i > 0) {
                    View sep = new View(ctx);
                    sep.setBackgroundColor(Ui.BORD);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    sp.leftMargin = Ui.dp(ctx, 16);
                    sp.rightMargin = Ui.dp(ctx, 16);
                    histo.addView(sep, sp);
                }
                histo.addView(ligne);
            }
            histo.setVisibility(VISIBLE);
        } catch (Exception e) {
            histo.setVisibility(GONE);
        }
    }

    private void afficherLatence(final int g, final double[] l) {
        h.post(() -> {
            if (g != gen) return;
            valeur(vLat, String.format(Locale.getDefault(), "%.0f", l[0]), "ms", couleurMs(l[0]));
            valeur(vJit, String.format(Locale.getDefault(), "%.0f", l[1]), "ms", couleurGigue(l[1]));
        });
    }

    /** Délais adaptés à la qualité du réseau : courts si rapide, tolérants si lent. */
    private int cnxMs() {
        int r = rttMs;
        return r <= 0 ? 8000 : Math.max(3000, Math.min(10000, r * 8));
    }

    private int lectMs() {
        int r = rttMs;
        return r <= 0 ? 8000 : Math.max(4000, Math.min(12000, r * 12));
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format(Locale.getDefault(), "%.0f", v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }

    private static String court(String s) {
        if (s == null) return "réseau indisponible";
        s = s.trim();
        int n = s.indexOf('\n');
        if (n > 0) s = s.substring(0, n);
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    /** Type de connexion utilisé par le test : texte, couleur et catégorie (pour l'historique). */
    private static final class Cnx {
        String type = "none";   // vpn_app, vpn_ext, mobile, wifi, eth, none
        String texte = "Aucune connexion";
        int couleur = Ui.ROUGE;
    }

    /** Détecte si le test passe par le VPN Yiwalo, un VPN externe, les données mobiles ou le Wi-Fi. */
    private Cnx analyserCnx() {
        Cnx r = new Cnx();
        boolean vpn = false, actifVpn = false, wifi = false, mobile = false, eth = false;
        boolean wifiDispo = false, mobDispo = false, ethDispo = false;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network act = cm.getActiveNetwork();
            NetworkCapabilities nca = act == null ? null : cm.getNetworkCapabilities(act);
            if (nca != null) {
                actifVpn = nca.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                if (!actifVpn) {
                    wifi = nca.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    mobile = !wifi && nca.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    eth = !wifi && !mobile && nca.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                }
            }
            for (Network x : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(x);
                if (c == null) continue;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpn = true; continue; }
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiDispo = true;
                else if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mobDispo = true;
                else if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethDispo = true;
            }
            if (actifVpn) {
                // Le VPN est le réseau actif : on cherche sur quoi il s'appuie
                vpn = true;
                wifi = wifiDispo;
                mobile = !wifi && mobDispo;
                eth = !wifi && !mobile && ethDispo;
            }
            vpnExclu = vpn && !actifVpn;
        } catch (RuntimeException ignore) {
            // état réseau indisponible
        }
        EtatVpn e = SuiviVpn.etat();
        boolean propre = e == EtatVpn.CONNECTE || e == EtatVpn.RECONNEXION;
        if (propre || tunnel) {
            r.type = "vpn_app";
            r.texte = "VPN interne";
            r.couleur = Ui.VIOLET;
        } else if (vpn) {
            r.type = "vpn_ext";
            r.texte = "VPN externe";
            r.couleur = Ui.ORANGE;
        } else if (wifi) {
            r.type = "wifi";
            r.texte = "Wi-Fi";
            r.couleur = Ui.BLEU;
        } else if (mobile) {
            r.type = "mobile";
            r.texte = "Données mobiles";
            r.couleur = Ui.VERT;
        } else if (eth) {
            r.type = "eth";
            r.texte = "Ethernet";
            r.couleur = Ui.BLEU;
        }
        return r;
    }

    /** Couleur d'une ligne d'historique (les anciennes entrées n'ont pas de catégorie). */
    private static int couleurCnx(String type, String texte) {
        switch (type) {
            case "vpn_app": return Ui.VIOLET;
            case "vpn_ext": return Ui.ORANGE;
            case "mobile": return Ui.VERT;
            case "wifi":
            case "eth": return Ui.BLEU;
            default: break;
        }
        String t = texte.toLowerCase(Locale.ROOT);
        if (t.contains("externe")) return Ui.ORANGE;
        if (t.contains("vpn")) return Ui.VIOLET;
        if (t.contains("wi-fi")) return Ui.BLEU;
        if (t.contains("mobile")) return Ui.VERT;
        return Ui.DISCRET;
    }

    /** Anciennes entrées : ne garde que VPN, Wi-Fi ou Données mobiles. */
    private static String libelleSimple(String t) {
        String m = t.toLowerCase(Locale.ROOT);
        if (m.contains("externe")) return "VPN externe";
        if (m.contains("vpn")) return "VPN";
        if (m.contains("wi-fi")) return "Wi-Fi";
        if (m.contains("mobile")) return "Données mobiles";
        return t;
    }

    private void majConnexion() {
        Cnx c = analyserCnx();
        definirCnx(c.texte, c.couleur);
    }

    private void basculer() {
        if (enCours) arreter(); else demarrer();
    }

    private void arreter() {
        gen++;
        enCours = false;
        etat("Arrêté", 0);
        jauge.phase("", Ui.VERT);
        jauge.figer(-1);
        jauge.definir(0);
        bouton.setText("▶  Redémarrer le test");
        styleBouton(false);
    }

    private void demarrer() {
        final int g = ++gen;
        enCours = true;
        erreurDl = null;
        erreurUl = null;
        tunnel = false;
        lisse = 0;
        majConnexion();
        erreurVue.setVisibility(GONE);
        jauge.phase("Latence", CYAN);
        jauge.figer(-1);
        jauge.definir(0);
        verdict.setVisibility(GONE);
        for (TextView t : new TextView[]{vLat, vJit, vDown, vUp}) {
            t.setText("—");
            t.setTextColor(Ui.TEXTE);
        }
        etat("Latence", 1);
        bouton.setText("■  Arrêter");
        styleBouton(true);
        new Thread(() -> {
            try {
                rttMs = 0;
                double[] lat = null;
                final Configuration cfg = store.active();
                if (vpnExclu && cfg != null) {
                    // L'app est exclue du VPN : on mesure à travers la configuration active
                    // (même serveur que le tunnel) au lieu de la 4G directe.
                    try {
                        erreurDl = null;
                        h.post(() -> {
                            if (g != gen) return;
                            etat("Connexion au tunnel", 1);
                        });
                        lancerMoteur(cfg);
                        tunnel = true;
                        h.post(() -> {
                            if (g != gen) return;
                            majConnexion();
                            etat("Latence", 1);
                        });
                        lat = latenceSocks(g);
                    } catch (Exception e) {
                        // moteur indisponible : retour à la mesure directe
                        arreterMoteur();
                        tunnel = false;
                        lat = null;
                    }
                    if (g != gen) return;
                }
                if (!tunnel) lat = latence(g);
                if (g != gen) return;
                if (lat != null) {
                    rttMs = (int) Math.max(1, lat[0]);
                    afficherLatence(g, lat);
                }
                // Le téléchargement démarre tout de suite, même si le ping a échoué
                // (serveur lent ou filtré) : on ne bloque pas sur la latence.
                h.post(() -> {
                    if (g != gen) return;
                    etat("Téléchargement", 2);
                    jauge.phase("Téléchargement", Ui.VERT);
                });
                double d = flux(g, false);
                if (g != gen) return;
                if (d <= 0 && lat == null) {
                    // Aucune réponse en direct : on mesure à travers la configuration active.
                    final Configuration c = store.active();
                    if (c == null) {
                        throw new IOException("Aucun accès Internet.");
                    }
                    erreurDl = null;
                    h.post(() -> {
                        if (g != gen) return;
                        etat("Connexion au tunnel", 1);
                    });
                    lancerMoteur(c);
                    tunnel = true;
                    h.post(() -> {
                        if (g != gen) return;
                        majConnexion();
                    });
                    lat = latenceSocks(g);
                    if (g != gen) return;
                    rttMs = (int) Math.max(1, lat[0]);
                    afficherLatence(g, lat);
                    h.post(() -> {
                        if (g != gen) return;
                        etat("Téléchargement", 2);
                        jauge.phase("Téléchargement", Ui.VERT);
                        jauge.definir(0);
                    });
                    lisse = 0;
                    d = flux(g, false);
                    if (g != gen) return;
                }
                if (d <= 0) {
                    throw new IOException(erreurDl == null ? "Aucune donnée reçue." : erreurDl);
                }
                final double dd = d;
                h.post(() -> {
                    if (g != gen) return;
                    valeur(vDown, fmt(dd), "Mb/s", Ui.TEXTE);
                    etat("Envoi", 3);
                    jauge.phase("Envoi", Ui.VIOLET);
                    jauge.definir(0);
                });

                // Envoi : même méthode (plusieurs flux, chauffe, fenêtre stable)
                lisse = 0;
                final double uu = flux(g, true);
                if (g != gen) return;
                final double latMs = lat == null ? -1 : lat[0];
                enregistrer(latMs, dd, uu);
                h.post(() -> {
                    if (g != gen) return;
                    etat("Terminé", 4);
                    jauge.phase("Téléchargement", Ui.VERT);
                    jauge.figer(dd);
                    jauge.definir(0);
                    if (uu > 0) {
                        valeur(vUp, fmt(uu), "Mb/s", Ui.TEXTE);
                    } else {
                        vUp.setText("—");
                        vUp.setTextColor(Ui.DISCRET);
                    }
                    enCours = false;
                    bouton.setText("↻  Refaire le test");
                    styleBouton(false);
                    afficherVerdict(dd, uu, latMs);
                    majHistorique();
                });
            } catch (Exception e) {
                final String m = e instanceof java.net.SocketTimeoutException
                        ? "Délai dépassé : le serveur ne répond pas."
                        : court(e.getMessage() != null ? e.getMessage()
                        : e.getClass().getSimpleName());
                h.post(() -> {
                    if (g != gen) return;
                    enCours = false;
                    etat("Échec", 0);
                    phase.setTextColor(Ui.ROUGE);
                    erreurVue.setText(m);
                    erreurVue.setVisibility(VISIBLE);
                    jauge.phase("", Ui.VERT);
                    jauge.definir(0);
                    bouton.setText("↻  Réessayer");
                    styleBouton(false);
                });
            } finally {
                arreterMoteur();
            }
        }, "yiwalo-vitesse").start();
    }

    // ---------- Moteur Xray temporaire (mesure à travers la configuration) ----------

    private void lancerMoteur(Configuration c) throws Exception {
        File xray = new File(ctx.getApplicationInfo().nativeLibraryDir, "libxray.so");
        if (!xray.exists()) throw new IOException("Moteur introuvable.");
        GenerateurXray.Resultat r = GenerateurXray.generer(c, c.serveur);
        JSONObject o = new JSONObject(r.json);
        JSONArray in = o.getJSONArray("inbounds");
        JSONArray garde = new JSONArray();
        for (int i = 0; i < in.length(); i++) {
            JSONObject x = in.getJSONObject(i);
            if (!"tun".equals(x.optString("protocol"))) garde.put(x);
        }
        o.put("inbounds", garde);
        File dossier = new File(ctx.getCacheDir(), "tests");
        dossier.mkdirs();
        cfgFile = File.createTempFile("yiwalo", ".json", dossier);
        try (java.io.FileOutputStream fo = new java.io.FileOutputStream(cfgFile)) {
            fo.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        proc = new ProcessBuilder(xray.getAbsolutePath(), "run", "-c", cfgFile.getAbsolutePath())
                .redirectErrorStream(true).start();
        boolean pret = false;
        for (int i = 0; i < 120 && !pret && proc.isAlive(); i++) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress("127.0.0.1", r.portTest), 100);
                pret = true;
            } catch (Exception e) {
                Thread.sleep(50);
            } finally {
                try { s.close(); } catch (Exception ignore) { }
            }
        }
        if (!pret) throw new IOException("Le moteur n'a pas démarré avec la configuration active.");
        sPort = r.portTest;
        sUser = r.utilisateurTest;
        sPass = r.motDePasseTest;
    }

    private void arreterMoteur() {
        if (proc != null) {
            proc.destroyForcibly();
            proc = null;
        }
        if (cfgFile != null) {
            cfgFile.delete();
            cfgFile = null;
        }
    }

    private static void lireTout(InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) {
            int k = in.read(b, n, b.length - n);
            if (k < 0) throw new IOException("Connexion coupée par le serveur.");
            n += k;
        }
    }

    private static String raisonSocks(int code) {
        switch (code) {
            case 1: return "Échec général du serveur.";
            case 2: return "Règle du serveur : refusé.";
            case 3: return "Réseau injoignable.";
            case 4: return "Serveur injoignable.";
            case 5: return "Connexion refusée par le serveur.";
            case 6: return "Délai dépassé (serveur injoignable).";
            default: return "Erreur SOCKS " + code + ".";
        }
    }

    /** Ouvre une connexion TCP vers hote:port à travers le moteur (SOCKS local). */
    private Socket ouvrirSocks(String hote, int port) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", sPort), 2000);
            s.setSoTimeout(10000);
            OutputStream o = s.getOutputStream();
            InputStream in = s.getInputStream();
            o.write(new byte[]{5, 1, 2});
            o.flush();
            byte[] rep = new byte[2];
            lireTout(in, rep);
            if (rep[0] != 5 || rep[1] != 2) throw new IOException("Moteur : authentification refusée.");
            byte[] u = sUser.getBytes(StandardCharsets.US_ASCII);
            byte[] pw = sPass.getBytes(StandardCharsets.US_ASCII);
            java.io.ByteArrayOutputStream a = new java.io.ByteArrayOutputStream();
            a.write(1);
            a.write(u.length);
            a.write(u, 0, u.length);
            a.write(pw.length);
            a.write(pw, 0, pw.length);
            o.write(a.toByteArray());
            o.flush();
            lireTout(in, rep);
            if (rep[1] != 0) throw new IOException("Moteur : identifiants refusés.");
            byte[] hb = hote.getBytes(StandardCharsets.US_ASCII);
            java.io.ByteArrayOutputStream q = new java.io.ByteArrayOutputStream();
            q.write(5);
            q.write(1);
            q.write(0);
            q.write(3);
            q.write(hb.length);
            q.write(hb, 0, hb.length);
            q.write(port >> 8);
            q.write(port & 0xFF);
            o.write(q.toByteArray());
            o.flush();
            byte[] e = new byte[4];
            lireTout(in, e);
            if (e[1] != 0) throw new IOException(raisonSocks(e[1]));
            int atyp = e[3];
            int reste = atyp == 1 ? 6 : atyp == 4 ? 18 : in.read() + 2;
            lireTout(in, new byte[Math.max(0, reste)]);
            return s;
        } catch (IOException e) {
            try { s.close(); } catch (Exception ignore) { }
            throw e;
        }
    }

    private double[] latenceSocks(int g) throws IOException {
        List<Double> l = new ArrayList<>();
        IOException derniere = null;
        for (int i = 0; i < 4 && g == gen; i++) {
            long t0 = System.nanoTime();
            Socket s = null;
            try {
                s = ouvrirSocks("cp.cloudflare.com", 80);
                OutputStream o = s.getOutputStream();
                o.write(("GET /generate_204 HTTP/1.1\r\nHost: cp.cloudflare.com\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                o.flush();
                byte[] b = new byte[12];
                lireTout(s.getInputStream(), b);
                if (!new String(b, StandardCharsets.US_ASCII).startsWith("HTTP/1.")) {
                    throw new IOException("Réponse inattendue du serveur.");
                }
                l.add((System.nanoTime() - t0) / 1e6);
            } catch (IOException e) {
                derniere = e;
            } finally {
                if (s != null) try { s.close(); } catch (Exception ignore) { }
            }
        }
        if (l.isEmpty()) {
            throw derniere != null ? derniere
                    : new IOException("Aucune réponse à travers la configuration.");
        }
        if (l.size() > 1) l.remove(0);
        double somme = 0, jit = 0;
        for (int i = 0; i < l.size(); i++) {
            somme += l.get(i);
            if (i > 0) jit += Math.abs(l.get(i) - l.get(i - 1));
        }
        return new double[]{somme / l.size(), l.size() > 1 ? jit / (l.size() - 1) : 0};
    }

    private void telechargerSocks(int g, int idx, AtomicLong total, AtomicLong fin, long plafond) {
        byte[] b = new byte[65536];
        int s = idx % HTTP_SRC.length;
        int echecs = 0;
        while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                && echecs < HTTP_SRC.length * 2) {
            Socket so = null;
            long recu = 0;
            try {
                so = ouvrirSocks(HTTP_SRC[s][0], 80);
                OutputStream o = so.getOutputStream();
                o.write(("GET " + HTTP_SRC[s][1] + " HTTP/1.1\r\nHost: " + HTTP_SRC[s][0]
                        + "\r\nConnection: close\r\nUser-Agent: Mozilla/5.0\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                o.flush();
                InputStream in = so.getInputStream();
                boolean premier = true;
                int n;
                while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                        && (n = in.read(b)) > 0) {
                    if (premier) {
                        premier = false;
                        String st = new String(b, 0, Math.min(n, 15), StandardCharsets.US_ASCII);
                        if (!st.startsWith("HTTP/1.1 200") && !st.startsWith("HTTP/1.0 200")) {
                            throw new IOException("Serveur de test : " + st.trim());
                        }
                    }
                    total.addAndGet(n);
                    recu += n;
                }
            } catch (Exception e) {
                erreurDl = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                if (so != null) try { so.close(); } catch (Exception ignore) { }
            }
            if (recu == 0) {
                echecs++;
                s = (s + 1) % HTTP_SRC.length;
            }
        }
    }

    // ---------- Mesures en connexion directe ----------

    private HttpURLConnection ouvrir(String u) throws IOException {
        return ouvrir(u, cnxMs(), lectMs());
    }

    private HttpURLConnection ouvrir(String u, int cnx, int lecture) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(cnx);
        c.setReadTimeout(lecture);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    private double[] latence(final int g) {
        final double[][] res = new double[1][];
        final AtomicBoolean stop = new AtomicBoolean(false);
        final Object verrou = new Object();
        final int[] finis = {0};
        for (final String u : PINGS) {
            new Thread(() -> {
                double[] r = mesurerPing(g, u, stop);
                synchronized (verrou) {
                    if (r != null && res[0] == null) {
                        res[0] = r;
                        stop.set(true);
                    }
                    finis[0]++;
                    verrou.notifyAll();
                }
            }, "yiwalo-ping").start();
        }
        synchronized (verrou) {
            long limite = System.nanoTime() + 5000000000L;
            while (res[0] == null && finis[0] < PINGS.length && g == gen) {
                long reste = (limite - System.nanoTime()) / 1000000L;
                if (reste <= 0) break;
                try {
                    verrou.wait(Math.min(reste, 100L));
                } catch (InterruptedException e) {
                    break;
                }
            }
            stop.set(true);
            return res[0];
        }
    }

    /** 3 requêtes sur la même connexion : la 1re (TLS) sert de chauffe, les autres mesurent. */
    private double[] mesurerPing(int g, String u, AtomicBoolean stop) {
        List<Double> l = new ArrayList<>();
        double premier = -1;
        for (int i = 0; i < 3 && g == gen && !stop.get(); i++) {
            HttpURLConnection c = null;
            try {
                long t0 = System.nanoTime();
                c = ouvrir(u, 4500, 4500);
                int code = c.getResponseCode();
                if (code != 204 && code != 200) {
                    c.disconnect();
                    break;
                }
                try {
                    InputStream in = c.getInputStream();
                    byte[] b = new byte[512];
                    while (in.read(b) > 0) { }
                    in.close();
                } catch (Exception ignore) {
                    // pas de corps
                }
                double ms = (System.nanoTime() - t0) / 1e6;
                if (i == 0) premier = ms; else l.add(ms);
            } catch (Exception e) {
                if (c != null) c.disconnect();
                break;
            }
        }
        if (l.isEmpty() && premier > 0) l.add(premier);
        if (l.isEmpty()) return null;
        double somme = 0, jit = 0;
        for (int i = 0; i < l.size(); i++) {
            somme += l.get(i);
            if (i > 0) jit += Math.abs(l.get(i) - l.get(i - 1));
        }
        return new double[]{somme / l.size(), l.size() > 1 ? jit / (l.size() - 1) : 0};
    }

    private void telecharger(int g, int idx, AtomicLong total, AtomicLong fin, long plafond) {
        byte[] b = new byte[65536];
        int s = idx % SOURCES.length;
        int echecs = 0;
        // Chaque fil démarre sur une source différente et bascule seul si elle ne répond pas.
        while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                && echecs < SOURCES.length * 2) {
            HttpURLConnection c = null;
            long recu = 0;
            try {
                c = ouvrir(SOURCES[s]);
                InputStream in = c.getInputStream();
                int n;
                while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                        && (n = in.read(b)) > 0) {
                    total.addAndGet(n);
                    recu += n;
                }
            } catch (Exception e) {
                erreurDl = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                if (c != null) c.disconnect();
            }
            if (recu == 0) {
                echecs++;
                s = (s + 1) % SOURCES.length;
            }
        }
    }

    private static long totalA(ArrayList<long[]> ech, long t) {
        for (int i = ech.size() - 1; i >= 0; i--) {
            if (ech.get(i)[0] <= t) return ech.get(i)[1];
        }
        return 0;
    }

    /** Envoi : POST de données aléatoires (incompressibles), en direct ou à travers le moteur. */
    private void envoyer(int g, int idx, AtomicLong total, AtomicLong fin, long plafond,
                         boolean viaTunnel) {
        final byte[] b = new byte[65536];
        new java.util.Random(idx + 1).nextBytes(b);
        final long taille = 10000000L;
        int s = idx % UP_SRC.length;
        int echecs = 0;
        while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                && echecs < UP_SRC.length * 2) {
            Socket so = null;
            long envoye = 0;
            try {
                String hote = UP_SRC[s][0];
                int port = Integer.parseInt(UP_SRC[s][2]);
                if (viaTunnel) {
                    so = ouvrirSocks(hote, port);
                } else {
                    so = new Socket();
                    so.connect(new InetSocketAddress(hote, port), cnxMs());
                }
                if (port == 443) {
                    SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(so, hote, port, true);
                    so = ssl;
                    ssl.setSoTimeout(8000);
                    ssl.startHandshake();
                }
                so.setSoTimeout(8000);
                try { so.setSendBufferSize(32768); } catch (Exception ignore) { }
                OutputStream o = so.getOutputStream();
                o.write(("POST " + UP_SRC[s][1] + " HTTP/1.1\r\nHost: " + hote + "\r\n"
                        + "Content-Type: application/octet-stream\r\nContent-Length: " + taille
                        + "\r\nConnection: close\r\nUser-Agent: Mozilla/5.0\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                while (g == gen && System.nanoTime() < fin.get() && total.get() < plafond
                        && envoye < taille) {
                    int n = (int) Math.min(b.length, taille - envoye);
                    o.write(b, 0, n);
                    o.flush();
                    total.addAndGet(n);
                    envoye += n;
                }
            } catch (Exception e) {
                erreurUl = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                if (so != null) try { so.close(); } catch (Exception ignore) { }
            }
            if (envoye == 0) {
                echecs++;
                s = (s + 1) % UP_SRC.length;
            }
        }
    }

    /** Mesure le débit descendant (envoi = false) ou montant (envoi = true), en Mb/s. */
    private double flux(final int g, final boolean envoi) throws InterruptedException {
        final int nb = envoi ? 4 : 8;
        final long maxNs = 6000000000L;
        final long minNs = 2400000000L;
        final long plafond = envoi ? 60000000L : 100000000L;
        final AtomicLong total = new AtomicLong();
        final boolean viaTunnel = tunnel;
        final long t0 = System.nanoTime();
        final AtomicLong fin = new AtomicLong(t0 + maxNs);
        Thread[] ts = new Thread[nb];
        for (int i = 0; i < nb; i++) {
            final int idx = i;
            ts[i] = new Thread(() -> {
                if (envoi) envoyer(g, idx, total, fin, plafond, viaTunnel);
                else if (viaTunnel) telechargerSocks(g, idx, total, fin, plafond);
                else telecharger(g, idx, total, fin, plafond);
            }, "yiwalo-vitesse-" + i);
            ts[i].start();
        }
        final long chauffe = 800000000L;
        final long fenetre = 700000000L;
        ArrayList<long[]> ech = new ArrayList<>();
        ech.add(new long[]{t0, 0});
        long now = t0;
        while (g == gen) {
            Thread.sleep(100);
            now = System.nanoTime();
            long cur = total.get();
            ech.add(new long[]{now, cur});
            long ref = totalA(ech, now - 400000000L);
            long tRef = Math.max(t0, now - 400000000L);
            echantillon(g, (cur - ref) * 8.0 / 1e6 / Math.max(0.05, (now - tRef) / 1e9), envoi);
            boolean vivant = false;
            for (Thread t : ts) if (t.isAlive()) vivant = true;
            if (!vivant || now >= fin.get()) break;
            // arrêt anticipé : deux fenêtres consécutives quasi identiques
            if (now - t0 >= minNs) {
                long c1 = totalA(ech, now - fenetre);
                long a = cur - c1;
                long b = c1 - totalA(ech, now - 2 * fenetre);
                long m = Math.max(a, b);
                if (m > 0 && Math.abs(a - b) <= 0.10 * m) {
                    fin.set(0);
                    break;
                }
            }
        }
        fin.set(0);
        long cur = total.get();
        if (cur <= 0) return 0;
        long dep = Math.max(t0 + chauffe, now - 3000000000L);
        if (now - dep < 500000000L) dep = t0;
        long vDep = totalA(ech, dep);
        double sec = Math.max(0.2, (now - dep) / 1e9);
        return (cur - vDep) * 8.0 / 1e6 / sec;
    }

    private void echantillon(final int g, final double v, final boolean envoi) {
        lisse = lisse * 0.5 + v * 0.5;
        final double val = lisse;
        h.post(() -> {
            if (g != gen) return;
            jauge.definir(val);
            valeur(envoi ? vUp : vDown, fmt(val), "Mb/s", Ui.TEXTE);
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        majConnexion();
    }

    @Override protected void onDetachedFromWindow() {
        gen++;
        arreterMoteur();
        super.onDetachedFromWindow();
    }

    /** Compteur circulaire : arc dégradé lumineux, graduations qui s’allument et aiguille. */
    private final class Jauge extends View {
        private final double[] marques = {0, 1, 5, 10, 20, 50, 100, 250};
        private final Paint fondArc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint anneau = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcVal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint traitFin = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint etiquette = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pleine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bague = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disque = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grosTexte = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint petitTexte = new Paint(Paint.ANTI_ALIAS_FLAG);
        private double cible, aff;
        private double texteFixe = -1;
        private String libelle = "";
        private boolean modeEnvoi;
        private int teinte = Ui.VERT;
        private android.graphics.Shader degrade;
        private android.graphics.Shader lueur;
        private float dCx = -1, dCy = -1, dR = -1;

        Jauge(Context c) {
            super(c);
            fondArc.setStyle(Paint.Style.STROKE);
            fondArc.setStrokeCap(Paint.Cap.ROUND);
            fondArc.setStrokeWidth(Ui.dp(c, 10));
            fondArc.setColor(0xFF263149);
            anneau.setStyle(Paint.Style.STROKE);
            anneau.setStrokeWidth(Ui.dp(c, 1));
            anneau.setColor(Ui.BORD);
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeCap(Paint.Cap.ROUND);
            arcVal.setStyle(Paint.Style.STROKE);
            arcVal.setStrokeCap(Paint.Cap.ROUND);
            arcVal.setStrokeWidth(Ui.dp(c, 10));
            trait.setStrokeWidth(Ui.dp(c, 2));
            trait.setStrokeCap(Paint.Cap.ROUND);
            traitFin.setStrokeWidth(Ui.dp(c, 1));
            etiquette.setTextSize(Ui.dp(c, 10));
            etiquette.setTextAlign(Paint.Align.CENTER);
            bague.setStyle(Paint.Style.STROKE);
            bague.setStrokeWidth(Ui.dp(c, 2));
            bague.setColor(CYAN);
            grosTexte.setColor(Ui.TEXTE);
            grosTexte.setFakeBoldText(true);
            grosTexte.setTextAlign(Paint.Align.CENTER);
            petitTexte.setColor(CYAN);
            petitTexte.setFakeBoldText(true);
            petitTexte.setLetterSpacing(0.1f);
            petitTexte.setTextSize(Ui.dp(c, 12));
            petitTexte.setTextAlign(Paint.Align.CENTER);
        }

        void definir(double v) {
            cible = Math.max(0, v);
            invalidate();
        }

        /** Étape en cours : libellé sous le compteur, couleur du moyeu, dégradé de l'arc. */
        void phase(String l, int c) {
            libelle = l;
            teinte = c;
            boolean e = "Envoi".equals(l);
            if (e != modeEnvoi) {
                modeEnvoi = e;
                degrade = null;
            }
            invalidate();
        }

        /** Garde la valeur affichée au centre (résultat) pendant que l'aiguille retombe à 0. */
        void figer(double v) {
            texteFixe = v;
            invalidate();
        }

        private double fraction(double v) {
            if (v <= 0) return 0;
            int n = marques.length - 1;
            if (v >= marques[n]) return 1;
            for (int i = 0; i < n; i++) {
                if (v <= marques[i + 1]) {
                    return (i + (v - marques[i]) / (marques[i + 1] - marques[i])) / n;
                }
            }
            return 1;
        }

        private void majShaders(float cx, float cy, float r) {
            if (degrade != null && cx == dCx && cy == dCy && r == dR) return;
            // l'arc va de 135° (t = 0,375) à 45° (t = 0,125) en passant par t = 0
            // téléchargement : cyan > bleu > violet > rose ; envoi : rose > violet > bleu > cyan
            int c0 = modeEnvoi ? Ui.BLEU : Ui.VIOLET;
            int c125 = modeEnvoi ? CYAN : Ui.ROSE;
            int c375 = modeEnvoi ? Ui.ROSE : CYAN;
            int c750 = modeEnvoi ? Ui.VIOLET : Ui.BLEU;
            degrade = new android.graphics.SweepGradient(cx, cy,
                    new int[]{c0, c125, c375, c750, c0},
                    new float[]{0f, 0.125f, 0.375f, 0.75f, 1f});
            lueur = new android.graphics.RadialGradient(cx, cy, r,
                    0x383B82F6, 0x003B82F6, android.graphics.Shader.TileMode.CLAMP);
            disque.setShader(lueur);
            dCx = cx;
            dCy = cy;
            dR = r;
        }

        @Override protected void onDraw(Canvas cv) {
            aff += (cible - aff) * 0.2;
            if (Math.abs(cible - aff) < 0.02) aff = cible; else postInvalidateOnAnimation();

            float w = getWidth();
            float hh = getHeight();
            float haut = Ui.dp(ctx, 32);
            float r = Math.min(w / 2f - Ui.dp(ctx, 32), (hh - haut - Ui.dp(ctx, 6)) / 1.85f);
            if (r < Ui.dp(ctx, 30)) r = Ui.dp(ctx, 30);
            float cx = w / 2f;
            float cy = haut + r;
            RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);
            majShaders(cx, cy, r);

            // halo bleuté au centre + anneau fin intérieur
            cv.drawCircle(cx, cy, r, disque);
            float ri = r - Ui.dp(ctx, 16);
            cv.drawArc(new RectF(cx - ri, cy - ri, cx + ri, cy + ri), 135, 270, false, anneau);

            cv.drawArc(rect, 135, 270, false, fondArc);
            double f = fraction(aff);
            if (f > 0) {
                float sweep = (float) (270 * f);
                halo.setShader(degrade);
                halo.setStrokeWidth(Ui.dp(ctx, 26));
                halo.setAlpha(22);
                cv.drawArc(rect, 135, sweep, false, halo);
                halo.setStrokeWidth(Ui.dp(ctx, 18));
                halo.setAlpha(42);
                cv.drawArc(rect, 135, sweep, false, halo);
                arcVal.setShader(degrade);
                cv.drawArc(rect, 135, sweep, false, arcVal);
                // point lumineux au bout de l'arc
                double ae = Math.toRadians(135 + 270 * f);
                float ex = cx + (float) Math.cos(ae) * r;
                float ey = cy + (float) Math.sin(ae) * r;
                pleine.setStyle(Paint.Style.FILL);
                pleine.setColor(0x55FFFFFF);
                cv.drawCircle(ex, ey, Ui.dp(ctx, 10), pleine);
                pleine.setColor(0xFFFFFFFF);
                cv.drawCircle(ex, ey, Ui.dp(ctx, 5), pleine);
            }

            int n = marques.length - 1;
            for (int i = 0; i <= n; i++) {
                boolean allume = f > 0 && fraction(marques[i]) <= f + 1e-6;
                trait.setColor(allume ? Ui.TEXTE : Ui.DISCRET);
                etiquette.setColor(allume ? Ui.TEXTE : Ui.DISCRET);
                double a = Math.toRadians(135 + 270.0 * i / n);
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                cv.drawLine(cx + cos * (r + Ui.dp(ctx, 9)), cy + sin * (r + Ui.dp(ctx, 9)),
                        cx + cos * (r + Ui.dp(ctx, 14)), cy + sin * (r + Ui.dp(ctx, 14)), trait);
                String t = marques[i] == (long) marques[i]
                        ? String.valueOf((long) marques[i]) : String.valueOf(marques[i]);
                cv.drawText(t, cx + cos * (r + Ui.dp(ctx, 25)),
                        cy + sin * (r + Ui.dp(ctx, 25)) + Ui.dp(ctx, 4), etiquette);
                if (i < n) {
                    for (int k = 1; k < 4; k++) {
                        double pos = (i + k / 4.0) / n;
                        traitFin.setColor(f > 0 && pos <= f ? 0xFF7C8BA8 : Ui.BORD);
                        double b = Math.toRadians(135 + 270.0 * pos);
                        float c2 = (float) Math.cos(b);
                        float s2 = (float) Math.sin(b);
                        cv.drawLine(cx + c2 * (r + Ui.dp(ctx, 9)), cy + s2 * (r + Ui.dp(ctx, 9)),
                                cx + c2 * (r + Ui.dp(ctx, 12)), cy + s2 * (r + Ui.dp(ctx, 12)),
                                traitFin);
                    }
                }
            }

            double ang = Math.toRadians(135 + 270 * f);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            float longueur = r - Ui.dp(ctx, 8);
            float px = -sin * Ui.dp(ctx, 3);
            float py = cos * Ui.dp(ctx, 3);
            Path aiguille = new Path();
            aiguille.moveTo(cx + cos * longueur, cy + sin * longueur);
            aiguille.lineTo(cx + px, cy + py);
            aiguille.lineTo(cx - cos * Ui.dp(ctx, 12), cy - sin * Ui.dp(ctx, 12));
            aiguille.lineTo(cx - px, cy - py);
            aiguille.close();
            pleine.setStyle(Paint.Style.FILL);
            pleine.setColor(Ui.TEXTE);
            cv.drawPath(aiguille, pleine);
            pleine.setColor(0xFF0F1626);
            cv.drawCircle(cx, cy, Ui.dp(ctx, 11), pleine);
            cv.drawCircle(cx, cy, Ui.dp(ctx, 11), bague);
            pleine.setColor(teinte);
            cv.drawCircle(cx, cy, Ui.dp(ctx, 4), pleine);

            float taille = Math.max(Ui.dp(ctx, 22), Math.min(Ui.dp(ctx, 46), r * 0.52f));
            grosTexte.setTextSize(taille);
            float yVal = cy + r * 0.5f + taille * 0.3f;
            cv.drawText(fmt(texteFixe >= 0 ? texteFixe : aff), cx, yVal, grosTexte);
            petitTexte.setColor(modeEnvoi ? Ui.VIOLET : CYAN);
            cv.drawText(libelle.isEmpty() ? "Mb/s"
                    : "Mb/s  ·  " + libelle.toUpperCase(Locale.getDefault()),
                    cx, yVal + Ui.dp(ctx, 17), petitTexte);
        }
    }

    /** Trois segments (latence, débit, résultat) qui se remplissent au fil du test. */
    private final class Etapes extends View {
        private final String[] noms = {"Latence", "Réception", "Envoi", "Résultat"};
        private final Paint barre = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int etape;

        Etapes(Context c) {
            super(c);
            txt.setTextSize(Ui.dp(c, 11));
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void etape(int e) {
            etape = e;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float gap = Ui.dp(ctx, 8);
            float bw = (getWidth() - 3 * gap) / 4f;
            float bh = Ui.dp(ctx, 4);
            boolean actif = false;
            for (int i = 0; i < 4; i++) {
                float x = i * (bw + gap);
                int c;
                int ct;
                boolean courant = etape == i + 1 && etape < 4;
                if (etape >= 4) {
                    c = Ui.VERT;
                    ct = Ui.TEXTE;
                } else if (etape > i + 1) {
                    c = Ui.BLEU;
                    ct = Ui.TEXTE;
                } else if (courant) {
                    c = CYAN;
                    ct = Ui.TEXTE;
                    actif = true;
                } else {
                    c = Ui.BORD;
                    ct = Ui.DISCRET;
                }
                barre.setColor(c);
                if (courant) {
                    barre.setAlpha((int) (175 + 80 * Math.sin(SystemClock.uptimeMillis() / 200.0)));
                }
                cv.drawRoundRect(new RectF(x, 0, x + bw, bh), bh / 2, bh / 2, barre);
                txt.setColor(ct);
                cv.drawText(noms[i], x + bw / 2, bh + Ui.dp(ctx, 16), txt);
            }
            if (actif) postInvalidateOnAnimation();
        }
    }
}
