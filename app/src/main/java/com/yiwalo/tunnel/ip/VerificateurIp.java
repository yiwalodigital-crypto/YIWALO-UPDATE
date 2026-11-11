package com.yiwalo.tunnel.ip;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.yiwalo.tunnel.EtatVpn;
import com.yiwalo.tunnel.SuiviVpn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Recherche d'IP réseau mobile. Tout s'exécute sur le fil principal. */
public final class VerificateurIp {

    public enum Etat {
        ARRETE, EN_ATTENTE, TROUVEE
    }

    private static final VerificateurIp UNIQUE = new VerificateurIp();

    public static VerificateurIp instance() {
        return UNIQUE;
    }

    private final Handler principal = new Handler(Looper.getMainLooper());
    private final List<Runnable> ecouteurs = new CopyOnWriteArrayList<>();
    private final List<String> historique = new ArrayList<>();
    private final List<String> cibles = new ArrayList<>();
    private final Runnable verification = this::verifierApresDelai;

    private Context contexte;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback rappel;
    private Etat etat = Etat.ARRETE;
    private String message = "";
    private String ipCourante = null;
    private String motifTrouve = null;
    private int tentatives = 0;
    private int delai = 5;
    private boolean renouvellementAttendu = false;

    private VerificateurIp() {
    }

    public Etat etat() {
        return etat;
    }

    public String message() {
        return message;
    }

    public String ipCourante() {
        return ipCourante;
    }

    public String motifTrouve() {
        return motifTrouve;
    }

    public int tentatives() {
        return tentatives;
    }

    public List<String> historique() {
        return new ArrayList<>(historique);
    }

    public void ajouterEcouteur(Runnable ecouteur) {
        ecouteurs.add(ecouteur);
    }

    public void retirerEcouteur(Runnable ecouteur) {
        ecouteurs.remove(ecouteur);
    }

    private void notifier() {
        for (Runnable r : ecouteurs) {
            principal.post(r);
        }
    }

    /** Lance la recherche. Retourne un message d'erreur, ou null si elle a démarré. */
    public String demarrer(Context ctx, List<String> liste, int delaiSecondes) {
        if (etat == Etat.EN_ATTENTE) {
            return null;
        }
        if (liste.isEmpty()) {
            return "Ajoutez au moins une adresse IP à rechercher.";
        }
        EtatVpn vpn = SuiviVpn.etat();
        if (vpn != EtatVpn.DECONNECTE && vpn != EtatVpn.ERREUR) {
            return "Déconnectez le VPN avant de lancer la recherche.";
        }
        contexte = ctx.getApplicationContext();
        cm = (ConnectivityManager) contexte.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return "Service réseau indisponible.";
        }
        cibles.clear();
        cibles.addAll(liste);
        delai = delaiSecondes == 3 ? 3 : 5;
        tentatives = 0;
        historique.clear();
        motifTrouve = null;
        ipCourante = null;
        renouvellementAttendu = false;

        NetworkRequest requete = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        rappel = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network reseau) {
                reseauPret(reseau);
            }

            @Override
            public void onLinkPropertiesChanged(Network reseau, LinkProperties proprietes) {
                reseauPret(reseau);
            }

            @Override
            public void onLost(Network reseau) {
                reseauPerdu();
            }
        };
        try {
            cm.registerNetworkCallback(requete, rappel, principal);
        } catch (RuntimeException e) {
            rappel = null;
            return "Impossible de surveiller le réseau mobile : " + e.getMessage();
        }
        etat = Etat.EN_ATTENTE;
        message = "Lecture de l’adresse mobile actuelle…";
        notifier();
        verifierMaintenant();
        return null;
    }

    public void arreter() {
        principal.removeCallbacks(verification);
        desinscrire();
        if (etat == Etat.EN_ATTENTE) {
            etat = Etat.ARRETE;
            message = "Recherche arrêtée.";
        }
        notifier();
    }

    private void desinscrire() {
        if (rappel != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(rappel);
            } catch (RuntimeException ignore) {
                // déjà désinscrit
            }
        }
        rappel = null;
    }

    private String lireIp() {
        try {
            return ReseauMobile.ipv4Mobile(cm);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Première lecture, sans attente : l'adresse actuellement attribuée. */
    private void verifierMaintenant() {
        String ip = lireIp();
        if (ip == null) {
            message = "Aucun réseau mobile détecté. Désactivez le Wi-Fi et activez les "
                    + "données mobiles.";
            notifier();
            return;
        }
        ipCourante = ip;
        ajouterHistorique(ip);
        String motif = ListeIp.premiereCorrespondance(ip, cibles);
        if (motif != null) {
            trouve(motif);
        } else {
            message = "Adresse actuelle non trouvée. Activez puis désactivez le mode avion "
                    + "pour la renouveler.";
            notifier();
        }
    }

    private void reseauPret(Network reseau) {
        if (etat != Etat.EN_ATTENTE) {
            return;
        }
        try {
            if (!ReseauMobile.estMobile(cm, reseau)) {
                return;
            }
            String ip = ReseauMobile.ipv4(cm, reseau);
            if (ip == null) {
                return;
            }
            boolean nouvelleAdresse = !ip.equals(ipCourante);
            if (!renouvellementAttendu && !nouvelleAdresse) {
                return;
            }
            message = "Réseau mobile revenu. Vérification dans " + delai + " s…";
            notifier();
            principal.removeCallbacks(verification);
            principal.postDelayed(verification, delai * 1000L);
        } catch (RuntimeException ignore) {
            // réseau disparu pendant la lecture : un nouvel événement suivra
        }
    }

    private void reseauPerdu() {
        if (etat != Etat.EN_ATTENTE) {
            return;
        }
        renouvellementAttendu = true;
        principal.removeCallbacks(verification);
        message = "Réseau mobile coupé. Attente de son retour…";
        notifier();
    }

    private void verifierApresDelai() {
        if (etat != Etat.EN_ATTENTE) {
            return;
        }
        String ip = lireIp();
        if (ip == null) {
            message = "Aucune adresse mobile disponible. Vérifiez les données mobiles.";
            notifier();
            return;
        }
        renouvellementAttendu = false;
        tentatives++;
        ipCourante = ip;
        ajouterHistorique(ip);
        String motif = ListeIp.premiereCorrespondance(ip, cibles);
        if (motif != null) {
            trouve(motif);
        } else {
            message = "Tentative " + tentatives + " : " + ip + " n’est pas dans la liste. "
                    + "Renouvelez à nouveau (mode avion).";
            notifier();
        }
    }

    private void trouve(String motif) {
        etat = Etat.TROUVEE;
        motifTrouve = motif;
        message = "IP TROUVÉE";
        principal.removeCallbacks(verification);
        desinscrire();
        alerter();
        notifier();
    }

    private void ajouterHistorique(String ip) {
        historique.add(ip);
        while (historique.size() > 6) {
            historique.remove(0);
        }
    }

    @SuppressWarnings("deprecation")
    private void alerter() {
        try {
            Vibrator vibreur = (Vibrator) contexte.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibreur != null && vibreur.hasVibrator()) {
                vibreur.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 400, 200, 400, 200, 400}, -1));
            }
        } catch (RuntimeException ignore) {
            // vibration indisponible
        }
        try {
            Ringtone sonnerie = RingtoneManager.getRingtone(contexte,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (sonnerie != null) {
                sonnerie.play();
            }
        } catch (RuntimeException ignore) {
            // son indisponible
        }
    }
}
