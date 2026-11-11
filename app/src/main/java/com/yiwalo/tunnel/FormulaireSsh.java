package com.yiwalo.tunnel;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.config.TypeConnexion;
import java.util.UUID;

public final class FormulaireSsh {
    private FormulaireSsh() {}

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout.LayoutParams marge(Context ctx, int haut) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(ctx, haut);
        return p;
    }

    static EditText champ(Context ctx, String indice, String valeur, int type) {
        EditText e = new EditText(ctx);
        e.setHint(indice);
        e.setHintTextColor(ctx.getColor(R.color.texte_discret));
        e.setTextColor(ctx.getColor(R.color.texte));
        e.setTextSize(15f);
        e.setInputType(type);
        e.setText(valeur == null ? "" : valeur);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(ctx, 1), ctx.getColor(R.color.texte_discret));
        g.setCornerRadius(dp(ctx, 8));
        e.setBackground(g);
        e.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        e.setLayoutParams(marge(ctx, 14));
        return e;
    }

    static TextView aide(Context ctx, String texte) {
        TextView t = new TextView(ctx);
        t.setText(texte);
        t.setTextSize(12f);
        t.setTextColor(ctx.getColor(R.color.texte_discret));
        t.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), 0);
        return t;
    }

    static TextView bouton(Context ctx, String texte, boolean plein) {
        TextView b = new TextView(ctx);
        b.setText(texte);
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, 24));
        if (plein) {
            g.setColor(ctx.getColor(R.color.bleu));
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            b.setTextColor(ctx.getColor(R.color.bleu_clair));
        }
        b.setBackground(g);
        b.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        b.setLayoutParams(marge(ctx, 14));
        return b;
    }

    static String t(EditText e) {
        return e.getText().toString().trim();
    }

    static void erreur(Context ctx, String msg) {
        T.toast(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public static void ouvrir(Context ctx, ConfigStore store, Configuration ex, Runnable apres) {
        final int TXT = InputType.TYPE_CLASS_TEXT;
        final int URI = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        final int MDP = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

        LinearLayout racine = new LinearLayout(ctx);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 8));

        TextView titre = new TextView(ctx);
        titre.setText(ex == null ? "Nouvelle configuration SSH" : "Modifier la configuration SSH");
        titre.setTextSize(20f);
        titre.setTypeface(titre.getTypeface(), Typeface.BOLD);
        titre.setTextColor(ctx.getColor(R.color.texte));
        racine.addView(titre);

        final EditText nom = champ(ctx, "Nom de la configuration", ex == null ? "" : ex.nom, TXT);
        racine.addView(nom);
        racine.addView(aide(ctx, "Nom affiché dans la liste de tes configurations."));

        final EditText ns = champ(ctx, "Domaine NS", ex == null ? "" : ex.slowNs, URI);

        final EditText cle = champ(ctx, "Clé publique du serveur", ex == null ? "" : ex.slowCle, URI);

        final EditText dns = champ(ctx, "Résolveur DNS",
                ex == null || ex.slowResolveur.isEmpty() ? "8.8.8.8:53" : ex.slowResolveur, URI);

        final EditText hote = champ(ctx, "Adresse du serveur SSH", ex == null ? "" : ex.serveur, URI);
        racine.addView(hote);
        racine.addView(aide(ctx, "Adresse ou IP du serveur SSH situé derrière le tunnel."));

        final EditText user = champ(ctx, "Utilisateur SSH", ex == null ? "" : ex.sshUser, URI);
        racine.addView(user);
        racine.addView(aide(ctx, "Identifiant du compte SSH."));

        final EditText mdp = champ(ctx, "Mot de passe SSH", ex == null ? "" : ex.sshMdp, MDP);
        racine.addView(mdp);
        racine.addView(aide(ctx, "Mot de passe du compte SSH. Il est masqué à l'écran."));

        final EditText port = champ(ctx, "Port SSH", ex == null ? "22" : String.valueOf(ex.port),
                InputType.TYPE_CLASS_NUMBER);
        racine.addView(port);
        racine.addView(aide(ctx, "En général 22."));

        final AlertDialog[] dlg = new AlertDialog[1];

        TextView enreg = bouton(ctx, "Enregistrer", true);
        enreg.setOnClickListener(v -> {
            String n = t(nom), d = t(ns), k = t(cle), r = t(dns), h = t(hote), u = t(user);
            if (n.isEmpty()) { erreur(ctx, "Le nom est obligatoire."); return; }
            if (h.isEmpty()) { erreur(ctx, "L'adresse du serveur SSH est obligatoire."); return; }
            if (u.isEmpty()) { erreur(ctx, "L'utilisateur SSH est obligatoire."); return; }
            int p;
            try { p = Integer.parseInt(t(port)); } catch (NumberFormatException e) { p = -1; }
            if (p < 1 || p > 65535) { erreur(ctx, "Le port SSH doit être entre 1 et 65535."); return; }
            if (!r.contains(":")) r = r + ":53";

            String lien = "ssh://" + h + ":" + p;
            Configuration c = new Configuration(
                    ex != null ? ex.id : UUID.randomUUID().toString(),
                    n, TypeConnexion.SSH, h, p, lien, "");
            c.slowNs = d;
            c.slowCle = k;
            c.slowResolveur = r;
            c.sshUser = u;
            c.sshMdp = mdp.getText().toString();
            if (ex != null) {
                c.expireLe = ex.expireLe;
                c.message = ex.message;
                c.verrouille = ex.verrouille;
                c.mobileSeulement = ex.mobileSeulement;
                c.bloquerRoot = ex.bloquerRoot;
                c.storeSeulement = ex.storeSeulement;
                c.appareils = ex.appareils;
                store.remplacer(c);
            } else if (!store.ajouter(c)) {
                erreur(ctx, "Ce nom de configuration existe déjà.");
                return;
            }
            dlg[0].dismiss();
            apres.run();
        });
        racine.addView(enreg);

        TextView annuler = bouton(ctx, "ANNULER", false);
        annuler.setOnClickListener(v -> dlg[0].dismiss());
        racine.addView(annuler);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(racine);
        dlg[0] = new AlertDialog.Builder(ctx).setView(sv).create();
        dlg[0].show();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ctx.getColor(R.color.carte));
        bg.setCornerRadius(dp(ctx, 20));
        dlg[0].getWindow().setBackgroundDrawable(bg);
    }
}
