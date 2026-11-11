package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.yiwalo.tunnel.vpn.PartageConnexion;
import java.util.List;

final class PartageView extends LinearLayout {
    private final Context ctx;
    private final Runnable retour;
    private TextView textStats;
    private Thread fil;

    PartageView(Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        this.retour = retour;
        setOrientation(VERTICAL);
        construire();
    }

    private void copier(String libelle, String valeur) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(libelle, valeur));
        }
        T.toast(ctx, libelle + " copié", Toast.LENGTH_SHORT).show();
    }

    private void ligne(LinearLayout carte, final String titre, final String valeur, boolean copiable) {
        if (carte.getChildCount() > 0) {
            carte.addView(Ui.separateur(ctx));
        }
        carte.addView(Ui.rangee(ctx, R.drawable.ic_signal, Ui.BLEU, titre, valeur,
                () -> {
                    if (copiable) {
                        copier(titre, valeur);
                    }
                }));
    }

    private String formaterStats() {
        return "Reçu : " + octets(com.yiwalo.tunnel.vpn.StatsPartage.recu)
                + "     Envoyé : " + octets(com.yiwalo.tunnel.vpn.StatsPartage.envoye);
    }

    private static String octets(long n) {
        java.util.Locale fr = java.util.Locale.FRANCE;
        if (n < 1024) return n + " o";
        if (n < 1048576L) return String.format(fr, "%.1f Ko", n / 1024.0);
        if (n < 1073741824L) return String.format(fr, "%.1f Mo", n / 1048576.0);
        return String.format(fr, "%.2f Go", n / 1073741824.0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        fil = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (PartageConnexion.actif(ctx) && !PartageConnexion.pointAccesActif(ctx)) {
                    PartageConnexion.definirActif(ctx, false);
                    post(() -> construire());
                }
                if (PartageConnexion.actif(ctx) && SuiviVpn.etat() == EtatVpn.CONNECTE) {
                    com.yiwalo.tunnel.vpn.StatsPartage.lire(ctx);
                } else {
                    com.yiwalo.tunnel.vpn.StatsPartage.recu = 0;
                    com.yiwalo.tunnel.vpn.StatsPartage.envoye = 0;
                }
                post(() -> {
                    if (textStats != null) {
                        textStats.setText(formaterStats());
                    }
                });
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "yiwalo-stats-partage");
        fil.setDaemon(true);
        fil.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fil != null) {
            fil.interrupt();
            fil = null;
        }
        super.onDetachedFromWindow();
    }

    private void construire() {
        removeAllViews();
        textStats = null;
        addView(Ui.entete(ctx, "Partage de connexion", retour));
        final boolean actif = PartageConnexion.actif(ctx);
        final boolean mdp = PartageConnexion.motDePasseActif(ctx);

        LinearLayout reglages = Ui.carte(ctx);
        reglages.addView(Ui.rangee(ctx, R.drawable.ic_globe, Ui.VERT, "Partage par Wi-Fi",
                actif ? "Activé" : "Désactivé", () -> {
                    if (!actif && !PartageConnexion.pointAccesActif(ctx)) {
                        T.toast(ctx, "Active d’abord le point d’accès Wi-Fi",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    PartageConnexion.definirActif(ctx, !actif);
                    construire();
                }));
        reglages.addView(Ui.separateur(ctx));
        reglages.addView(Ui.rangee(ctx, R.drawable.ic_cle, Ui.ROSE, "Protéger par mot de passe",
                mdp ? "Activé" : "Désactivé", () -> {
                    PartageConnexion.definirMotDePasseActif(ctx, !mdp);
                    construire();
                }));
        addView(reglages);

        if (actif) {
            LinearLayout infos = Ui.carte(ctx);
            List<String> adresses = PartageConnexion.adresses();
            if (adresses.isEmpty()) {
                ligne(infos, "Adresse", "Allume le point d’accès Wi-Fi", false);
            } else {
                for (String a : adresses) {
                    ligne(infos, "Adresse", a, true);
                }
            }
            ligne(infos, "Port", String.valueOf(PartageConnexion.portAffiche()), true);
            if (mdp) {
                ligne(infos, "Utilisateur", PartageConnexion.utilisateur(), true);
                ligne(infos, "Mot de passe", PartageConnexion.motDePasse(ctx), true);
            }
            addView(infos);
        }

        if (actif) {
            LinearLayout trafic = Ui.carte(ctx);
            TextView titreStats = Ui.texte(ctx, "Trafic du partage", 16f, Ui.TEXTE, true);
            titreStats.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 2));
            textStats = Ui.texte(ctx, formaterStats(), 15f, 0xFFCBD5E1, false);
            textStats.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 2), Ui.dp(ctx, 16), Ui.dp(ctx, 14));
            trafic.addView(titreStats);
            trafic.addView(textStats);
            addView(trafic);
        }

        TextView aide = Ui.texte(ctx,
                "Ordre à suivre : 1. Allume le point d’accès Wi-Fi dans les réglages du téléphone. 2. Active « Partage par Wi-Fi » ici : l’adresse à saisir s’affiche. 3. Connecte le VPN (reconnecte-le s’il l’était déjà).\n\n"
                + "Sur l’autre appareil : Wi-Fi > réseau de ton téléphone > Proxy > Manuel. "
                + "Saisis l’adresse du point d’accès (souvent 192.168.43.1 ou 192.168.x.1) "
                + "et le port.\n\n"
                + "Android n’a pas de champ identifiant dans le proxy Wi-Fi : sur ce type "
                + "d’appareil, désactive le mot de passe. iPhone et ordinateur le gèrent.\n\n"
                + "Le changement s’applique à la prochaine connexion du VPN : reconnecte-toi "
                + "après l’avoir modifié. Active le partage seulement quand tu l’utilises : "
                + "l’autre appareil consomme ton forfait.", 14f, 0xFFCBD5E1, false);
        aide.setLineSpacing(0, 1.2f);
        aide.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        addView(aide);
    }
}
