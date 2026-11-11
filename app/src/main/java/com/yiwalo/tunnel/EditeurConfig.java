package com.yiwalo.tunnel;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.config.EditeurLien;
import com.yiwalo.tunnel.config.ErreurImport;
import com.yiwalo.tunnel.config.ImportateurLiens;

public final class EditeurConfig {

    private static final String[] RESEAUX = {"tcp", "ws", "grpc", "httpupgrade", "xhttp"};
    private static final String[] SECURITES = {"none", "tls", "reality"};

    private EditeurConfig() {
    }

    private static final class Bloc {
        final LinearLayout conteneur;
        final TextView etiquette;

        Bloc(LinearLayout conteneur, TextView etiquette) {
            this.conteneur = conteneur;
            this.etiquette = etiquette;
        }
    }

    @SuppressWarnings("deprecation")
    public static void ouvrir(Context contexte, ConfigStore store, Configuration c,
                              Runnable apres) {
        final EditeurLien.Champs ch;
        try {
            ch = EditeurLien.lire(c);
        } catch (ErreurImport e) {
            T.toast(contexte, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialogue = new Dialog(contexte, android.R.style.Theme_Material_NoActionBar);

        LinearLayout racine = new LinearLayout(contexte);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setBackgroundColor(contexte.getColor(R.color.fond));
        racine.setOnApplyWindowInsetsListener((vue, insets) -> {
            vue.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView titre = new TextView(contexte);
        titre.setText(R.string.ed_titre);
        titre.setTextSize(20f);
        titre.setTextColor(contexte.getColor(R.color.texte));
        titre.setTypeface(titre.getTypeface(), Typeface.BOLD);
        titre.setPadding(dp(contexte, 20), dp(contexte, 16), dp(contexte, 20), dp(contexte, 4));
        racine.addView(titre);

        TextView intro = new TextView(contexte);
        intro.setText(R.string.ed_intro);
        intro.setTextSize(13f);
        intro.setTextColor(contexte.getColor(R.color.texte_discret));
        intro.setPadding(dp(contexte, 20), 0, dp(contexte, 20), dp(contexte, 8));
        racine.addView(intro);

        ScrollView defilement = new ScrollView(contexte);
        LinearLayout formulaire = new LinearLayout(contexte);
        formulaire.setOrientation(LinearLayout.VERTICAL);
        formulaire.setPadding(dp(contexte, 16), 0, dp(contexte, 16), dp(contexte, 24));
        defilement.addView(formulaire, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        racine.addView(defilement, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final String[] reseau = {ch.reseau.isEmpty() ? "tcp" : ch.reseau};
        final String[] securite = {ch.securite.isEmpty() ? "none" : ch.securite};

        EditText nom = saisie(contexte, ch.nom, false);
        bloc(formulaire, R.string.ed_nom, R.string.ed_nom_aide, nom);
        EditText hote = saisie(contexte, ch.hote, false);
        bloc(formulaire, R.string.ed_hote, R.string.ed_hote_aide, hote);
        EditText port = saisie(contexte, ch.port, true);
        bloc(formulaire, R.string.ed_port, R.string.ed_port_aide, port);
        EditText identifiant = saisie(contexte, ch.identifiant, false);
        bloc(formulaire, R.string.ed_id, R.string.ed_id_aide, identifiant);

        TextView choixReseau = choix(contexte, reseau[0]);
        bloc(formulaire, R.string.ed_reseau, R.string.ed_reseau_aide, choixReseau);
        TextView choixSecurite = choix(contexte, securite[0]);
        bloc(formulaire, R.string.ed_securite, R.string.ed_securite_aide, choixSecurite);

        EditText sni = saisie(contexte, ch.sni, false);
        Bloc blocSni = bloc(formulaire, R.string.ed_sni, R.string.ed_sni_aide, sni);
        EditText hostEntete = saisie(contexte, ch.hostEntete, false);
        bloc(formulaire, R.string.ed_host, R.string.ed_host_aide, hostEntete);
        EditText chemin = saisie(contexte, ch.chemin, false);
        Bloc blocChemin = bloc(formulaire, R.string.ed_chemin, R.string.ed_chemin_aide, chemin);
        EditText alpn = saisie(contexte, ch.alpn, false);
        Bloc blocAlpn = bloc(formulaire, R.string.ed_alpn, R.string.ed_alpn_aide, alpn);
        EditText empreinte = saisie(contexte, ch.empreinte, false);
        Bloc blocFp = bloc(formulaire, R.string.ed_fp, R.string.ed_fp_aide, empreinte);
        EditText pbk = saisie(contexte, ch.pbk, false);
        Bloc blocPbk = bloc(formulaire, R.string.ed_pbk, R.string.ed_pbk_aide, pbk);
        EditText sid = saisie(contexte, ch.sid, false);
        Bloc blocSid = bloc(formulaire, R.string.ed_sid, R.string.ed_sid_aide, sid);
        EditText spx = saisie(contexte, ch.spx, false);
        Bloc blocSpx = bloc(formulaire, R.string.ed_spx, R.string.ed_spx_aide, spx);
        EditText flow = saisie(contexte, ch.flow, false);
        Bloc blocFlow = bloc(formulaire, R.string.ed_flow, R.string.ed_flow_aide, flow);

        Runnable majVisibilite = () -> {
            boolean tls = !securite[0].equals("none");
            boolean reality = securite[0].equals("reality");
            boolean tcp = reseau[0].equals("tcp");
            boolean grpc = reseau[0].equals("grpc");
            blocSni.conteneur.setVisibility(tls ? View.VISIBLE : View.GONE);
            blocAlpn.conteneur.setVisibility(tls && !reality ? View.VISIBLE : View.GONE);
            blocFp.conteneur.setVisibility(tls ? View.VISIBLE : View.GONE);
            blocPbk.conteneur.setVisibility(reality ? View.VISIBLE : View.GONE);
            blocSid.conteneur.setVisibility(reality ? View.VISIBLE : View.GONE);
            blocSpx.conteneur.setVisibility(reality ? View.VISIBLE : View.GONE);
            blocFlow.conteneur.setVisibility(tls && tcp ? View.VISIBLE : View.GONE);
            blocChemin.conteneur.setVisibility(tcp ? View.GONE : View.VISIBLE);
            blocChemin.etiquette.setText(grpc ? R.string.ed_service : R.string.ed_chemin);
        };
        choixReseau.setOnClickListener(v ->
                choisir(contexte, R.string.ed_reseau_titre, RESEAUX, reseau, choixReseau,
                        majVisibilite));
        choixSecurite.setOnClickListener(v ->
                choisir(contexte, R.string.ed_securite_titre, SECURITES, securite, choixSecurite,
                        majVisibilite));
        majVisibilite.run();

        TextView erreur = new TextView(contexte);
        erreur.setTextSize(14f);
        erreur.setTextColor(Color.parseColor("#EF4444"));
        erreur.setPadding(dp(contexte, 4), dp(contexte, 16), dp(contexte, 4), 0);
        erreur.setVisibility(View.GONE);
        formulaire.addView(erreur);

        LinearLayout boutons = new LinearLayout(contexte);
        boutons.setOrientation(LinearLayout.HORIZONTAL);
        boutons.setGravity(Gravity.CENTER);
        boutons.setPadding(0, dp(contexte, 20), 0, 0);

        TextView annuler = new TextView(contexte);
        annuler.setText(R.string.cfg_annuler);
        annuler.setTextSize(16f);
        annuler.setGravity(Gravity.CENTER);
        annuler.setTextColor(contexte.getColor(R.color.bleu_clair));
        annuler.setPadding(0, dp(contexte, 14), 0, dp(contexte, 14));
        annuler.setOnClickListener(v -> dialogue.dismiss());
        boutons.addView(annuler, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView enregistrer = new TextView(contexte);
        enregistrer.setText(R.string.ed_enregistrer);
        enregistrer.setTextSize(16f);
        enregistrer.setGravity(Gravity.CENTER);
        enregistrer.setTextColor(Color.WHITE);
        enregistrer.setTypeface(enregistrer.getTypeface(), Typeface.BOLD);
        enregistrer.setPadding(0, dp(contexte, 14), 0, dp(contexte, 14));
        GradientDrawable fondBouton = new GradientDrawable();
        fondBouton.setColor(contexte.getColor(R.color.bleu));
        fondBouton.setCornerRadius(dp(contexte, 14));
        enregistrer.setBackground(fondBouton);
        boutons.addView(enregistrer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        formulaire.addView(boutons);

        enregistrer.setOnClickListener(v -> {
            ch.nom = nom.getText().toString();
            ch.hote = hote.getText().toString();
            ch.port = port.getText().toString();
            ch.identifiant = identifiant.getText().toString();
            ch.reseau = reseau[0];
            ch.securite = securite[0];
            ch.sni = sni.getText().toString();
            ch.hostEntete = hostEntete.getText().toString();
            ch.chemin = chemin.getText().toString();
            ch.alpn = alpn.getText().toString();
            ch.empreinte = empreinte.getText().toString();
            ch.pbk = pbk.getText().toString();
            ch.sid = sid.getText().toString();
            ch.spx = spx.getText().toString();
            ch.flow = flow.getText().toString();
            try {
                String lien = EditeurLien.construire(c.type, ch);
                Configuration analysee = ImportateurLiens.analyser(lien);
                store.remplacer(new Configuration(c.id, analysee.nom, analysee.type,
                        analysee.serveur, analysee.port, lien));
                dialogue.dismiss();
                T.toast(contexte, R.string.ed_enregistre, Toast.LENGTH_SHORT).show();
                apres.run();
            } catch (ErreurImport e) {
                erreur.setText(e.getMessage());
                erreur.setVisibility(View.VISIBLE);
            }
        });

        dialogue.setContentView(racine);
        Window fenetre = dialogue.getWindow();
        if (fenetre != null) {
            fenetre.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            fenetre.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialogue.show();
    }

    private static Bloc bloc(LinearLayout parent, int libelle, int aide, View champ) {
        Context contexte = parent.getContext();
        LinearLayout conteneur = new LinearLayout(contexte);
        conteneur.setOrientation(LinearLayout.VERTICAL);

        TextView etiquette = new TextView(contexte);
        etiquette.setText(libelle);
        etiquette.setTextSize(12f);
        etiquette.setTextColor(contexte.getColor(R.color.bleu_clair));
        etiquette.setPadding(dp(contexte, 4), dp(contexte, 14), 0, dp(contexte, 6));
        conteneur.addView(etiquette);

        conteneur.addView(champ, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView texteAide = new TextView(contexte);
        texteAide.setText(aide);
        texteAide.setTextSize(12f);
        texteAide.setTextColor(contexte.getColor(R.color.texte_discret));
        texteAide.setPadding(dp(contexte, 4), dp(contexte, 6), dp(contexte, 4), 0);
        conteneur.addView(texteAide);

        parent.addView(conteneur, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Bloc(conteneur, etiquette);
    }

    private static GradientDrawable fondChamp(Context contexte) {
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(contexte.getColor(R.color.carte));
        fond.setCornerRadius(dp(contexte, 12));
        fond.setStroke(dp(contexte, 1), contexte.getColor(R.color.texte_discret));
        return fond;
    }

    private static EditText saisie(Context contexte, String valeur, boolean nombre) {
        EditText saisie = new EditText(contexte);
        saisie.setText(valeur);
        saisie.setTextSize(15f);
        saisie.setTextColor(contexte.getColor(R.color.texte));
        saisie.setInputType(nombre
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        saisie.setPadding(dp(contexte, 16), dp(contexte, 14), dp(contexte, 16), dp(contexte, 14));
        saisie.setBackground(fondChamp(contexte));
        return saisie;
    }

    private static TextView choix(Context contexte, String valeur) {
        TextView vue = new TextView(contexte);
        vue.setTextSize(15f);
        vue.setTextColor(contexte.getColor(R.color.texte));
        vue.setPadding(dp(contexte, 16), dp(contexte, 14), dp(contexte, 16), dp(contexte, 14));
        vue.setBackground(fondChamp(contexte));
        afficher(vue, valeur);
        return vue;
    }

    private static void afficher(TextView vue, String valeur) {
        vue.setText(valeur + "   ▾");
    }

    private static void choisir(Context contexte, int titre, String[] valeurs,
                                String[] courant, TextView vue, Runnable apres) {
        new AlertDialog.Builder(contexte)
                .setTitle(T.t(titre))
                .setItems(valeurs, (d, i) -> {
                    courant[0] = valeurs[i];
                    afficher(vue, courant[0]);
                    apres.run();
                })
                .show();
    }

    private static int dp(Context contexte, int valeur) {
        return Math.round(valeur * contexte.getResources().getDisplayMetrics().density);
    }
}
