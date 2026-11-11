package com.yiwalo.tunnel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ConsommationView extends LinearLayout {
    private static final int TELECH = 0xFFF87171;
    private static final int ENVOI = 0xFFFACC15;

    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile int mode;
    private long t0;
    private long[] baseSess;
    private int tics;
    private volatile boolean releveEnCours;
    private ExecutorService exe;
    private final TextView sousTitre;
    private final LinearLayout carteAcces;
    private final TextView[] puces = new TextView[3];

    private final TextView sessTotal, sessRx, sessTx, sessDuree;
    private final TextView aujTotal, aujRx, aujTx, aujPctRx, aujPctTx;
    private final Donut donut;
    private final TextView[] hier, sem, mois, tot;
    private boolean permDemandee;

    private final Runnable tic = new Runnable() {
        @Override public void run() { maj(); h.postDelayed(this, 1000); }
    };

    ConsommationView(Context ctx, Runnable retour) {
        super(ctx);
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "Consommation de données", retour));
        mode = ctx.getSharedPreferences("yiwalo_reglages", Context.MODE_PRIVATE)
                .getInt("conso_mode", 0);

        sousTitre = Ui.texte(ctx, "", 13f, Ui.DISCRET, false);
        sousTitre.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), Ui.dp(ctx, 10));
        addView(sousTitre);

        // ---- Filtre : tout / mobile / Wi-Fi
        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(HORIZONTAL);
        chips.setPadding(0, 0, 0, Ui.dp(ctx, 10));
        String[] noms = {"Tout", "Mobile", "Wi-Fi"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.texte(ctx, noms[i], 14f, Ui.TEXTE, true);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, Ui.dp(ctx, 40), 1f);
            if (i > 0) cp.leftMargin = Ui.dp(ctx, 8);
            t.setLayoutParams(cp);
            t.setOnClickListener(v -> choisirMode(idx));
            puces[i] = t;
            chips.addView(t);
        }
        addView(chips);
        styleChips();

        // ---- Autorisation nécessaire pour la valeur réelle
        carteAcces = Ui.carte(ctx);
        remplir(ctx, carteAcces);
        carteAcces.addView(Ui.texte(ctx, "Voir la consommation de tout le téléphone", 16f,
                Ui.TEXTE, true));
        TextView expl = Ui.texte(ctx, "Pour afficher la consommation réelle (toutes les "
                + "applications, Wi-Fi et données mobiles, comme dans les paramètres Android), "
                + "autorise l’accès « Utilisation » pour Yiwalo, puis reviens ici.",
                13f, Ui.DISCRET, false);
        expl.setPadding(0, Ui.dp(ctx, 4), 0, 0);
        carteAcces.addView(expl);
        carteAcces.addView(Ui.bouton(ctx, "Autoriser l’accès", Ui.BLEU, this::ouvrirReglagesAcces));
        carteAcces.setVisibility(GONE);
        addView(carteAcces);

        // ---- Session actuelle
        LinearLayout sess = Ui.carte(ctx);
        remplir(ctx, sess);
        sess.addView(Ui.texte(ctx, "Session actuelle", 14f, Ui.TEXTE, false));
        sessTotal = Ui.texte(ctx, "0 o", 30f, Ui.TEXTE, true);
        sess.addView(sessTotal);
        LinearLayout ligne = new LinearLayout(ctx);
        ligne.setOrientation(HORIZONTAL);
        ligne.setPadding(0, Ui.dp(ctx, 14), 0, 0);
        sessRx = Ui.texte(ctx, "0 o", 17f, TELECH, true);
        sessDuree = Ui.texte(ctx, "--:--:--", 17f, Ui.TEXTE, true);
        sessTx = Ui.texte(ctx, "0 o", 17f, ENVOI, true);
        ligne.addView(colonne(ctx, "Téléchargement", sessRx, Gravity.START));
        ligne.addView(colonne(ctx, "Durée", sessDuree, Gravity.CENTER_HORIZONTAL));
        ligne.addView(colonne(ctx, "Envoi", sessTx, Gravity.END));
        sess.addView(ligne);
        addView(sess);

        // ---- Aujourd'hui avec anneau
        LinearLayout auj = Ui.carte(ctx);
        remplir(ctx, auj);
        auj.addView(Ui.texte(ctx, "Aujourd'hui", 14f, Ui.TEXTE, false));
        aujTotal = Ui.texte(ctx, "0 o", 30f, Ui.TEXTE, true);
        auj.addView(aujTotal);
        LinearLayout corps = new LinearLayout(ctx);
        corps.setOrientation(HORIZONTAL);
        corps.setGravity(Gravity.CENTER_VERTICAL);
        corps.setPadding(0, Ui.dp(ctx, 14), 0, 0);
        donut = new Donut(ctx);
        donut.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 120), Ui.dp(ctx, 120)));
        corps.addView(donut);
        LinearLayout droite = new LinearLayout(ctx);
        droite.setOrientation(VERTICAL);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dp.leftMargin = Ui.dp(ctx, 20);
        droite.setLayoutParams(dp);
        droite.addView(Ui.texte(ctx, "Téléchargement", 14f, Ui.TEXTE, false));
        LinearLayout l1 = new LinearLayout(ctx);
        l1.setOrientation(HORIZONTAL);
        aujRx = Ui.texte(ctx, "0 o", 17f, TELECH, true);
        aujPctRx = Ui.texte(ctx, "0%", 14f, Ui.DISCRET, false);
        aujPctRx.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
        l1.addView(aujRx);
        l1.addView(aujPctRx);
        droite.addView(l1);
        TextView te = Ui.texte(ctx, "Envoi", 14f, Ui.TEXTE, false);
        te.setPadding(0, Ui.dp(ctx, 14), 0, 0);
        droite.addView(te);
        LinearLayout l2 = new LinearLayout(ctx);
        l2.setOrientation(HORIZONTAL);
        aujTx = Ui.texte(ctx, "0 o", 17f, ENVOI, true);
        aujPctTx = Ui.texte(ctx, "0%", 14f, Ui.DISCRET, false);
        aujPctTx.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
        l2.addView(aujTx);
        l2.addView(aujPctTx);
        droite.addView(l2);
        corps.addView(droite);
        auj.addView(corps);
        addView(auj);

        // ---- Hier / Semaine / Mois / Total
        LinearLayout r1 = rangeeDouble(ctx);
        hier = petiteCarte(ctx, r1, "Hier", true);
        sem = petiteCarte(ctx, r1, "Cette semaine", false);
        addView(r1);
        LinearLayout r2 = rangeeDouble(ctx);
        mois = petiteCarte(ctx, r2, "Ce mois-ci", true);
        tot = petiteCarte(ctx, r2, "30 derniers jours", false);
        addView(r2);

        // ---- Alerte de quota
        LinearLayout q = Ui.carte(ctx);
        remplir(ctx, q);
        q.addView(Ui.texte(ctx, "Alerte de quota", 18f, Ui.TEXTE, true));
        TextView desc = Ui.texte(ctx,
                "Laisse vide pour ne pas avoir d'alerte. Notification à 50 pour cent, "
                        + "80 pour cent puis 100 pour cent, selon le filtre choisi en haut "
                        + "(écran ouvert).", 13f, Ui.DISCRET, false);
        desc.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        q.addView(desc);
        q.addView(ligneQuota(ctx, "Limite par jour", "jour"));
        q.addView(ligneQuota(ctx, "Limite par mois", "mois"));
        addView(q);
    }

    // ------------------------------------------------------------------ Construction

    private void remplir(Context c, LinearLayout carte) {
        carte.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
    }

    private LinearLayout colonne(Context c, String label, TextView valeur, int gravite) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(VERTICAL);
        l.setGravity(gravite);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(Ui.texte(c, label, 13f, Ui.TEXTE, false));
        l.addView(valeur);
        return l;
    }

    private LinearLayout rangeeDouble(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    /** Retourne {total, reçu, envoyé}. */
    private TextView[] petiteCarte(Context c, LinearLayout rangee, String titre, boolean gauche) {
        LinearLayout carte = Ui.carte(c);
        remplir(c, carte);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.bottomMargin = Ui.dp(c, 10);
        if (gauche) lp.rightMargin = Ui.dp(c, 5); else lp.leftMargin = Ui.dp(c, 5);
        carte.setLayoutParams(lp);
        TextView tt = Ui.texte(c, titre, 14f, Ui.TEXTE, false);
        carte.addView(tt);
        TextView total = Ui.texte(c, "0 o", 24f, Ui.TEXTE, true);
        TextView rx = Ui.texte(c, "↓ 0 o", 16f, TELECH, true);
        TextView tx = Ui.texte(c, "↑ 0 o", 16f, ENVOI, true);
        total.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
        carte.addView(total);
        carte.addView(rx);
        carte.addView(tx);
        rangee.addView(carte);
        return new TextView[] { total, rx, tx, tt };
    }

    private View ligneQuota(final Context c, String indice, final String periode) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 10), 0, 0);

        final EditText champ = Ui.champ(c, indice, ConsommationStore.limiteTexte(c, periode));
        champ.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        champ.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(champ);

        final int[] unite = { ConsommationStore.limiteUnite(c, periode) };
        final TextView mo = boutonUnite(c, "Mo");
        final TextView go = boutonUnite(c, "Go");
        styleUnite(c, mo, go, unite[0]);

        champ.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                ConsommationStore.definirLimite(c, periode, e.toString(), unite[0]);
            }
        });
        champ.setOnFocusChangeListener((v, a) -> {
            if (!a && champ.getText().length() > 0) demanderPermission();
        });
        mo.setOnClickListener(v -> choisir(c, champ, periode, unite, 0, mo, go));
        go.setOnClickListener(v -> choisir(c, champ, periode, unite, 1, mo, go));
        r.addView(mo);
        r.addView(go);
        return r;
    }

    private void choisir(Context c, EditText champ, String periode, int[] unite, int valeur,
                         TextView mo, TextView go) {
        unite[0] = valeur;
        styleUnite(c, mo, go, valeur);
        ConsommationStore.definirLimite(c, periode, champ.getText().toString(), valeur);
        if (champ.getText().length() > 0) demanderPermission();
    }

    private TextView boutonUnite(Context c, String s) {
        TextView b = Ui.texte(c, s, 16f, Ui.TEXTE, true);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 44));
        lp.leftMargin = Ui.dp(c, 8);
        lp.topMargin = Ui.dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleUnite(Context c, TextView mo, TextView go, int choix) {
        mo.setBackground(Ui.fond(c, choix == 0 ? Ui.BLEU : 0x00000000, 10, choix == 0 ? 0 : Ui.BORD));
        go.setBackground(Ui.fond(c, choix == 1 ? Ui.BLEU : 0x00000000, 10, choix == 1 ? 0 : Ui.BORD));
    }

    private void demanderPermission() {
        if (permDemandee) return;
        permDemandee = true;
        Context c = getContext();
        if (Build.VERSION.SDK_INT >= 33 && c instanceof Activity) {
            Activity a = (Activity) c;
            if (a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 4100);
            }
        }
    }

    // ------------------------------------------------------------------ Formats

    static String taille(long o) {
        if (o < 1024) return o + " o";
        if (o < 1048576L) return String.format(Locale.getDefault(), "%.1f Ko", o / 1024.0);
        if (o < 1073741824L) return String.format(Locale.getDefault(), "%.1f Mo", o / 1048576.0);
        return String.format(Locale.getDefault(), "%.2f Go", o / 1073741824.0);
    }

    static String debit(double o) {
        if (o < 1024) return String.format(Locale.getDefault(), "%.0f o/s", o);
        if (o < 1048576) return String.format(Locale.getDefault(), "%.1f Ko/s", o / 1024.0);
        return String.format(Locale.getDefault(), "%.2f Mo/s", o / 1048576.0);
    }

    private static String duree(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static String pct(long part, long total) {
        if (total <= 0) return "0%";
        return Math.round(part * 100.0 / total) + "%";
    }

    private void remplirPetite(TextView[] v, long[] d) {
        v[0].setText(taille(d[0] + d[1]));
        v[1].setText("↓ " + taille(d[0]));
        v[2].setText("↑ " + taille(d[1]));
    }

    // ------------------------------------------------------------------ Mise à jour

    private void choisirMode(int m) {
        mode = m;
        getContext().getSharedPreferences("yiwalo_reglages", Context.MODE_PRIVATE)
                .edit().putInt("conso_mode", m).apply();
        baseSess = null;
        tics = 0;
        styleChips();
    }

    private void styleChips() {
        Context c = getContext();
        for (int i = 0; i < 3; i++) {
            boolean actif = i == mode;
            puces[i].setBackground(Ui.fond(c, actif ? Ui.BLEU : 0x00000000, 10,
                    actif ? 0 : Ui.BORD));
        }
    }

    private void ouvrirReglagesAcces() {
        Context c = getContext();
        Intent i = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            i.setData(Uri.parse("package:" + c.getPackageName()));
            c.startActivity(i);
        } catch (RuntimeException e) {
            try {
                Intent j = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
                j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(j);
            } catch (RuntimeException ignore) {
                // réglage indisponible sur cet appareil
            }
        }
    }

    // ------------------------------------------------------------------ Mise à jour

    private void maj() {
        sessDuree.setText(t0 > 0 ? duree(SystemClock.elapsedRealtime() - t0) : "--:--:--");
        if (tics++ % 5 == 0) lancerReleve();
    }

    /** Lecture des statistiques hors du fil principal : {aujourd'hui, hier, semaine, mois, total}. */
    private void lancerReleve() {
        if (releveEnCours || exe == null) return;
        releveEnCours = true;
        final Context c = getContext().getApplicationContext();
        final int m = mode;
        try {
            exe.execute(() -> {
                try {
                    long[][] r = null;
                    boolean sys = false;
                    try {
                        if (ConsoSysteme.permis(c)) {
                            r = ConsoSysteme.releve(c, m);
                            sys = true;
                        }
                    } catch (RuntimeException e) {
                        r = null;
                        sys = false;
                    }
                    if (r == null) {
                        // sans autorisation : estimation sur les données mobiles du téléphone
                        ConsommationStore.echantillonner(c);
                        r = new long[][]{ConsommationStore.jour(c, 0), ConsommationStore.jour(c, 1),
                                ConsommationStore.semaine(c), ConsommationStore.mois(c),
                                ConsommationStore.total(c)};
                    } else {
                        ConsommationStore.verifierExterne(c, r[0][0] + r[0][1], r[3][0] + r[3][1]);
                    }
                    final long[][] res = r;
                    final boolean s = sys;
                    h.post(() -> {
                        releveEnCours = false;
                        if (m == mode) appliquer(res, s);
                    });
                } catch (RuntimeException e) {
                    h.post(() -> releveEnCours = false);
                }
            });
        } catch (RuntimeException e) {
            releveEnCours = false;
        }
    }

    private void appliquer(long[][] r, boolean sys) {
        carteAcces.setVisibility(sys ? GONE : VISIBLE);
        String[] filtres = {"Wi-Fi + données mobiles", "données mobiles", "Wi-Fi"};
        sousTitre.setText(sys
                ? "Tout le téléphone · " + filtres[mode] + " · toutes les applications"
                : "Estimation des données mobiles de tout le téléphone. Autorise l’accès "
                + "ci-dessous pour la valeur réelle.");

        long[] auj = r[0];
        long now = SystemClock.elapsedRealtime();
        if (baseSess == null || auj[0] < baseSess[0] || auj[1] < baseSess[1]) {
            baseSess = auj.clone();
            t0 = now;
        }
        long sr = auj[0] - baseSess[0];
        long st = auj[1] - baseSess[1];
        sessTotal.setText(taille(sr + st));
        sessRx.setText(taille(sr));
        sessTx.setText(taille(st));
        sessDuree.setText(duree(now - t0));

        long ta = auj[0] + auj[1];
        aujTotal.setText(taille(ta));
        aujRx.setText(taille(auj[0]));
        aujTx.setText(taille(auj[1]));
        aujPctRx.setText(pct(auj[0], ta));
        aujPctTx.setText(pct(auj[1], ta));
        donut.set(auj[0], auj[1], taille(ta));

        remplirPetite(hier, r[1]);
        remplirPetite(sem, r[2]);
        remplirPetite(mois, r[3]);
        remplirPetite(tot, r[4]);
        tot[3].setText(sys ? "30 derniers jours" : "Total");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        baseSess = null;
        t0 = 0;
        tics = 0;
        releveEnCours = false;
        exe = Executors.newSingleThreadExecutor();
        h.post(tic);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(tic);
        if (exe != null) {
            exe.shutdownNow();
            exe = null;
        }
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ Anneau

    private static final class Donut extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long rx, tx;
        private String centre = "0 o";

        Donut(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(c, 14));
            pt.setColor(Ui.TEXTE);
            pt.setTextAlign(Paint.Align.CENTER);
            pt.setFakeBoldText(true);
            pt.setTextSize(Ui.dp(c, 15));
        }

        void set(long rx, long tx, String centre) {
            this.rx = rx;
            this.tx = tx;
            this.centre = centre;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth();
            float hh = getHeight();
            float marge = p.getStrokeWidth() / 2f;
            float cote = Math.min(w, hh);
            float x0 = (w - cote) / 2f;
            float y0 = (hh - cote) / 2f;
            r.set(x0 + marge, y0 + marge, x0 + cote - marge, y0 + cote - marge);
            p.setColor(Ui.BORD);
            cv.drawArc(r, 0, 360, false, p);
            long t = rx + tx;
            if (t > 0) {
                float balayage = 360f * rx / t;
                p.setColor(TELECH);
                cv.drawArc(r, -90, balayage, false, p);
                p.setColor(ENVOI);
                cv.drawArc(r, -90 + balayage, 360f - balayage, false, p);
            }
            float y = hh / 2f - (pt.descent() + pt.ascent()) / 2f;
            cv.drawText(centre, w / 2f, y, pt);
        }
    }
}
