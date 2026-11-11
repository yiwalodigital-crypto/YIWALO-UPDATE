package com.yiwalo.tunnel;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.View;
import com.yiwalo.tunnel.config.ConfigStore;

final class ConfigsAvecTest extends LinearLayout {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private final MainActivity act;
    private final ConfigStore store;
    private final LinearLayout barre;

    ConfigsAvecTest(MainActivity act, ConfigStore store) {
        super(act);
        this.act = act;
        this.store = store;
        setOrientation(VERTICAL);
        barre = new LinearLayout(act);
        barre.setOrientation(VERTICAL);
        barre.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), 0);
        afficherListe();
        TestConfigsView.preparerReseaux(act);
    }

    private void afficherListe() {
        removeAllViews();
        if (barre.getParent() != null) ((ViewGroup) barre.getParent()).removeView(barre);
        ConfigurationsView cv = new ConfigurationsView(act, store); cv.surPing = () -> { if (ResultatsPing.enCours()) ResultatsPing.terminer(); else ResultatsPing.lancer(act, store, null); }; View liste = cv;
        if (liste instanceof ScrollView) {
            addView(barre, new LinearLayout.LayoutParams(MATCH, WRAP));
            addView(liste, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        } else {
            LinearLayout col = new LinearLayout(act);
            col.setOrientation(VERTICAL);
            col.addView(barre, new LinearLayout.LayoutParams(MATCH, WRAP));
            col.addView(liste, new LinearLayout.LayoutParams(MATCH, WRAP));
            ScrollView sv = new ScrollView(act);
            sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
            addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        }
    }

    void ouvrirTest() {
        removeAllViews();
        ScrollView sv = new ScrollView(act);
        sv.addView(new TestConfigsView(act, store, this::afficherListe),
                new ViewGroup.LayoutParams(MATCH, WRAP));
        addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
    }
}
