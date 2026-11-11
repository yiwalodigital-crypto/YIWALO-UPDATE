package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.TypeConnexion;
import com.yiwalo.tunnel.ip.ChercheurIpLocal;

public class PlusView extends ScrollView {
    private final Context ctx;
    private final LinearLayout menu;
    /** Vrai juste après un changement de thème ou de langue : on revient directement dans Réglages. */
    static boolean rouvrirReglages = false;

    public PlusView(final Context ctx) {
        super(ctx);
        this.ctx = ctx;
        ctx.getSharedPreferences("yiwalo_reglages", Context.MODE_PRIVATE)
                .edit().remove("reconnexion_auto").apply();
        menu = new LinearLayout(ctx);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 22), Ui.dp(ctx, 18), Ui.dp(ctx, 32));
        addView(menu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        construire();
        if (rouvrirReglages) {
            rouvrirReglages = false;
            page(new ReglagesView(ctx, this::retourMenu));
        }
    }

    private void page(View v) {
        removeAllViews();
        addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollTo(0, 0);
    }

    private void retourMenu() {
        removeAllViews();
        construire();
        addView(menu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollTo(0, 0);
    }

    private void naviguer(int onglet) {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).afficher(onglet);
    }

    private void copier(String libelle, String valeur, String message) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(libelle, valeur));
        T.toast(ctx, message, Toast.LENGTH_SHORT).show();
    }

    private void construire() {
        menu.removeAllViews();
        final Runnable retour = this::retourMenu;
        menu.addView(Ui.texte(ctx, "Menu", 28f, Ui.TEXTE, true));


        menu.addView(Ui.section(ctx, "NAVIGATION"));
        LinearLayout nav = Ui.carte(ctx);
        nav.addView(Ui.rangee(ctx, R.drawable.ic_accueil, Ui.VERT, "Accueil",
                "", () -> naviguer(0)));
        nav.addView(Ui.separateur(ctx));
        nav.addView(Ui.rangee(ctx, R.drawable.ic_configurations, Ui.BLEU, "Profils",
                "", () -> naviguer(1)));
        nav.addView(Ui.separateur(ctx));
        nav.addView(Ui.rangee(ctx, R.drawable.ic_abonnements, Ui.VIOLET, "Abonnements",
                "", () -> naviguer(2)));
        nav.addView(Ui.separateur(ctx));
        nav.addView(Ui.rangee(ctx, R.drawable.ic_reglages, Ui.ORANGE, "Réglages", "",
                () -> page(new ReglagesView(ctx, retour))));
        menu.addView(nav);

        menu.addView(Ui.section(ctx, "AJOUTER MANUELLEMENT"));
        LinearLayout ajout = Ui.carte(ctx);
        final ConfigStore magasin = new ConfigStore(ctx);
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_modifier, Ui.BLEU, "V2ray", "",
                () -> new ConfigurationsView(ctx, magasin).afficherSaisie()));
        ajout.addView(Ui.separateur(ctx));
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_configurations, Ui.VERT, "SSH", "",
                () -> FormulaireSsh.ouvrir(ctx, magasin, null, () -> naviguer(1))));
        ajout.addView(Ui.separateur(ctx));
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_globe, Ui.VIOLET, "SlowDNS", "",
                () -> FormulaireSlowDns.ouvrir(ctx, magasin, null, () -> naviguer(1))));
        ajout.addView(Ui.separateur(ctx));
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_vitesse, Ui.ORANGE, "Hysteria2", "",
                () -> FormulaireQuic.ouvrir(ctx, magasin, null, TypeConnexion.HYSTERIA2,
                        () -> naviguer(1))));
        ajout.addView(Ui.separateur(ctx));
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_vitesse, Ui.BLEU, "TUIC", "",
                () -> FormulaireQuic.ouvrir(ctx, magasin, null, TypeConnexion.TUIC,
                        () -> naviguer(1))));
        ajout.addView(Ui.separateur(ctx));
        ajout.addView(Ui.rangee(ctx, R.drawable.ic_cle, Ui.ROSE, "UDP Custom", "",
                () -> FormulaireQuic.ouvrir(ctx, magasin, null, TypeConnexion.HYSTERIA2,
                        () -> naviguer(1))));
        menu.addView(ajout);

        menu.addView(Ui.section(ctx, "OUTILS"));
        LinearLayout outils = Ui.carte(ctx);
        outils.addView(Ui.rangee(ctx, R.drawable.ic_globe, Ui.ORANGE, "Partage de connexion", "",
                () -> page(new PartageView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_signal, Ui.VERT, "RECHERCHE D'IP",
                "",
                () -> page(new ChercheurIpView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_globe, Ui.BLEU, "Vérification IP", "", () -> page(new VerificationIpView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_signal, Ui.BLEU, "Informations réseau", "", () -> page(new InfosReseauPage(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_signal, Ui.VIOLET, "Host checker", "", () -> page(new HostCheckerView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_donnees, Ui.BLEU, "Consommation des données",
                "",
                () -> page(new ConsommationView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_vitesse, Ui.VERT, "Test de vitesse", "",
                () -> page(new VitesseView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_ping, Ui.ORANGE, "Testeur de ports",
                "",
                () -> page(new PortsView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_configurations, Ui.VIOLET,
                "Tunnel par application", "",
                () -> page(new TunnelAppsView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_globe, Ui.ROSE, "Test de fuite DNS", "",
                () -> page(new DnsView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        String id;
        try {
            id = PartageConfig.idAppareil(ctx);
        } catch (RuntimeException e) {
            id = "indisponible";
        }
        final String idF = id;
        outils.addView(Ui.rangee(ctx, R.drawable.ic_cle, Ui.ROSE, "ID de l’appareil", idF,
                R.drawable.ic_copier, () -> copier("ID appareil", idF, "ID copié")));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_partager, Ui.VERT, "Partager l’application",
                "",
                () -> PartageApk.partager(ctx)));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_guide, Ui.BLEU, "Guide", "",
                () -> page(new GuideView(ctx, retour))));
        outils.addView(Ui.separateur(ctx));
        outils.addView(Ui.rangee(ctx, R.drawable.ic_info, Ui.DISCRET, "À propos",
                "",
                () -> page(new AProposView(ctx, retour))));
        menu.addView(outils);
    }
}
