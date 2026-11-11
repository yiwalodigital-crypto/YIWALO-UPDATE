package com.yiwalo.tunnel;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ReglagesView extends ScrollView {
    private final Context ctx;
    private final boolean en;
    private final boolean sombre;

    public ReglagesView(final Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        SharedPreferences p = prefs();
        en = "en".equals(p.getString("langue", "fr"));
        sombre = p.getBoolean("sombre", true);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(10), dp(18), dp(32));
        addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.entete(ctx, en ? "Settings" : "Réglages", retour));
        col.addView(hero());

        col.addView(Ui.section(ctx, en ? "APPEARANCE" : "APPARENCE"));
        LinearLayout themes = new LinearLayout(ctx);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.addView(carteTheme(true, en ? "Dark" : "Sombre"), poids(0, 6));
        themes.addView(carteTheme(false, en ? "Light" : "Clair"), poids(6, 0));
        col.addView(themes);

        col.addView(Ui.section(ctx, en ? "LANGUAGE" : "LANGUE"));
        LinearLayout langues = new LinearLayout(ctx);
        langues.setOrientation(LinearLayout.HORIZONTAL);
        langues.addView(puceLangue("fr", "FR", "Français"), poids(0, 6));
        langues.addView(puceLangue("en", "EN", "English"), poids(6, 0));
        col.addView(langues);

        col.addView(Ui.section(ctx, en ? "SECURITY" : "SÉCURITÉ"));
        col.addView(carteVerrou());

        TextView note = Ui.texte(ctx, en ? "Changes apply instantly."
                : "Les changements s’appliquent immédiatement.", 12f, Ui.DISCRET, false);
        note.setPadding(dp(4), dp(16), 0, 0);
        col.addView(note);
    }

    private View carteVerrou() {
        LinearLayout carte = Ui.carte(ctx);
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.texte(ctx, en ? "Lock the app" : "Verrouiller l’application",
                16f, Ui.TEXTE, true));
        t.addView(Ui.texte(ctx, en
                ? "Ask for your fingerprint, PIN or pattern when the app opens."
                : "Demande ton empreinte, ton code ou ton schéma à l’ouverture de l’application.",
                12f, Ui.DISCRET, false));
        l.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch sw = new android.widget.Switch(ctx);
        sw.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFFFFFFFF, sombre ? 0xFFCBD5E1 : 0xFF94A3B8}));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.BLEU, sombre ? 0xFF475569 : 0xFFCBD5E1}));
        sw.setChecked(VerrouApp.actif(ctx));
        sw.setOnCheckedChangeListener((b, coche) -> {
            if (coche && !VerrouApp.ecranSecurise(ctx)) {
                b.setChecked(false);
                T.toast(ctx, en
                        ? "Set a screen lock (PIN, pattern or fingerprint) in the phone settings first."
                        : "Configure d’abord un verrouillage d’écran (code, schéma ou empreinte) dans les réglages du téléphone.",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            VerrouApp.definir(ctx, coche);
        });
        l.addView(sw);
        carte.addView(l);
        return carte;
    }

    private int dp(int v) {
        return Ui.dp(ctx, v);
    }

    private LinearLayout.LayoutParams poids(int gauche, int droite) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(gauche);
        lp.rightMargin = dp(droite);
        return lp;
    }

    private View hero() {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), dp(4), dp(4), dp(8));
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_reglages);
        ic.setColorFilter(Ui.BLEU);
        ic.setPadding(dp(14), dp(14), dp(14), dp(14));
        ic.setBackground(Ui.fond(ctx, (Ui.BLEU & 0x00FFFFFF) | (sombre ? 0x33000000 : 0x1F000000), 20, 0));
        h.addView(ic, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(14);
        h.addView(t, tp);
        t.addView(Ui.texte(ctx, en ? "Make it yours" : "Personnalisez l’application",
                17f, Ui.TEXTE, true));
        t.addView(Ui.texte(ctx, en ? "Theme and language apply to the whole app."
                : "Le thème et la langue s’appliquent partout.", 13f, Ui.DISCRET, false));
        return h;
    }

    private View bloc(int couleur, int largeurDp, int hauteurDp) {
        View b = new View(ctx);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(4));
        g.setColor(couleur);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                largeurDp == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(largeurDp),
                dp(hauteurDp));
        lp.bottomMargin = dp(5);
        b.setLayoutParams(lp);
        return b;
    }

    private View carteTheme(final boolean pourSombre, String titre) {
        final boolean actif = sombre == pourSombre;
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(Ui.FOND);
        g.setStroke(dp(actif ? 2 : 1), actif ? Ui.BLEU : Ui.BORD);
        c.setBackground(g);
        Ui.relief(ctx, c);

        LinearLayout ap = new LinearLayout(ctx);
        ap.setOrientation(LinearLayout.VERTICAL);
        ap.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable ga = new GradientDrawable();
        ga.setCornerRadius(dp(12));
        ga.setColor(pourSombre ? 0xFF0B1020 : 0xFFF1F5F9);
        ga.setStroke(1, pourSombre ? 0xFF2C3648 : 0xFFD5DCE8);
        ap.setBackground(ga);
        ap.addView(bloc(pourSombre ? 0xFF3B82F6 : 0xFF2563EB, 36, 6));
        ap.addView(bloc(pourSombre ? 0xFF1B2230 : 0xFFFFFFFF, 0, 16));
        ap.addView(bloc(pourSombre ? 0xFF1B2230 : 0xFFFFFFFF, 0, 16));
        ap.addView(bloc(pourSombre ? 0xFF22C55E : 0xFF16A34A, 24, 6));
        c.addView(ap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));

        LinearLayout bas = new LinearLayout(ctx);
        bas.setOrientation(LinearLayout.HORIZONTAL);
        bas.setGravity(Gravity.CENTER_VERTICAL);
        TextView coche = Ui.texte(ctx, actif ? "✓" : "", 12f, 0xFFFFFFFF, true);
        coche.setGravity(Gravity.CENTER);
        GradientDrawable gc = new GradientDrawable();
        gc.setShape(GradientDrawable.OVAL);
        if (actif) {
            gc.setColor(Ui.BLEU);
        } else {
            gc.setColor(0x00000000);
            gc.setStroke(dp(1), Ui.BORD);
        }
        coche.setBackground(gc);
        bas.addView(coche, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView nom = Ui.texte(ctx, titre, 15f, Ui.TEXTE, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.leftMargin = dp(10);
        bas.addView(nom, np);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(12);
        c.addView(bas, bp);

        c.setClickable(true);
        c.setOnClickListener(v -> {
            if (!actif) definir("sombre", pourSombre, null);
        });
        return c;
    }

    private View puceLangue(final String code, String badge, String nom) {
        final boolean actif = (en ? "en" : "fr").equals(code);
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(16), dp(12), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(actif ? Ui.BLEU : Ui.FOND);
        g.setStroke(dp(1), actif ? Ui.BLEU : Ui.BORD);
        c.setBackground(g);
        Ui.relief(ctx, c);
        TextView b = Ui.texte(ctx, badge, 12f, actif ? 0xFFFFFFFF : Ui.DISCRET, true);
        b.setLetterSpacing(0.1f);
        c.addView(b);
        TextView n = Ui.texte(ctx, nom, 16f, actif ? 0xFFFFFFFF : Ui.TEXTE, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.leftMargin = dp(10);
        c.addView(n, np);
        c.setClickable(true);
        c.setOnClickListener(v -> {
            if (!actif) definir(null, false, code);
        });
        return c;
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("yiwalo_apparence", Context.MODE_PRIVATE);
    }

    private void definir(String cle, boolean sombreVoulu, String langue) {
        SharedPreferences.Editor e = prefs().edit();
        if (cle != null) e.putBoolean(cle, sombreVoulu);
        if (langue != null) e.putString("langue", langue);
        e.apply();
        PlusView.rouvrirReglages = true;
        if (ctx instanceof Activity) ((Activity) ctx).recreate();
    }
}
