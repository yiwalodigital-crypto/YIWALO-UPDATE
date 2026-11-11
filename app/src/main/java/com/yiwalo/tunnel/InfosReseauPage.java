package com.yiwalo.tunnel;

import android.content.Context;
import android.widget.LinearLayout;

/** Page « Informations réseau » du menu Outils. */
final class InfosReseauPage extends LinearLayout {
    InfosReseauPage(Context ctx, Runnable retour) {
        super(ctx);
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "Informations réseau", retour));
        addView(new InfosReseauView(ctx, true));
    }
}
