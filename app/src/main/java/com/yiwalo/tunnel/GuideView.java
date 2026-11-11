package com.yiwalo.tunnel;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class GuideView extends LinearLayout {

    GuideView(Context ctx, Runnable retour) {
        super(ctx);
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "Guide", retour));

        section(ctx, R.drawable.ic_power, Ui.VERT, "Se connecter",
                "1. Ouvre l’onglet Profils et choisis ta configuration.\n"
                + "2. Reviens à l’accueil et touche le bouton central.\n"
                + "3. Quand l’état passe à « Connecté », l’IP réseau, la latence, la durée "
                + "et les données s’affichent.\n\n"
                + "Le bouton + de l’accueil sert à ajouter une configuration.\n\n"
                + "Si le réseau change ou saute un instant (4G, Wi-Fi, signal faible), l’appli "
                + "vérifie le tunnel et le garde s’il répond encore. Sinon elle se reconnecte "
                + "toute seule, plusieurs fois de suite.\n\n"
                + "Au premier lancement, accepte la demande « Ne pas optimiser la batterie » : "
                + "sans elle, Android peut couper le VPN en veille.", true);

        section(ctx, R.drawable.ic_signal, Ui.VERT, "RECHERCHE D'IP",
                "Il change ton IP en coupant puis en rétablissant le réseau (mode avion), "
                + "jusqu’à tomber sur une IP de ta liste.\n\n"
                + "1. Touche le crayon et écris tes adresses, une par ligne.\n"
                + "2. Règle la pause entre deux essais.\n"
                + "3. Touche Lancer.\n\n"
                + "Écrire 10.143 trouve toutes les IP 10.143.x.x. Une adresse complète est "
                + "exacte, et * remplace un bloc (10.*.16).\n\n"
                + "Une plage s’écrit avec un tiret : 10.203-10.210 teste 10.203, 10.204, "
                + "jusqu’à 10.210. « 10.203 à 10.210 » et 10.203..10.210 marchent aussi. "
                + "Tu peux mélanger plusieurs entrées, séparées par des virgules, des espaces "
                + "ou des lignes.\n\n"
                + "Quand une IP voulue est trouvée, la recherche s’arrête et tu reçois une "
                + "notification avec un carillon. Si tu es déjà sur une bonne IP, l’alerte "
                + "est immédiate.\n\n"
                + "Au premier lancement, Android demande de choisir l’appli comme assistant "
                + "numérique, ou d’activer son service d’accessibilité. C’est nécessaire pour "
                + "couper le mode avion tout seul.", false);

        section(ctx, R.drawable.ic_globe, Ui.BLEU, "Vérification IP",
                "Affiche l’adresse IP que le réseau te donne en ce moment. Utilise-la après "
                + "la Recherche d’IP ou une reconnexion pour confirmer ton IP.", false);

        section(ctx, R.drawable.ic_ping, Ui.ORANGE, "Ping des configurations",
                "Dans l’onglet Profils, touche Ping. Chaque configuration est testée "
                + "avec le moteur, comme pour une vraie connexion, et le temps de réponse "
                + "s’affiche en ms.\n\n"
                + "Le test se fait sur tous les réseaux en même temps : SIM Orange, SIM Moov "
                + "et Wi-Fi. Une configuration est bonne dès qu’elle répond sur l’un d’eux, "
                + "et la ligne indique sur quel réseau. Tu peux donc tester les configurations "
                + "Orange et Moov ensemble.\n\n"
                + "Vert : moins de 150 ms. Orange : moins de 400 ms. Rouge : plus lent. "
                + "Échec : le serveur ne répond pas. Non testable : une SIM n’a pas de réseau "
                + "de données ouvert, donc le résultat n’est pas fiable : ce n’est pas un échec "
                + "et la configuration n’est pas supprimée. Touche une ligne pour voir la cause "
                + "détaillée.\n\n"
                + "Pour tester la 2ᵉ SIM, accorde la permission Téléphone à l’appli (Android 12 "
                + "ou plus) et active si possible l’option « données sur les deux SIM » dans les "
                + "réglages du téléphone.\n\n"
                + "Pour Slow DNS, c’est le résolveur DNS qui est testé. Copier le rapport copie "
                + "tous les résultats, avec les réseaux détectés et l’état des SIM. Supprimer "
                + "les échecs retire les configurations en échec.", false);

        section(ctx, R.drawable.ic_signal, Ui.VIOLET, "Host checker",
                "Écris un hôte (exemple.com) ou un hôte:port (serveur.com:443).\n\n"
                + "Le test vérifie la résolution DNS, la connexion TCP et la réponse HTTPS, "
                + "sur tous les réseaux en même temps : Orange, Moov et Wi-Fi. Le résultat "
                + "principal montre le meilleur réseau, et la liste « Par réseau » donne le "
                + "détail de chacun.\n\n"
                + "Fonctionne : tout répond. Partiel : la connexion s’ouvre mais pas le HTTPS. "
                + "Bloqué : le réseau refuse ou ne répond pas. Avec hôte:port, seul le TCP est "
                + "testé.", false);

        section(ctx, R.drawable.ic_ping, Ui.ORANGE, "Testeur de ports",
                "Indique un hôte (exemple.com) ou une adresse IP, puis touche Scanner les ports. "
                + "Les ports 1 à 1024 et les ports courants (web, proxy, tunnels) sont testés "
                + "automatiquement.\n\n"
                + "Le scan se fait sur tous les réseaux en même temps. Seuls les ports ouverts "
                + "s’affichent, avec leur service, leur temps de réponse en ms (vert : moins de "
                + "100 ms, orange : moins de 300 ms, rouge : plus lent) et le réseau sur lequel "
                + "ils répondent. À la fin, le détail par réseau est affiché.\n\n"
                + "Si un réseau accepte presque tous les ports, c’est un proxy de l’opérateur : "
                + "les résultats ne sont pas fiables. Le scan dure de quelques secondes à "
                + "quelques minutes et consomme peu de données.", false);

        section(ctx, R.drawable.ic_vitesse, Ui.VERT, "Test de vitesse",
                "Mesure ton débit descendant en Mb/s et garde un historique des derniers "
                + "tests. Lance-le connecté pour mesurer le tunnel, ou déconnecté pour mesurer "
                + "ton réseau.", false);

        section(ctx, R.drawable.ic_donnees, Ui.BLEU, "Consommation des données",
                "Le débit en direct de l’application, avec une courbe sur la dernière "
                + "minute.\n\n"
                + "Cette session : volume échangé depuis l’ouverture de l’écran.\n"
                + "Application : total de l’application depuis le dernier redémarrage du "
                + "téléphone.\n"
                + "Réseau mobile : total du téléphone depuis son dernier redémarrage.", false);

        section(ctx, R.drawable.ic_cle, Ui.ROSE, "ID de l’appareil",
                "Chaque appareil a un ID unique. Certaines configurations peuvent être "
                + "réservées à des appareils précis : donne cet ID à la personne qui te "
                + "fournit la configuration.\n\n"
                + "Touche la ligne pour le copier.", false);

        section(ctx, R.drawable.ic_partager, Ui.VERT, "Partager l’application",
                "Envoie l’application elle-même (fichier APK) par Bluetooth, Xender, "
                + "WhatsApp ou Quick Share.\n\n"
                + "Le destinataire ouvre le fichier pour l’installer, après avoir autorisé "
                + "les sources inconnues.", false);

        section(ctx, R.drawable.ic_vitesse, Ui.ORANGE, "Hysteria2 / UDP Custom",
                "Dans Plus > Ajouter manuellement, touche Hysteria2 ou UDP Custom : les deux "
                + "ouvrent le même formulaire.\n\n"
                + "1. Nom : celui qui s’affiche dans Profils.\n"
                + "2. Adresse et port : ceux de ton serveur Hysteria2 (port UDP, souvent 443).\n"
                + "3. Mot de passe : celui de ton serveur.\n"
                + "4. SNI : le nom TLS du serveur. Vide, l’adresse est utilisée.\n"
                + "5. Obfs : seulement si ton serveur l’exige.\n"
                + "6. Certificat non vérifié : à cocher seulement pour un serveur de confiance "
                + "avec un certificat auto-signé.\n\n"
                + "Il faut un serveur Hysteria2. Un compte SSH (IP:22@utilisateur:mot de passe) "
                + "ne marche pas ici : utilise le formulaire SSH.", false);

        section(ctx, R.drawable.ic_ping, Ui.ORANGE, "Configurations liées à un opérateur",
                "Beaucoup de configurations ne marchent que sur un opérateur précis. Une "
                + "configuration Moov répond sur la SIM Moov et échoue sur Orange, et "
                + "inversement. Le Ping les teste sur chaque réseau : un échec sur l’autre SIM "
                + "est normal.\n\n"
                + "Pour utiliser une configuration, mets la data mobile sur la bonne SIM et "
                + "vérifie que ton forfait est actif.", false);

        section(ctx, R.drawable.ic_globe, Ui.ORANGE, "Partage de connexion",
                "Prête ton VPN à un autre appareil (téléphone, ordinateur, tablette) par le "
                + "point d’accès Wi-Fi, sans root.\n\n"
                + "1. Allume le point d’accès Wi-Fi dans les réglages du téléphone.\n"
                + "2. Dans Plus > Outils > Partage de connexion, active le partage : l’adresse à saisir s’affiche.\n"
                + "3. Connecte ton VPN (reconnecte-le s’il l’était déjà).\n"
                + "4. Sur l’autre appareil, connecte-toi au Wi-Fi de ton téléphone, puis "
                + "ouvre les réglages de ce réseau > Proxy > Manuel. Saisis l’adresse du "
                + "point d’accès (souvent 192.168.x.1) et le port affiché dans l’appli "
                + "(8080 en général).\n\n"
                + "Si ton VPN est coupé ou change, le partage s’arrête avec lui. Après avoir "
                + "activé ou modifié le partage, reconnecte le VPN.\n\n"
                + "Le mot de passe est désactivé par défaut : le Wi-Fi du point d’accès "
                + "protège déjà l’accès. Tu peux l’activer pour un iPhone ou un ordinateur "
                + "(l’identifiant est yiwalo-tunnel). Les téléphones Android ne gèrent pas "
                + "l’identifiant dans le proxy Wi-Fi : laisse-le désactivé pour eux.\n\n"
                + "Le trafic du partage (reçu et envoyé) s’affiche en direct. Il ne compte "
                + "que les appareils branchés sur ton point d’accès.\n\n"
                + "Les applications qui ignorent le proxy (certains jeux, services en UDP) "
                + "ne passent pas par le VPN. Active le partage seulement quand tu l’utilises : "
                + "l’autre appareil consomme ton forfait.", false);

        section(ctx, R.drawable.ic_info, Ui.DISCRET, "Si la connexion échoue ou se coupe",
                "1. Lance la Recherche d’IP pour obtenir une bonne IP.\n"
                + "2. Teste tes configurations avec Ping et choisis une configuration verte. "
                + "Vérifie ensuite ton serveur avec le Host checker.\n"
                + "3. Réglages Android > Applications > Yiwalo > Batterie : choisis « Sans "
                + "restriction », et verrouille l’appli dans les applications récentes.\n"
                + "4. Si ça se coupe encore, ouvre Journaux juste après la coupure : les "
                + "dernières lignes donnent la cause.\n"
                + "5. Vérifie ton forfait et ta couverture réseau.", false);
    }

    private void section(Context c, int icone, int teinte, String titre, String corps,
                         boolean ouvert) {
        LinearLayout carte = Ui.carte(c);
        final TextView body = Ui.texte(c, corps, 14f, 0xFFCBD5E1, false);
        body.setLineSpacing(0, 1.2f);
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 16));
        body.setVisibility(ouvert ? VISIBLE : GONE);
        final View[] fin = new View[1];
        final boolean[] etat = {ouvert};
        View tete = Ui.rangee(c, icone, teinte, titre, "", () -> {
            etat[0] = !etat[0];
            body.setVisibility(etat[0] ? VISIBLE : GONE);
            fin[0].setRotation(etat[0] ? 90 : 0);
        });
        fin[0] = ((ViewGroup) tete).getChildAt(2);
        fin[0].setRotation(ouvert ? 90 : 0);
        carte.addView(tete);
        carte.addView(body);
        addView(carte);
    }
}
