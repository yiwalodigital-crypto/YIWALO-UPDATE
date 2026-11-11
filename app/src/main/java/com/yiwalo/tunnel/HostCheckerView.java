package com.yiwalo.tunnel;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Vérificateur d'hôte : champ URL orange, OPTIONS (HTTPS/HTTP), case Proxy (via le tunnel),
 * menu Header / GET / TCP-TLS et gros bouton Vérifier. Teste DNS, TCP, TLS et HTTP(S)
 * sur chaque réseau disponible.
 */
final class HostCheckerView extends LinearLayout {

    private static final int AMBRE = 0xFFFFB300;
    private static final int AMBRE_FIN = 0xFFE07B00;
    private static final int SUR_AMBRE = 0xFF1A1200;

    private interface Choix { void on(int i); }

    private static final class Mesure {
        String reseau = "";
        int niveau;                 // 0 = bloqué, 1 = partiel, 2 = fonctionne
        String statut = "Bloqué";
        int couleur = Ui.ROUGE;
        String ip = "";
        String cause = "";          // explication courte si problème
        long dnsMs = -1, tlsMs = -1, httpMs = -1;
        int code;                   // code HTTP, 0 si aucun
        boolean tls;                // vrai si le test visait HTTPS
        long tcpMs = Long.MAX_VALUE;
    }

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean attache = true;
    private boolean enCours;

    private boolean https = true;   // OPTIONS : HTTPS (443) ou HTTP (80)
    private int methode;            // 0 = Header (HEAD), 1 = GET, 2 = TCP / TLS seulement

    private final EditText champ;
    private final CheckBox proxy;
    private final TextView bouton, vStatut, vDiag;
    private final LinearLayout carteResultat, blocReseaux;

