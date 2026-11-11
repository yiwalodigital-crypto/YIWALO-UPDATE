package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AbonnementsView extends ScrollView {

    private static final String NUMERO = "22652839161";
    private static final String[] RESEAUX = {"Moov", "Orange"};
    private static final int[] COULEURS = {0xFF2563EB, 0xFFFF7900};
    private static final String[] DUREES = {"1 semaine", "1 mois"};
    private static final int[] PRIX = {600, 1500};
    private static final int FOND_CARTE = 0xFF111827;
    private static final int BORDURE = 0xFF263046;

    private final Context ctx;
    private final String idAppareil;
    private int reseau = 0;
    private int duree = 1;
    private final LinearLayout[] cartesReseau = new LinearLayout[2];
    private final LinearLayout[] cartesDuree = new LinearLayout[2];
    private TextView recap;
    private TextView bouton;

    public AbonnementsView(Context c) {
        super(c);
        ctx = c;
        String id;
        try {
            id = PartageConfig.idAppareil(c);
        } catch (RuntimeException e) {
            id = "indisponible";
        }
        idAppareil = id;

        LinearLayout racine = new LinearLayout(c);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setPadding(dp(18), dp(20), dp(18), dp(28));
        addView(racine, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titre = texte("Abonnements", 26f, 0xFFFFFFFF, true);
        racine.addView(titre);
        racine.addView(texte("Choisissez votre réseau et votre durée, puis commandez en un geste.",
                14f, 0xFF94A3B8, false));

        racine.addView(section("1. Votre réseau"));
        LinearLayout ligneReseau = new LinearLayout(c);
        ligneReseau.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            final int k = i;
            LinearLayout carte = carte();
            carte.setGravity(Gravity.CENTER);
            carte.setPadding(dp(8), dp(22), dp(8), dp(22));
            TextView pastille = texte(RESEAUX[i].substring(0, 1), 22f, 0xFFFFFFFF, true);
            pastille.setGravity(Gravity.CENTER);
            GradientDrawable p = new GradientDrawable();
            p.setShape(GradientDrawable.OVAL);
            p.setColor(COULEURS[i]);
            pastille.setBackground(p);
            carte.addView(pastille, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView nom = texte(RESEAUX[i], 18f, 0xFFFFFFFF, true);
            nom.setGravity(Gravity.CENTER);
            nom.setPadding(0, dp(10), 0, 0);
            carte.addView(nom);
            carte.setOnClickListener(v -> { reseau = k; maj(); });
            cartesReseau[i] = carte;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(i == 0 ? 0 : dp(6), 0, i == 0 ? dp(6) : 0, 0);
            ligneReseau.addView(carte, lp);
        }
        racine.addView(ligneReseau);

        racine.addView(section("2. Votre durée"));
        for (int i = 0; i < 2; i++) {
            final int k = i;
            LinearLayout carte = carte();
            carte.setOrientation(LinearLayout.HORIZONTAL);
            carte.setGravity(Gravity.CENTER_VERTICAL);
            carte.setPadding(dp(18), dp(18), dp(18), dp(18));
            LinearLayout gauche = new LinearLayout(c);
            gauche.setOrientation(LinearLayout.VERTICAL);
            gauche.addView(texte(DUREES[i], 18f, 0xFFFFFFFF, true));
            gauche.addView(texte(i == 0 ? "Idéal pour essayer" : "Meilleur prix", 13f,
                    i == 0 ? 0xFF94A3B8 : 0xFF4ADE80, false));
            carte.addView(gauche, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            carte.addView(texte(PRIX[i] + " F", 22f, 0xFF22D3EE, true));
            carte.setOnClickListener(v -> { duree = k; maj(); });
            cartesDuree[i] = carte;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(10));
            racine.addView(carte, lp);
        }

        racine.addView(section("3. Récapitulatif"));
        LinearLayout boite = carte();
        boite.setPadding(dp(18), dp(16), dp(18), dp(16));
        recap = texte("", 16f, 0xFFFFFFFF, false);
        boite.addView(recap);
        TextView idVue = texte("ID : " + idAppareil + "   (toucher pour copier)", 14f, 0xFF22D3EE, true);
        idVue.setPadding(0, dp(10), 0, 0);
        idVue.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ID appareil", idAppareil));
            T.toast(ctx, "ID copié", Toast.LENGTH_SHORT).show();
        });
        boite.addView(idVue);
        racine.addView(boite);

        bouton = texte("", 17f, 0xFFFFFFFF, true);
        bouton.setGravity(Gravity.CENTER);
        bouton.setPadding(dp(16), dp(16), dp(16), dp(16));
        bouton.setOnClickListener(v -> commander());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(18);
        racine.addView(bouton, bp);

        racine.addView(texte("Votre ID est envoyé automatiquement dans le message. "
                + "Le paiement et l’activation se font ensuite sur WhatsApp.",
                12f, 0xFF64748B, false));
        maj();
    }

    private void commander() {
        String message = "Bonjour, je souhaite un abonnement YIWALO TUNNEL.\n"
                + "Réseau : " + RESEAUX[reseau] + "\n"
                + "Durée : " + DUREES[duree] + " (" + PRIX[duree] + " F)\n"
                + "Mon ID : " + idAppareil;
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + NUMERO + "?text=" + Uri.encode(message))));
        } catch (RuntimeException e) {
            T.toast(ctx, "WhatsApp introuvable", Toast.LENGTH_LONG).show();
        }
    }

    private void maj() {
        int couleur = COULEURS[reseau];
        for (int i = 0; i < 2; i++) {
            style(cartesReseau[i], i == reseau, COULEURS[i]);
            style(cartesDuree[i], i == duree, couleur);
        }
        recap.setText("Réseau : " + RESEAUX[reseau] + "\nDurée : " + DUREES[duree]
                + "\nPrix : " + PRIX[duree] + " F");
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{couleur, 0xFF7C3AED});
        g.setCornerRadius(dp(28));
        bouton.setBackground(g);
        bouton.setText("Commander sur WhatsApp • " + PRIX[duree] + " F");
    }

    private void style(View v, boolean actif, int couleur) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(actif ? (couleur & 0x00FFFFFF) | 0x33000000 : FOND_CARTE);
        g.setStroke(dp(actif ? 3 : 1), actif ? couleur : BORDURE);
        v.setBackground(g);
    }

    private LinearLayout carte() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        style(l, false, 0);
        return l;
    }

    private TextView section(String t) {
        TextView v = texte(t, 15f, 0xFF22D3EE, true);
        v.setPadding(0, dp(24), 0, dp(10));
        return v;
    }

    private TextView texte(String t, float taille, int couleur, boolean gras) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(taille);
        v.setTextColor(couleur);
        if (gras) {
            v.setTypeface(v.getTypeface(), Typeface.BOLD);
        }
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
