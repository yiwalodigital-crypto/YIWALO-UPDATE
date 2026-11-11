package com.yiwalo.tunnel;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran affiché tant que l'application est verrouillée. */
public class VerrouActivity extends Activity {
    private static final int CODE = 77;

    @Override
    protected void attachBaseContext(Context base) {
        android.content.SharedPreferences p = base.getSharedPreferences(
                "yiwalo_apparence", Context.MODE_PRIVATE);
        boolean sombre = p.getBoolean("sombre", true);
        java.util.Locale loc = new java.util.Locale(p.getString("langue", "fr"));
        android.content.res.Configuration cfg =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                | (sombre ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                : android.content.res.Configuration.UI_MODE_NIGHT_NO);
        cfg.setLocale(loc);
        Ui.appliquerTheme(sombre);
        T.en = "en".equals(p.getString("langue", "fr"));
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        VerrouApp.affichage();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));

        TextView titre = Ui.texte(this, "YIWALO TUNNEL", 22f, Ui.TEXTE, true);
        titre.setGravity(Gravity.CENTER);
        col.addView(titre);
        TextView sous = Ui.texte(this, T.en ? "App locked" : "Application verrouillée",
                14f, Ui.DISCRET, false);
        sous.setGravity(Gravity.CENTER);
        col.addView(sous);

        TextView bouton = Ui.bouton(this, T.en ? "Unlock" : "Déverrouiller", Ui.BLEU,
                this::demander);
        col.addView(bouton);
        ViewGroup.LayoutParams bl = bouton.getLayoutParams();
        if (bl instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) bl).topMargin = Ui.dp(this, 32);
        }
        setContentView(col);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> moveTaskToBack(true));
        }
        if (etat == null) {
            demander();
        }
    }

    private void demander() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent(
                    "YIWALO TUNNEL",
                    T.en ? "Confirm it is you to open the app"
                            : "Confirme ton identité pour ouvrir l’application");
            if (i == null) {
                // plus aucun verrouillage d'écran sur le téléphone : on désactive l'option
                VerrouApp.desactiver(this);
                finish();
                return;
            }
            startActivityForResult(i, CODE);
        } catch (RuntimeException e) {
            T.toast(this, String.valueOf(e.getMessage()),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        super.onActivityResult(code, resultat, donnees);
        if (code == CODE && resultat == RESULT_OK) {
            VerrouApp.succes();
            finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) VerrouApp.abandon();
        super.onDestroy();
    }
}
