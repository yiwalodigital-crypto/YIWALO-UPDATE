package com.yiwalo.tunnel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Choix des applications qui passent (ou non) par le tunnel. */
final class TunnelAppsView extends ScrollView {
    static final String PREFS = "yiwalo_tunnel_apps";
    static final String MODE = "mode";
    static final String APPS = "apps";
    static final String MODE_AUCUN = "aucun";
    static final String MODE_EXCLURE = "exclure";
    static final String MODE_INCLURE = "inclure";

    private static final class Item {
        final String paquet;
        final String nom;
        final Drawable icone;

        Item(String paquet, String nom, Drawable icone) {
            this.paquet = paquet;
            this.nom = nom;
            this.icone = icone;
        }
    }

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Set<String> choisies = new HashSet<>();
    private final List<Item> tous = new ArrayList<>();
    private String mode;
    private String filtre = "";
    private boolean detache;

    private LinearLayout modes;
    private TextView compteur;
    private LinearLayout listeApps;

    TunnelAppsView(Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mode = prefs.getString(MODE, MODE_AUCUN);
        Set<String> deja = prefs.getStringSet(APPS, null);
        if (deja != null) choisies.addAll(deja);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(24));
        addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.entete(ctx, "Tunnel par application", retour));
        col.addView(Ui.texte(ctx, "Choisis quelles applications passent par le VPN. "
                + "Le changement s’applique à la prochaine connexion.",
                13f, Ui.DISCRET, false), marge(4));

        modes = new LinearLayout(ctx);
        modes.setOrientation(LinearLayout.VERTICAL);
        col.addView(modes, marge(12));
        dessinerModes();

        EditText recherche = Ui.champ(ctx, "Rechercher une application", "");
        col.addView(recherche, marge(14));
        recherche.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                filtre = s.toString().trim().toLowerCase(Locale.ROOT);
                afficher();
            }
        });

        compteur = Ui.texte(ctx, "", 12f, Ui.DISCRET, true);
        col.addView(compteur, marge(14));

        listeApps = Ui.carte(ctx);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) listeApps.getLayoutParams();
        lp.topMargin = dp(8);
        col.addView(listeApps);

        col.setFocusableInTouchMode(true);
        col.requestFocus();
        majCompteur();
        charger();
    }

    private int dp(int v) {
        return Ui.dp(ctx, v);
    }

    private LinearLayout.LayoutParams marge(int haut) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(haut);
        return p;
    }

    private void sauver() {
        prefs.edit().putString(MODE, mode)
                .putStringSet(APPS, new HashSet<>(choisies)).apply();
    }

    private void majCompteur() {
        int n = choisies.size();
        String s = n + (n > 1 ? " applications cochées" : " application cochée");
        if (MODE_AUCUN.equals(mode)) s += " · sélection inactive";
        compteur.setText(s);
    }

    private void dessinerModes() {
        modes.removeAllViews();
        modes.addView(puce(MODE_AUCUN, "Toutes les applications",
                "Tout le trafic passe par le VPN"), marge(0));
        modes.addView(puce(MODE_EXCLURE, "Exclure les applications cochées",
                "Elles utilisent Internet sans le VPN"), marge(8));
        modes.addView(puce(MODE_INCLURE, "Seulement les applications cochées",
                "Les autres utilisent Internet sans le VPN"), marge(8));
    }

    private View puce(final String m, String titre, String sous) {
        boolean actif = m.equals(mode);
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(actif ? Ui.BLEU : Ui.FOND);
        g.setStroke(dp(1), actif ? Ui.BLEU : Ui.BORD);
        c.setBackground(g);
        c.addView(Ui.texte(ctx, titre, 15f, actif ? 0xFFFFFFFF : Ui.TEXTE, true));
        c.addView(Ui.texte(ctx, sous, 12f, actif ? 0xFFE0ECFF : Ui.DISCRET, false));
        c.setClickable(true);
        c.setOnClickListener(v -> {
            mode = m;
            sauver();
            dessinerModes();
            majCompteur();
        });
        return c;
    }

    @SuppressWarnings("deprecation")
    private void charger() {
        listeApps.removeAllViews();
        TextView att = Ui.texte(ctx, "Chargement des applications…", 14f, Ui.DISCRET, false);
        att.setPadding(dp(16), dp(16), dp(16), dp(16));
        listeApps.addView(att);
        new Thread(() -> {
            PackageManager pm = ctx.getPackageManager();
            Intent it = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            final List<Item> res = new ArrayList<>();
            try {
                Set<String> vus = new HashSet<>();
                for (ResolveInfo ri : pm.queryIntentActivities(it, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(ctx.getPackageName()) || !vus.add(pkg)) continue;
                    res.add(new Item(pkg, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
                }
            } catch (RuntimeException ignore) {
                // liste indisponible : écran vide
            }
            Collections.sort(res, (a, b) -> a.nom.compareToIgnoreCase(b.nom));
            h.post(() -> {
                if (detache) return;
                tous.clear();
                tous.addAll(res);
                afficher();
            });
        }, "yiwalo-apps").start();
    }

    private void afficher() {
        listeApps.removeAllViews();
        int n = 0;
        for (Item it : tous) {
            if (!filtre.isEmpty()
                    && !it.nom.toLowerCase(Locale.ROOT).contains(filtre)
                    && !it.paquet.toLowerCase(Locale.ROOT).contains(filtre)) {
                continue;
            }
            if (n++ > 0) listeApps.addView(Ui.separateur(ctx));
            listeApps.addView(ligne(it));
        }
        if (n == 0) {
            TextView vide = Ui.texte(ctx, "Aucune application trouvée.", 14f, Ui.DISCRET, false);
            vide.setPadding(dp(16), dp(16), dp(16), dp(16));
            listeApps.addView(vide);
        }
    }

    private View ligne(final Item it) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(12), dp(8), dp(12), dp(8));

        ImageView ic = new ImageView(ctx);
        ic.setImageDrawable(it.icone);
        l.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.texte(ctx, it.nom, 15f, Ui.TEXTE, true));
        t.addView(Ui.texte(ctx, it.paquet, 11f, Ui.DISCRET, false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(12);
        l.addView(t, tp);

        final CheckBox cb = new CheckBox(ctx);
        cb.setButtonTintList(ColorStateList.valueOf(Ui.BLEU));
        cb.setChecked(choisies.contains(it.paquet));
        cb.setOnCheckedChangeListener((b, coche) -> {
            if (coche) choisies.add(it.paquet); else choisies.remove(it.paquet);
            sauver();
            majCompteur();
        });
        l.addView(cb);
        l.setOnClickListener(v -> cb.toggle());
        return l;
    }

    @Override protected void onDetachedFromWindow() {
        detache = true;
        super.onDetachedFromWindow();
    }
}
