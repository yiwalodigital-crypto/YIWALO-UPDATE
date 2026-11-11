package com.yiwalo.tunnel;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class AProposView extends LinearLayout {
    static final String LIEN_GROUPE =
            "https://chat.whatsapp.com/FDANZLCOZhxIfSlF4HQhtN?s=cl&p=a&mlu=4&ilr=4";
    static final String LIEN_NUMERO = "https://wa.me/22652839161";

    @SuppressWarnings("deprecation")
    AProposView(final Context ctx, Runnable retour) {
        super(ctx);
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "À propos", retour));

        String version = "?";
        long build = 0;
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            version = pi.versionName;
            build = pi.versionCode;
        } catch (Exception ignore) {
            // version inconnue
        }

        LinearLayout haut = Ui.carte(ctx);
        haut.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(ctx, 24);
        haut.setPadding(p, p, p, p);
        ImageView logo = new ImageView(ctx);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 72), Ui.dp(ctx, 72)));
        haut.addView(logo);
        TextView nom = Ui.texte(ctx, "YIWALO TUNNEL", 22f, Ui.TEXTE, true);
        nom.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 4));
        haut.addView(nom);
        haut.addView(Ui.texte(ctx, "Version " + version + " (build " + build + ")", 14f,
                Ui.DISCRET, false));
        addView(haut);

        addView(Ui.section(ctx, "INFORMATIONS"));
        LinearLayout d = Ui.carte(ctx);
        d.addView(Ui.info(ctx, "Package", ctx.getPackageName()));
        d.addView(Ui.separateur(ctx));
        d.addView(Ui.info(ctx, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        d.addView(Ui.separateur(ctx));
        d.addView(Ui.info(ctx, "Appareil", Build.MANUFACTURER + " " + Build.MODEL));
        addView(d);

        addView(Ui.section(ctx, "CONTACT"));
        LinearLayout c = Ui.carte(ctx);
        c.addView(Ui.rangee(ctx, R.drawable.ic_partager, Ui.VERT, "Groupe WhatsApp",
                "Rejoindre la communauté", () -> ouvrir(ctx, LIEN_GROUPE)));
        c.addView(Ui.separateur(ctx));
        c.addView(Ui.rangee(ctx, R.drawable.ic_info, Ui.BLEU, "Support WhatsApp",
                "+226 52 83 91 61", () -> ouvrir(ctx, LIEN_NUMERO)));
        addView(c);
    }

    private static void ouvrir(Context ctx, String url) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            T.toast(ctx, "Impossible d’ouvrir le lien", Toast.LENGTH_SHORT).show();
        }
    }
}
