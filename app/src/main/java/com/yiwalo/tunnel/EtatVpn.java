package com.yiwalo.tunnel;

public enum EtatVpn {
    DECONNECTE(R.string.etat_deconnecte),
    PREPARATION(R.string.etat_preparation),
    CONNEXION(R.string.etat_connexion),
    CONNECTE(R.string.etat_connecte),
    RECONNEXION(R.string.etat_reconnexion),
    ERREUR(R.string.etat_erreur);

    public final int libelle;

    EtatVpn(int libelle) {
        this.libelle = libelle;
    }
}