    HostCheckerView(final Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        setOrientation(VERTICAL);
        addView(entete(retour));

        // Ligne 1 : champ URL (bord orange, libellé intégré) + OPTIONS
        LinearLayout l1 = new LinearLayout(ctx);
        l1.setOrientation(HORIZONTAL);
        l1.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout boite = new LinearLayout(ctx);
        boite.setOrientation(VERTICAL);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(Ui.FOND);
        gb.setCornerRadius(Ui.dp(ctx, 14));
        gb.setStroke(Ui.dp(ctx, 2), AMBRE);
        boite.setBackground(gb);
        boite.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        boite.addView(Ui.texte(ctx, "URL (ex : Facebook.Com)", 13f, AMBRE, true));
        champ = new EditText(ctx);
        champ.setSingleLine(true);
        champ.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        champ.setTextColor(Ui.TEXTE);
        champ.setTextSize(17f);
        champ.setBackground(null);
        champ.setPadding(0, Ui.dp(ctx, 2), 0, 0);
        champ.setImeOptions(EditorInfo.IME_ACTION_DONE);
        champ.setOnEditorActionListener((v, a, e) -> {
            if (a == EditorInfo.IME_ACTION_DONE) { verifier(); return true; }
            return false;
        });
        boite.addView(champ, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l1.addView(boite, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView vOptions = Ui.texte(ctx, "OPTIONS", 16f, Ui.TEXTE, false);
        l1.addView(deroulant(vOptions, new String[]{"HTTPS (443)", "HTTP (80)"}, false,
                i -> https = i == 0));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.topMargin = Ui.dp(ctx, 6);
        addView(l1, lp1);

        // Ligne 2 : case Proxy + menu Header
        LinearLayout l2 = new LinearLayout(ctx);
        l2.setOrientation(HORIZONTAL);
        l2.setGravity(Gravity.CENTER_VERTICAL);
        proxy = new CheckBox(ctx);
        proxy.setText(T.t("Proxy"));
        proxy.setTextSize(16f);
        proxy.setTextColor(Ui.DISCRET);
        proxy.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{AMBRE, Ui.TEXTE}));
        proxy.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
        l2.addView(proxy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vMethode = Ui.texte(ctx, "Header", 17f, Ui.TEXTE, false);
        l2.addView(deroulant(vMethode, new String[]{"Header", "GET", "TCP / TLS"}, true,
                i -> methode = i));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = Ui.dp(ctx, 6);
        addView(l2, lp2);

        // Gros bouton arrondi dégradé
        bouton = Ui.texte(ctx, "Vérifier", 20f, SUR_AMBRE, true);
        bouton.setGravity(Gravity.CENTER);
        bouton.setPadding(0, Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16));
        GradientDrawable gp = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AMBRE, AMBRE_FIN});
        gp.setCornerRadius(Ui.dp(ctx, 40));
        bouton.setBackground(gp);
        bouton.setOnClickListener(v -> verifier());
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lb.topMargin = Ui.dp(ctx, 12);
        lb.bottomMargin = Ui.dp(ctx, 16);
        addView(bouton, lb);

        // Résultat (caché tant qu'on n'a pas vérifié)
        int p = Ui.dp(ctx, 14);
        carteResultat = Ui.carte(ctx);
        carteResultat.setVisibility(GONE);
        LinearLayout tete = new LinearLayout(ctx);
        tete.setOrientation(VERTICAL);
        tete.setPadding(p, Ui.dp(ctx, 12), p, Ui.dp(ctx, 12));
        vStatut = Ui.texte(ctx, "", 30f, Ui.TEXTE, true);
        tete.addView(vStatut);
        vDiag = Ui.texte(ctx, "", 14f, Ui.DISCRET, false);
        tete.addView(vDiag);
        carteResultat.addView(tete);
        View sep = new View(ctx);
        sep.setBackgroundColor(Ui.BORD);
        carteResultat.addView(sep, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        blocReseaux = new LinearLayout(ctx);
        blocReseaux.setOrientation(VERTICAL);
        blocReseaux.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8));
        carteResultat.addView(blocReseaux);
        addView(carteResultat);
    }

    /** Barre du haut : retour, « Vérificateur d'hôte » et menu ⋮ (Coller / Effacer). */
    private View entete(final Runnable retour) {
        LinearLayout e = new LinearLayout(ctx);
        e.setOrientation(HORIZONTAL);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 12));
        ImageView b = new ImageView(ctx);
        b.setImageResource(R.drawable.ic_retour);
        b.setColorFilter(Ui.TEXTE);
        b.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)));
        b.setOnClickListener(v -> retour.run());
        e.addView(b);
        TextView t = Ui.texte(ctx, "Vérificateur d'hôte", 22f, Ui.TEXTE, true);
        t.setPadding(Ui.dp(ctx, 6), 0, 0, 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        e.addView(t);
        final TextView plus = Ui.texte(ctx, "\u22EE", 26f, Ui.TEXTE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)));
        plus.setOnClickListener(v -> menuPlus(plus));
        e.addView(plus);
        return e;
    }

    private void menuPlus(View ancre) {
        PopupMenu pm = new PopupMenu(ctx, ancre);
        pm.getMenu().add(0, 0, 0, T.t("Coller"));
        pm.getMenu().add(0, 1, 1, T.t("Effacer"));
        pm.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 0) {
                ClipboardManager cm =
                        (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
                    if (cs != null) {
                        champ.setText(cs.toString().trim());
                        champ.setSelection(champ.length());
                    }
                }
            } else {
                champ.setText("");
                carteResultat.setVisibility(GONE);
            }
            return true;
        });
        pm.show();
    }

    /** Libellé + flèche ⌄ qui ouvre un petit menu de choix. */
    private View deroulant(final TextView valeur, final String[] choix, final boolean majTexte,
                           final Choix cb) {
        final int[] sel = {0};
        final LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10));
        r.addView(valeur);
        ImageView fl = new ImageView(ctx);
        fl.setImageResource(R.drawable.ic_chevron);
        fl.setRotation(90f);
        fl.setColorFilter(Ui.TEXTE);
        r.addView(fl, new LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)));
        r.setClickable(true);
        r.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(ctx, r);
            for (int i = 0; i < choix.length; i++) {
                MenuItem it = pm.getMenu().add(0, i, i, T.t(choix[i]));
                it.setCheckable(true);
                it.setChecked(i == sel[0]);
            }
            pm.getMenu().setGroupCheckable(0, true, true);
            pm.setOnMenuItemClickListener(it -> {
                sel[0] = it.getItemId();
                if (majTexte) valeur.setText(T.t(choix[sel[0]]));
                cb.on(sel[0]);
                return true;
            });
            pm.show();
        });
        return r;
    }

    private void verifier() {
        if (enCours) return;
        String s = champ.getText().toString().trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.isEmpty() || s.contains(" ")) {
            T.toast(ctx, "Entre un hôte valide", Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager im =
                (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(champ.getWindowToken(), 0);

        final int portDef = https ? 443 : 80;
        final int mode = methode;
        final boolean viaTunnel = proxy.isChecked();

        enCours = true;
        bouton.setText(T.t("Vérification…"));
        bouton.setAlpha(0.7f);
        carteResultat.setVisibility(VISIBLE);
        vStatut.setText("…");
        vStatut.setTextColor(Ui.DISCRET);
        vDiag.setText(s);
        blocReseaux.removeAllViews();
        final String cible = s;
        new Thread(() -> tester(cible, portDef, mode, viaTunnel), "yiwalo-host-check").start();
    }

    private void tester(String saisie, int portDef, final int mode, boolean viaTunnel) {
        String hote = saisie;
        int port = portDef;
        int i = saisie.lastIndexOf(':');
        if (i > 0 && saisie.indexOf(':') == i) {
            try {
                port = Integer.parseInt(saisie.substring(i + 1));
                hote = saisie.substring(0, i);
            } catch (NumberFormatException ignore) {
                // pas de port
            }
        }
        final String h0 = hote;
        final int p0 = port;
        final boolean complet = port == 443 || port == 80;

        List<TestConfigsView.Reseau> reseaux = new ArrayList<>();
        if (!viaTunnel) {
            try {
                reseaux = TestConfigsView.reseauxActifs(ctx);
            } catch (RuntimeException e) {
                reseaux = new ArrayList<>();
            }
        }
        final List<Mesure> toutes = new ArrayList<>();
        ExecutorService ex = Executors.newCachedThreadPool();
        try {
            List<Future<Mesure>> fs = new ArrayList<>();
            List<String> noms = new ArrayList<>();
            if (reseaux.isEmpty()) {
                final String nomDefaut = viaTunnel ? "Via le tunnel" : "Réseau par défaut";
                noms.add(nomDefaut);
                fs.add(ex.submit(() -> mesurer(null, nomDefaut, h0, p0, complet, mode)));
            } else {
                for (final TestConfigsView.Reseau r : reseaux) {
                    noms.add(r.nom);
                    fs.add(ex.submit(() -> mesurer(r.net, r.nom, h0, p0, complet, mode)));
                }
            }
            for (int k = 0; k < fs.size(); k++) {
                try {
                    toutes.add(fs.get(k).get(40, TimeUnit.SECONDS));
                } catch (Exception e) {
                    Mesure m = new Mesure();
                    m.reseau = noms.get(k);
                    m.statut = "Sans réponse";
                    m.cause = "Délai dépassé";
                    toutes.add(m);
                }
            }
        } finally {
            ex.shutdownNow();
        }

        Mesure meilleure = toutes.get(0);
        int ok = 0;
        for (Mesure m : toutes) {
            if (m.niveau > meilleure.niveau
                    || (m.niveau == meilleure.niveau && m.tcpMs < meilleure.tcpMs)) {
                meilleure = m;
            }
            if (m.niveau == 2) ok++;
        }
        int n = toutes.size();
        final String titre;
        final int couleur;
        String diag;
        if (ok == n) {
            titre = complet ? "Fonctionne" : "Port ouvert";
            couleur = Ui.VERT;
            diag = h0 + ":" + p0;
        } else if (ok > 0) {
            titre = "Fonctionne sur " + ok + "/" + n + " réseaux";
            couleur = Ui.ORANGE;
            diag = h0 + ":" + p0;
        } else {
            titre = meilleure.niveau == 1 ? "Partiel" : meilleure.statut;
            couleur = meilleure.niveau == 1 ? Ui.ORANGE : Ui.ROUGE;
            diag = meilleure.cause.isEmpty() ? h0 + ":" + p0 : meilleure.cause;
        }
        final String d = diag;

        h.post(() -> {
            enCours = false;
            if (!attache) return;
            bouton.setText(T.t("Vérifier"));
            bouton.setAlpha(1f);
            vStatut.setText(titre);
            vStatut.setTextColor(couleur);
            vDiag.setText(d);
            blocReseaux.removeAllViews();
            boolean plusieurs = toutes.size() > 1;
            for (Mesure m : toutes) blocMesure(m, plusieurs);
        });
    }

    private TextView valeurLigne(String s) {
        TextView t = Ui.texte(ctx, s, 16f, Ui.TEXTE, false);
        t.setGravity(Gravity.END);
        return t;
    }

    private void ajouterLigne(String label, String valeur, int couleurValeur) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
        TextView a = Ui.texte(ctx, label, 16f, Ui.TEXTE, false);
        a.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(a);
        TextView b = valeurLigne(valeur);
        b.setTextColor(couleurValeur);
        r.addView(b);
        blocReseaux.addView(r);
    }

    /** Lignes de résultat d'un réseau : adresse IP, DNS, TCP, TLS, réponse HTTP(S). */
    private void blocMesure(Mesure m, boolean avecReseau) {
        if (avecReseau) {
            LinearLayout haut = new LinearLayout(ctx);
            haut.setOrientation(HORIZONTAL);
            haut.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 2));
            TextView nom = Ui.texte(ctx, m.reseau, 14f, Ui.DISCRET, true);
            nom.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            haut.addView(nom);
            haut.addView(Ui.texte(ctx, m.statut, 14f, m.couleur, true));
            blocReseaux.addView(haut);
        }
        if (!m.ip.isEmpty()) ajouterLigne("Adresse IP", m.ip, Ui.TEXTE);
        if (m.dnsMs >= 0) ajouterLigne("Résolution DNS", m.dnsMs + " ms", Ui.TEXTE);
        if (m.tcpMs != Long.MAX_VALUE) ajouterLigne("Connexion TCP", m.tcpMs + " ms", Ui.TEXTE);
        if (m.code > 0) {
            ajouterLigne(m.tls ? "Réponse HTTPS" : "Réponse HTTP",
                    "HTTP " + m.code + " \u00B7 " + m.httpMs + " ms", Ui.TEXTE);
        }
        if (m.niveau < 2 && !m.cause.isEmpty()) ajouterLigne("Problème", m.cause, m.couleur);
    }

    /**
     * Test DNS + TCP + TLS + HTTP(S) sur un réseau précis (null = réseau par défaut / tunnel).
     * mode : 0 = requête HEAD (Header), 1 = GET, 2 = TCP/TLS seulement.
     */
    private Mesure mesurer(Network net, String nom, String hote, int port, boolean complet,
                           int mode) {
        Mesure m = new Mesure();
        m.reseau = nom;

        InetAddress adr;
        long t0 = System.nanoTime();
        try {
            InetAddress[] l = net != null ? net.getAllByName(hote) : InetAddress.getAllByName(hote);
            adr = l[0];
            for (InetAddress a : l) {
                if (a instanceof Inet4Address) { adr = a; break; }
            }
        } catch (Exception e) {
            m.statut = "Introuvable";
            m.cause = "Le DNS ne trouve pas cet hôte";
            return m;
        }
        m.dnsMs = (System.nanoTime() - t0) / 1000000L;
        m.ip = adr.getHostAddress();
        m.tls = port == 443;

        long t1 = System.nanoTime();
        Socket s = null;
        try {
            s = net != null ? net.getSocketFactory().createSocket() : new Socket();
            s.connect(new InetSocketAddress(adr, port), 5000);
        } catch (Exception e) {
            m.statut = "Bloqué";
            m.cause = "Port " + port + " sans réponse";
            return m;
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ignore) { }
            }
        }
        m.tcpMs = (System.nanoTime() - t1) / 1000000L;
        if (!complet || (mode == 2 && port != 443)) {
            m.niveau = 2;
            m.statut = "Ouvert";
            m.couleur = Ui.VERT;
            return m;
        }

        // TLS (uniquement sur 443)
        if (port == 443) {
            Socket brut = null;
            SSLSocket ssl = null;
            long t2 = System.nanoTime();
            try {
                brut = net != null ? net.getSocketFactory().createSocket() : new Socket();
                brut.connect(new InetSocketAddress(adr, port), 5000);
                brut.setSoTimeout(6000);
                ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(brut, hote, port, true);
                ssl.startHandshake();
                m.tlsMs = (System.nanoTime() - t2) / 1000000L;
            } catch (Exception e) {
                m.niveau = 1;
                m.statut = "Partiel";
                m.couleur = Ui.ORANGE;
                m.cause = "TLS coupé : filtrage SNI probable";
                return m;
            } finally {
                try { if (ssl != null) ssl.close(); } catch (Exception ignore) { }
                try { if (brut != null) brut.close(); } catch (Exception ignore) { }
            }
            if (mode == 2) {
                m.niveau = 2;
                m.statut = "Fonctionne";
                m.couleur = Ui.VERT;
                return m;
            }
        }

        // HTTP(S) : HEAD (Header) ou GET
        long t3 = System.nanoTime();
        HttpURLConnection c = null;
        try {
            URL u = new URL((port == 443 ? "https://" : "http://") + hote + "/");
            c = (HttpURLConnection) (net != null ? net.openConnection(u) : u.openConnection());
            c.setRequestMethod(mode == 1 ? "GET" : "HEAD");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) HostChecker");
            int code = c.getResponseCode();
            long http = (System.nanoTime() - t3) / 1000000L;
            m.code = code;
            m.httpMs = http;
            m.niveau = 2;
            m.statut = "Fonctionne";
            m.couleur = http > 2000 || code >= 500 ? Ui.ORANGE : Ui.VERT;
        } catch (Exception e) {
            m.niveau = 1;
            m.statut = "Partiel";
            m.couleur = Ui.ORANGE;
            m.cause = port == 443 ? "Pas de réponse HTTPS" : "Pas de réponse HTTP";
        } finally {
            if (c != null) c.disconnect();
        }
        return m;
    }

    @Override protected void onDetachedFromWindow() {
        attache = false;
        super.onDetachedFromWindow();
    }
}
