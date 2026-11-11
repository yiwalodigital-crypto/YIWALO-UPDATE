package com.yiwalo.tunnel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;

final class PingView extends LinearLayout {
    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean actif;
    private volatile int gen;
    private boolean debitEnCours;
    private final EditText champ;
    private final TextView bouton, vMs, vStats, vEtat, vDebit;

    PingView(final Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "Ping et débit", retour));

        LinearLayout c1 = Ui.carte(ctx);
        int p = Ui.dp(ctx, 16);
        c1.setPadding(p, p, p, p);
        c1.addView(Ui.texte(ctx, "SERVEUR À TESTER", 12f, Ui.DISCRET, true));
        champ = Ui.champ(ctx, "Adresse ou IP (ex. 1.1.1.1)", "1.1.1.1");
        c1.addView(champ);
        LinearLayout puces = new LinearLayout(ctx);
        puces.setOrientation(HORIZONTAL);
        puces.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        puces.addView(puce("Cloudflare", "1.1.1.1"));
        puces.addView(puce("Google", "8.8.8.8"));
        puces.addView(puce("google.com", "google.com"));
        c1.addView(puces);
        bouton = Ui.bouton(ctx, "Lancer le ping", Ui.BLEU, this::basculer);
        c1.addView(bouton);
        addView(c1);

        LinearLayout c2 = Ui.carte(ctx);
        c2.setPadding(p, p, p, p);
        c2.setGravity(Gravity.CENTER_HORIZONTAL);
        vMs = Ui.texte(ctx, "—", 40f, Ui.TEXTE, true);
        vMs.setGravity(Gravity.CENTER);
        c2.addView(vMs);
        vEtat = Ui.texte(ctx, "Ping TCP : mesure le temps de connexion au serveur.", 13f,
                Ui.DISCRET, false);
        vEtat.setGravity(Gravity.CENTER);
        c2.addView(vEtat);
        vStats = Ui.texte(ctx, "", 14f, Ui.TEXTE, false);
        vStats.setGravity(Gravity.CENTER);
        vStats.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        c2.addView(vStats);
        addView(c2);

        addView(Ui.section(ctx, "TEST DE DÉBIT"));
        LinearLayout c3 = Ui.carte(ctx);
        c3.setPadding(p, p, p, p);
        c3.addView(Ui.texte(ctx, "Télécharge 10 Mo (10 s maximum). Connecté, il mesure le débit à travers le tunnel.",
                13f, Ui.DISCRET, false));
        vDebit = Ui.texte(ctx, "", 18f, Ui.TEXTE, true);
        vDebit.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        c3.addView(vDebit);
        c3.addView(Ui.bouton(ctx, "Mesurer le débit", Ui.VERT, this::testerDebit));
        addView(c3);
    }

    private TextView puce(String nom, final String hote) {
        TextView t = Ui.texte(ctx, nom, 13f, Ui.TEXTE, false);
        t.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
        t.setBackground(Ui.fond(ctx, Ui.FOND2, 20, Ui.BORD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(ctx, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> champ.setText(hote));
        return t;
    }

    private void basculer() {
        if (actif) {
            actif = false;
            gen++;
            fin("Arrêté");
            return;
        }
        final String saisie = champ.getText().toString().trim();
        if (saisie.isEmpty()) {
            T.toast(ctx, "Entre une adresse", Toast.LENGTH_SHORT).show();
            return;
        }
        actif = true;
        final int g = ++gen;
        bouton.setText("Arrêter");
        bouton.setBackground(Ui.fond(ctx, Ui.ROUGE, 12, 0));
        vEtat.setText("Résolution…");
        vStats.setText("");
        new Thread(() -> {
            String hote0 = saisie;
            int port0 = 443;
            int i = saisie.lastIndexOf(':');
            if (i > 0 && saisie.indexOf(':') == i) {
                try {
                    port0 = Integer.parseInt(saisie.substring(i + 1));
                    hote0 = saisie.substring(0, i);
                } catch (NumberFormatException ignore) {
                    // pas de port
                }
            }
            final String hote = hote0;
            final int port = port0;
            final InetAddress cible;
            try {
                cible = InetAddress.getByName(hote);
            } catch (Exception e) {
                h.post(() -> { if (g == gen) fin("Hôte introuvable"); });
                return;
            }
            if (g != gen) return;
            h.post(() -> { if (g == gen) vEtat.setText("Ping TCP vers " + cible.getHostAddress() + ":" + port); });
            int envoyes = 0, recus = 0;
            double somme = 0, min = 1e9, max = 0, jit = 0, prev = -1;
            for (int k = 0; k < 10 && actif && g == gen; k++) {
                envoyes++;
                double ms = -1;
                Socket s = new Socket();
                try {
                    long t = System.nanoTime();
                    s.connect(new InetSocketAddress(cible, port), 2500);
                    ms = (System.nanoTime() - t) / 1e6;
                } catch (Exception e) {
                    ms = -1;
                } finally {
                    try { s.close(); } catch (Exception ignore) { }
                }
                if (ms >= 0) {
                    recus++;
                    somme += ms;
                    min = Math.min(min, ms);
                    max = Math.max(max, ms);
                    if (prev >= 0) jit += Math.abs(ms - prev);
                    prev = ms;
                }
                final double fms = ms, fmin = min, fmax = max;
                final double fav = recus > 0 ? somme / recus : 0;
                final double fjt = recus > 1 ? jit / (recus - 1) : 0;
                final int fe = envoyes, fr = recus;
                h.post(() -> { if (g == gen) maj(fms, fe, fr, fmin, fav, fmax, fjt); });
                try { Thread.sleep(700); } catch (InterruptedException ignore) { break; }
            }
            h.post(() -> { if (g == gen) fin(null); });
        }, "yiwalo-ping").start();
    }

    private void maj(double ms, int env, int rec, double mn, double av, double mx, double jt) {
        if (ms < 0) {
            vMs.setTextSize(22f);
            vMs.setText("Délai dépassé");
            vMs.setTextColor(Ui.ROUGE);
        } else {
            vMs.setTextSize(40f);
            vMs.setText(String.format(Locale.getDefault(), "%.0f ms", ms));
            vMs.setTextColor(ms < 80 ? Ui.VERT : ms < 200 ? Ui.ORANGE : Ui.ROUGE);
        }
        int perte = env == 0 ? 0 : Math.round(100f * (env - rec) / env);
        if (rec > 0) {
            vStats.setText(String.format(Locale.getDefault(),
                    "Min %.0f · Moy %.0f · Max %.0f ms\nGigue %.0f ms · Perte %d %% (%d/%d)",
                    mn, av, mx, jt, perte, rec, env));
        } else {
            vStats.setText("Perte 100 % (0/" + env + ")");
        }
    }

    private void fin(String message) {
        actif = false;
        bouton.setText("Lancer le ping");
        bouton.setBackground(Ui.fond(ctx, Ui.BLEU, 12, 0));
        if (message != null) vEtat.setText(message);
    }

    private void testerDebit() {
        if (debitEnCours) return;
        debitEnCours = true;
        vDebit.setText("Mesure en cours…");
        new Thread(() -> {
            String res;
            try {
                URL u = new URL("https://speed.cloudflare.com/__down?bytes=10000000");
                long t0 = System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.connect();
                if (c.getResponseCode() != 200) throw new java.io.IOException("http");
                long lat = (System.nanoTime() - t0) / 1000000L;
                java.io.InputStream in = c.getInputStream();
                byte[] b = new byte[16384];
                long total = 0;
                long t1 = System.nanoTime();
                int n;
                while ((n = in.read(b)) > 0) {
                    total += n;
                    if (System.nanoTime() - t1 > 10000000000L) break;
                }
                in.close();
                c.disconnect();
                double sec = (System.nanoTime() - t1) / 1e9;
                double mbps = total * 8 / 1e6 / sec;
                res = String.format(Locale.US, "%.1f Mb/s  ·  Latence %d ms", mbps, lat);
            } catch (Exception e) {
                res = "Échec du test (réseau indisponible)";
            }
            final String r = res;
            h.post(() -> {
                vDebit.setText(r);
                debitEnCours = false;
            });
        }, "yiwalo-debit").start();
    }

    @Override protected void onDetachedFromWindow() {
        actif = false;
        gen++;
        super.onDetachedFromWindow();
    }
}
