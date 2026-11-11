package com.yiwalo.tunnel;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int FOND = 0xFF1B2230;
    static int BORD = 0xFF2C3648;
    static int TEXTE = 0xFFFFFFFF;
    static int DISCRET = 0xFF9AA4B5;
    static int FOND2 = 0xFF243049;
    static boolean SOMBRE = true;
    static int BLEU = 0xFF3B82F6;
    static int VERT = 0xFF22C55E;
    static int ROUGE = 0xFFEF4444;
    static int ORANGE = 0xFFF59E0B;
    static int VIOLET = 0xFFA78BFA;
    static int ROSE = 0xFFF472B6;

    private Ui() { }

    static void appliquerTheme(boolean sombre) {
        FOND = sombre ? 0xFF1B2230 : 0xFFFFFFFF;
        BORD = sombre ? 0xFF2C3648 : 0xFFD5DCE8;
        TEXTE = sombre ? 0xFFFFFFFF : 0xFF0F172A;
        DISCRET = sombre ? 0xFF9AA4B5 : 0xFF475569;
        FOND2 = sombre ? 0xFF243049 : 0xFFF1F5F9;
        SOMBRE = sombre;
        BLEU = sombre ? 0xFF3B82F6 : 0xFF2563EB;
        VERT = sombre ? 0xFF22C55E : 0xFF16A34A;
        ROUGE = sombre ? 0xFFEF4444 : 0xFFDC2626;
        ORANGE = sombre ? 0xFFF59E0B : 0xFFD97706;
        VIOLET = sombre ? 0xFFA78BFA : 0xFF7C3AED;
        ROSE = sombre ? 0xFFF472B6 : 0xFFDB2777;
    }

    /** Légère ombre douce en thème clair pour que les cartes se détachent du fond. */
    static void relief(Context c, View v) {
        if (!SOMBRE) v.setElevation(dp(c, 2));
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable fond(Context c, int couleur, int rayon, int bord) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(couleur);
        g.setCornerRadius(dp(c, rayon));
        if (bord != 0) g.setStroke(dp(c, 1), bord);
        return g;
    }

    static TextView texte(Context c, String s, float taille, int couleur, boolean gras) {
        TextView v = new TextView(c);
        v.setText(T.t(s));
        v.setTextSize(taille);
        v.setTextColor(couleur);
        if (gras) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static LinearLayout carte(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(fond(c, FOND, 16, BORD));
        l.setClipToOutline(true);
        relief(c, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView section(Context c, String s) {
        TextView v = texte(c, s, 12f, DISCRET, true);
        v.setLetterSpacing(0.08f);
        v.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return v;
    }

    static View separateur(Context c) {
        View v = new View(c);
        v.setBackgroundColor(BORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(c, 68);
        v.setLayoutParams(lp);
        return v;
    }

    static View rangee(Context c, int icone, int teinte, String titre, String sous,
                       final Runnable action) {
        return rangee(c, icone, teinte, titre, sous, R.drawable.ic_chevron, action);
    }

    static View rangee(Context c, int icone, int teinte, String titre, String sous,
                       int iconeFin, final Runnable action) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        ImageView ic = new ImageView(c);
        ic.setImageResource(icone);
        ic.setColorFilter(teinte);
        ic.setPadding(dp(c, 9), dp(c, 9), dp(c, 9), dp(c, 9));
        ic.setBackground(fond(c, (teinte & 0x00FFFFFF) | 0x33000000, 12, 0));
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        r.addView(ic);
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(c, 14);
        t.setLayoutParams(tp);
        t.addView(texte(c, titre, 16f, TEXTE, true));
        if (sous != null && !sous.isEmpty()) t.addView(texte(c, sous, 13f, DISCRET, false));
        r.addView(t);
        ImageView fin = new ImageView(c);
        fin.setImageResource(iconeFin);
        fin.setColorFilter(DISCRET);
        fin.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
        r.addView(fin);
        r.setClickable(true);
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(SOMBRE ? 0x33FFFFFF : 0x1F0F172A), null,
                new ColorDrawable(0xFFFFFFFF)));
        r.setOnClickListener(v -> action.run());
        return r;
    }

    static LinearLayout entete(Context c, String titre, final Runnable retour) {
        LinearLayout e = new LinearLayout(c);
        e.setOrientation(LinearLayout.HORIZONTAL);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(0, dp(c, 4), 0, dp(c, 12));
        ImageView b = new ImageView(c);
        b.setImageResource(R.drawable.ic_retour);
        b.setColorFilter(TEXTE);
        b.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        b.setOnClickListener(v -> retour.run());
        e.addView(b);
        TextView t = texte(c, titre, 22f, TEXTE, true);
        t.setPadding(dp(c, 6), 0, 0, 0);
        e.addView(t);
        return e;
    }

    static TextView bouton(final Context c, String s, int couleur, final Runnable action) {
        TextView b = texte(c, s, 16f, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14));
        b.setBackground(fond(c, couleur, 12, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    static EditText champ(Context c, String indice, String valeur) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setHint(T.t(indice));
        e.setHintTextColor(DISCRET);
        e.setText(valeur);
        e.setTextColor(TEXTE);
        e.setBackground(fond(c, FOND2, 10, BORD));
        e.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        e.setLayoutParams(lp);
        return e;
    }

    static LinearLayout info(Context c, String label, String valeur) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        TextView a = texte(c, label, 14f, DISCRET, false);
        a.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(a);
        TextView b = texte(c, valeur, 14f, TEXTE, true);
        b.setGravity(Gravity.END);
        r.addView(b);
        return r;
    }
}
