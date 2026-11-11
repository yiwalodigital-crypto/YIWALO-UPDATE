package com.yiwalo.tunnel;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwalo.tunnel.ip.ListeIp;
import com.yiwalo.tunnel.ip.ReseauMobile;
import com.yiwalo.tunnel.ip.StockageIp;
import com.yiwalo.tunnel.ip.VerificateurIp;

import java.util.ArrayList;
import java.util.List;

public class IpCheckerView extends ScrollView {

    private final StockageIp stockage;
    private final VerificateurIp verificateur = VerificateurIp.instance();
    private final List<String> cibles = new ArrayList<>();
    private int delai;

    private TextView texteEtat;
    private TextView texteOperateur;
    private TextView texteIp;
    private TextView texteTentatives;
    private TextView texteMessage;
    private TextView texteHistorique;
    private TextView texteErreur;
    private EditText saisie;
    private LinearLayout conteneurListe;
    private TextView puce3;
    private TextView puce5;
    private TextView boutonLancer;

    private final Runnable ecouteur = this::rafraichir;
    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            rafraichir();
            postDelayed(this, 1000);
        }
    };

    public IpCheckerView(Context contexte) {
        super(contexte);
        stockage = new StockageIp(contexte);
        cibles.addAll(stockage.lister());
        delai = stockage.delai();
        setFillViewport(true);

        int discret = contexte.getColor(R.color.texte_discret);
        int carteCouleur = contexte.getColor(R.color.carte);
        int bleu = contexte.getColor(R.color.bleu);
        int bleuClair = contexte.getColor(R.color.bleu_clair);

        LinearLayout colonne = new LinearLayout(contexte);
        colonne.setOrientation(LinearLayout.VERTICAL);
        colonne.setPadding(dp(16), dp(4), dp(16), dp(24));
        addView(colonne, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titre = texte(contexte, "IP Checker", 20f,
                contexte.getColor(R.color.texte), true);
        titre.setGravity(Gravity.CENTER);
        colonne.addView(titre, largeur(0));
        TextView sousTitre = texte(contexte,
                "Recherche d’une IP réseau mobile (Orange BF, Moov Africa)", 12f, discret, false);
        sousTitre.setGravity(Gravity.CENTER);
        colonne.addView(sousTitre, largeur(2));

        LinearLayout carte = new LinearLayout(contexte);
        carte.setOrientation(LinearLayout.VERTICAL);
        carte.setGravity(Gravity.CENTER_HORIZONTAL);
        carte.setPadding(dp(16), dp(16), dp(16), dp(16));
        carte.setBackground(arrondi(carteCouleur, 16, 0, 0));
        texteEtat = texte(contexte, "", 24f, discret, true);
        texteEtat.setGravity(Gravity.CENTER);
        carte.addView(texteEtat, largeur(0));
        texteOperateur = texte(contexte, "", 14f, contexte.getColor(R.color.cyan), false);
        texteOperateur.setGravity(Gravity.CENTER);
        carte.addView(texteOperateur, largeur(10));
        texteIp = texte(contexte, "", 24f, bleuClair, true);
        texteIp.setGravity(Gravity.CENTER);
        carte.addView(texteIp, largeur(0));
        texteTentatives = texte(contexte, "", 14f,
                contexte.getColor(R.color.violet_clair), false);
        texteTentatives.setGravity(Gravity.CENTER);
        carte.addView(texteTentatives, largeur(8));
        texteMessage = texte(contexte, "", 13f, discret, false);
        texteMessage.setGravity(Gravity.CENTER);
        carte.addView(texteMessage, largeur(8));
        colonne.addView(carte, largeur(14));

        colonne.addView(texte(contexte, "ADRESSES À RECHERCHER", 12f, bleuClair, true),
                largeur(18));

        LinearLayout ajout = new LinearLayout(contexte);
        ajout.setOrientation(LinearLayout.HORIZONTAL);
        ajout.setGravity(Gravity.CENTER_VERTICAL);
        saisie = new EditText(contexte);
        saisie.setHint("10.197.*.* ou 10.203-10.210");
        saisie.setHintTextColor(discret);
        saisie.setTextColor(contexte.getColor(R.color.texte));
        saisie.setTextSize(15f);
        saisie.setSingleLine(true);
        saisie.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        saisie.setPadding(dp(14), dp(12), dp(14), dp(12));
        saisie.setBackground(arrondi(carteCouleur, 12, 1, discret));
        ajout.addView(saisie, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView boutonAjouter = bouton(contexte, "Ajouter", bleu, Color.WHITE);
        boutonAjouter.setOnClickListener(v -> ajouter());
        LinearLayout.LayoutParams pAjouter = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pAjouter.leftMargin = dp(8);
        ajout.addView(boutonAjouter, pAjouter);
        colonne.addView(ajout, largeur(6));

        texteErreur = texte(contexte, "", 12f, contexte.getColor(R.color.rouge), false);
        texteErreur.setVisibility(GONE);
        colonne.addView(texteErreur, largeur(4));

        conteneurListe = new LinearLayout(contexte);
        conteneurListe.setOrientation(LinearLayout.VERTICAL);
        colonne.addView(conteneurListe, largeur(4));

        LinearLayout rangeeDelai = new LinearLayout(contexte);
        rangeeDelai.setOrientation(LinearLayout.HORIZONTAL);
        rangeeDelai.setGravity(Gravity.CENTER_VERTICAL);
        rangeeDelai.addView(texte(contexte, "Attendre avant de vérifier", 14f,
                contexte.getColor(R.color.texte), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        puce3 = bouton(contexte, "3 s", carteCouleur, discret);
        puce3.setOnClickListener(v -> choisirDelai(3));
        puce5 = bouton(contexte, "5 s", carteCouleur, discret);
        puce5.setOnClickListener(v -> choisirDelai(5));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p3.leftMargin = dp(8);
        LinearLayout.LayoutParams p5 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p5.leftMargin = dp(8);
        rangeeDelai.addView(puce3, p3);
        rangeeDelai.addView(puce5, p5);
        colonne.addView(rangeeDelai, largeur(16));

        boutonLancer = bouton(contexte, "", bleu, Color.WHITE);
        boutonLancer.setTextSize(16f);
        boutonLancer.setOnClickListener(v -> lancerOuArreter());
        colonne.addView(boutonLancer, largeur(16));

        TextView boutonAvion = bouton(contexte, "Ouvrir le mode avion", carteCouleur, bleuClair);
        boutonAvion.setBackground(arrondi(carteCouleur, 12, 1, bleu));
        boutonAvion.setOnClickListener(v -> ouvrirModeAvion());
        colonne.addView(boutonAvion, largeur(10));

        texteHistorique = texte(contexte, "", 12f, discret, false);
        texteHistorique.setTypeface(Typeface.MONOSPACE);
        colonne.addView(texteHistorique, largeur(14));

        colonne.addView(texte(contexte,
                "Android ne permet pas à une application de couper le réseau mobile. À chaque "
                        + "tentative, activez puis désactivez le mode avion (panneau rapide). "
                        + "L’application détecte le retour du réseau, attend 3 ou 5 secondes, "
                        + "lit l’IP du réseau mobile et la compare à votre liste. Désactivez le "
                        + "Wi-Fi et déconnectez le VPN avant de commencer.",
                11f, discret, false), largeur(10));

        reconstruireListe();
        rafraichir();
    }

    private void rafraichir() {
        Context c = getContext();
        VerificateurIp.Etat etat = verificateur.etat();
        boolean actif = etat == VerificateurIp.Etat.EN_ATTENTE;

        int couleur;
        String libelle;
        switch (etat) {
            case TROUVEE:
                couleur = c.getColor(R.color.vert);
                libelle = "IP TROUVÉE";
                break;
            case EN_ATTENTE:
                couleur = c.getColor(R.color.bleu_clair);
                libelle = "RECHERCHE EN COURS";
                break;
            default:
                couleur = c.getColor(R.color.texte_discret);
                libelle = "ARRÊTÉ";
                break;
        }
        texteEtat.setText(libelle);
        texteEtat.setTextColor(couleur);

        String ip = null;
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                ip = ReseauMobile.ipv4Mobile(cm);
            }
        } catch (RuntimeException ignore) {
            // réseau indisponible : on affiche un tiret
        }
        String operateur = ReseauMobile.operateur(c);
        texteOperateur.setText(operateur != null ? operateur : "Opérateur inconnu");
        texteIp.setText(ip != null ? ip : "—");
        texteTentatives.setText("Tentatives : " + verificateur.tentatives());

        String message;
        if (etat == VerificateurIp.Etat.TROUVEE) {
            message = "Adresse " + verificateur.ipCourante() + " (liste : "
                    + verificateur.motifTrouve() + "). Recherche arrêtée.";
        } else if (actif) {
            message = verificateur.message();
        } else if (ip == null) {
            message = "Aucun réseau mobile détecté. Désactivez le Wi-Fi et activez les "
                    + "données mobiles.";
        } else {
            message = "Ajoutez des adresses puis lancez la recherche.";
        }
        texteMessage.setText(message);

        List<String> lues = verificateur.historique();
        texteHistorique.setText(lues.isEmpty() ? ""
                : "Dernières IP lues : " + String.join(" · ", lues));

        boutonLancer.setText(actif ? "Arrêter la recherche"
                : (etat == VerificateurIp.Etat.TROUVEE ? "Nouvelle recherche"
                : "Lancer la recherche"));
        boutonLancer.setBackground(arrondi(
                c.getColor(actif ? R.color.rouge : R.color.bleu), 12, 0, 0));
        styliserPuce(puce3, delai == 3);
        styliserPuce(puce5, delai == 5);
    }

    private void styliserPuce(TextView puce, boolean choisie) {
        Context c = getContext();
        puce.setTextColor(choisie ? Color.WHITE : c.getColor(R.color.texte_discret));
        puce.setBackground(arrondi(
                c.getColor(choisie ? R.color.bleu : R.color.carte), 12, 0, 0));
    }

    private void reconstruireListe() {
        Context c = getContext();
        conteneurListe.removeAllViews();
        if (cibles.isEmpty()) {
            TextView vide = texte(c, "Aucune adresse. Ajoutez-en une ci-dessus.", 13f,
                    c.getColor(R.color.texte_discret), false);
            vide.setPadding(dp(4), dp(8), dp(4), dp(8));
            conteneurListe.addView(vide, largeur(0));
            return;
        }
        for (String ip : new ArrayList<>(cibles)) {
            LinearLayout ligne = new LinearLayout(c);
            ligne.setOrientation(LinearLayout.HORIZONTAL);
            ligne.setGravity(Gravity.CENTER_VERTICAL);
            ligne.setPadding(dp(14), dp(10), dp(8), dp(10));
            ligne.setBackground(arrondi(c.getColor(R.color.carte), 12, 0, 0));
            ligne.addView(texte(c, ip, 16f, c.getColor(R.color.cyan), true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView retirer = texte(c, "Retirer", 13f, c.getColor(R.color.rouge), false);
            retirer.setPadding(dp(12), dp(8), dp(12), dp(8));
            retirer.setOnClickListener(v -> retirer(ip));
            ligne.addView(retirer);
            conteneurListe.addView(ligne, largeur(6));
        }
    }

    private boolean modificationPossible() {
        if (verificateur.etat() == VerificateurIp.Etat.EN_ATTENTE) {
            erreur("Arrêtez la recherche pour modifier la liste ou le délai.");
            return false;
        }
        return true;
    }

    private void erreur(String texte) {
        texteErreur.setText(texte);
        texteErreur.setVisibility(VISIBLE);
    }

    private void ajouter() {
        if (!modificationPossible()) {
            return;
        }
        ListeIp.Extraction extraction = ListeIp.extraire(saisie.getText().toString());
        if (extraction.valides.isEmpty() && extraction.invalides.isEmpty()) {
            erreur("Saisissez une adresse IP.");
            return;
        }
        boolean change = false;
        for (String ip : extraction.valides) {
            if (!cibles.contains(ip)) {
                cibles.add(ip);
                change = true;
            }
        }
        if (change) {
            stockage.enregistrer(cibles);
        }
        if (extraction.invalides.isEmpty()) {
            texteErreur.setVisibility(GONE);
            saisie.setText("");
        } else {
            erreur("Adresse invalide : " + String.join(", ", extraction.invalides)
                    + ". Format : 10.197.158.178, 10.197.*.* ou 10.203-10.210");
            saisie.setText(String.join(" ", extraction.invalides));
        }
        reconstruireListe();
    }

    private void retirer(String ip) {
        if (!modificationPossible()) {
            return;
        }
        cibles.remove(ip);
        stockage.enregistrer(cibles);
        texteErreur.setVisibility(GONE);
        reconstruireListe();
    }

    private void choisirDelai(int secondes) {
        if (!modificationPossible()) {
            return;
        }
        delai = secondes;
        stockage.definirDelai(secondes);
        rafraichir();
    }

    private void lancerOuArreter() {
        if (verificateur.etat() == VerificateurIp.Etat.EN_ATTENTE) {
            verificateur.arreter();
        } else {
            String probleme = verificateur.demarrer(getContext(), cibles, delai);
            if (probleme != null) {
                T.toast(getContext(), probleme, Toast.LENGTH_LONG).show();
            }
        }
        rafraichir();
    }

    private void ouvrirModeAvion() {
        Context c = getContext();
        try {
            c.startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        } catch (RuntimeException e) {
            T.toast(c, "Ouvrez le panneau rapide d’Android et touchez « Mode avion ».",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        verificateur.ajouterEcouteur(ecouteur);
        post(tic);
    }

    @Override
    protected void onDetachedFromWindow() {
        verificateur.retirerEcouteur(ecouteur);
        removeCallbacks(tic);
        super.onDetachedFromWindow();
    }

    private TextView texte(Context c, String contenu, float taille, int couleur, boolean gras) {
        TextView t = new TextView(c);
        t.setText(contenu);
        t.setTextSize(taille);
        t.setTextColor(couleur);
        if (gras) {
            t.setTypeface(t.getTypeface(), Typeface.BOLD);
        }
        return t;
    }

    private TextView bouton(Context c, String contenu, int fond, int couleurTexte) {
        TextView t = texte(c, contenu, 14f, couleurTexte, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setBackground(arrondi(fond, 12, 0, 0));
        return t;
    }

    private GradientDrawable arrondi(int fond, int rayonDp, int contourDp, int couleurContour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fond);
        d.setCornerRadius(dp(rayonDp));
        if (contourDp > 0) {
            d.setStroke(dp(contourDp), couleurContour);
        }
        return d;
    }

    private LinearLayout.LayoutParams largeur(int margeHautDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(margeHautDp);
        return p;
    }

    private int dp(int valeur) {
        return Math.round(valeur * getResources().getDisplayMetrics().density);
    }
}
