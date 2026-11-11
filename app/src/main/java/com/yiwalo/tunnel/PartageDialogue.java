package com.yiwalo.tunnel;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.yiwalo.tunnel.config.Configuration;
import java.util.Calendar;

public final class PartageDialogue {
    private PartageDialogue() {}

    private interface Fabrique { String creer(); }

    private static TextView bouton(Context ctx, String texte, boolean plein) {
        TextView b = new TextView(ctx);
        b.setText(texte);
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, 24));
        if (plein) {
            g.setColor(ctx.getColor(R.color.bleu));
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(ctx, 2), ctx.getColor(R.color.bleu));
            b.setTextColor(ctx.getColor(R.color.bleu_clair));
        }
        b.setBackground(g);
        b.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        b.setLayoutParams(marge(ctx, 12));
        return b;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    private static void cadre(Context ctx, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(ctx, 1), ctx.getColor(R.color.texte_discret));
        g.setCornerRadius(dp(ctx, 8));
        v.setBackground(g);
        v.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
    }

    private static LinearLayout.LayoutParams marge(Context ctx, int haut) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(ctx, haut);
        return p;
    }

    private static EditText champ(Context ctx, String indice, int lignes) {
        EditText e = new EditText(ctx);
        e.setHint(indice);
        e.setHintTextColor(ctx.getColor(R.color.texte_discret));
        e.setTextColor(ctx.getColor(R.color.texte));
        e.setTextSize(15f);
        if (lignes > 1) {
            e.setMinLines(lignes);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        cadre(ctx, e);
        e.setLayoutParams(marge(ctx, 12));
        return e;
    }

    private static CheckBox option(Context ctx, String texte, boolean coche) {
        CheckBox cb = new CheckBox(ctx);
        cb.setText(texte);
        cb.setTextSize(15f);
        cb.setTextColor(ctx.getColor(R.color.texte));
        cb.setButtonTintList(ColorStateList.valueOf(ctx.getColor(R.color.bleu_clair)));
        cb.setChecked(coche);
        cb.setLayoutParams(marge(ctx, 8));
        return cb;
    }

    public static void ouvrir(Context ctx, Configuration c) {
        if (c.verrouille) {
            T.toast(ctx, "Configuration verrouillée : partage impossible",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Calendar cal = Calendar.getInstance();
        final long[] expire = {0};

        LinearLayout racine = new LinearLayout(ctx);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 8));

        TextView titre = new TextView(ctx);
        titre.setText("Partager la configuration");
        titre.setTextSize(20f);
        titre.setTypeface(titre.getTypeface(), Typeface.BOLD);
        titre.setTextColor(ctx.getColor(R.color.texte));
        racine.addView(titre);

        final EditText nomFichier = champ(ctx, "Nom du fichier de configuration", 1);
        racine.addView(nomFichier);

        final CheckBox verrou = option(ctx, "Verrouiller la configuration", true);
        verrou.setEnabled(false);
        racine.addView(verrou);

        final EditText mdp = champ(ctx, "Mot de passe (facultatif)", 1);
        mdp.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        racine.addView(mdp);

        final CheckBox mobile = option(ctx, "Réseau de données mobiles uniquement", false);
        racine.addView(mobile);
        final CheckBox root = option(ctx, "Bloquer les appareils rootés", false);
        racine.addView(root);
        final CheckBox store = option(ctx, "Installation depuis un store officiel uniquement", false);
        racine.addView(store);

        final TextView date = new TextView(ctx);
        date.setTextSize(15f);
        cadre(ctx, date);
        date.setLayoutParams(marge(ctx, 14));
        final Runnable maj = () -> {
            if (expire[0] == 0) {
                date.setText("Date et heure d'expiration (facultatif)");
                date.setTextColor(ctx.getColor(R.color.texte_discret));
            } else {
                date.setText("Expire le " + PartageConfig.formater(expire[0]));
                date.setTextColor(ctx.getColor(R.color.texte));
            }
        };
        maj.run();
        date.setOnClickListener(v -> {
            if (expire[0] != 0) cal.setTimeInMillis(expire[0]);
            else cal.add(Calendar.DAY_OF_MONTH, 7);
            new DatePickerDialog(ctx, (vue, a, m, j) -> {
                cal.set(a, m, j);
                new TimePickerDialog(ctx, (vue2, h, mn) -> {
                    cal.set(Calendar.HOUR_OF_DAY, h);
                    cal.set(Calendar.MINUTE, mn);
                    cal.set(Calendar.SECOND, 0);
                    expire[0] = cal.getTimeInMillis();
                    maj.run();
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        date.setOnLongClickListener(v -> {
            expire[0] = 0;
            maj.run();
            return true;
        });
        racine.addView(date);

        final EditText appareils = champ(ctx, "ID(s) appareil (facultatif, séparés par une virgule)", 1);
        racine.addView(appareils);

        final EditText message = champ(ctx, "Message pour les utilisateurs (facultatif)", 4);
        racine.addView(message);

        final boolean[] gros = {false};
        final Fabrique fab = () -> {
            if (nomFichier.getText().toString().trim().isEmpty()) {
                T.toast(ctx, "Donne un nom au fichier", Toast.LENGTH_SHORT).show();
                return null;
            }
            PartageConfig.Options o = new PartageConfig.Options();
            o.lourd = gros[0];
            o.expire = expire[0];
            o.nom = nomFichier.getText().toString().trim();
            o.message = message.getText().toString().trim();
            o.verrouille = true;
            o.mobile = mobile.isChecked();
            o.root = root.isChecked();
            o.store = store.isChecked();
            o.appareils = appareils.getText().toString().trim();
            o.mdp = mdp.getText().toString();
            try {
                return PartageConfig.exporter(c, o);
            } catch (Exception e) {
                T.toast(ctx, "Erreur de création", Toast.LENGTH_LONG).show();
                return null;
            }
        };

        final AlertDialog[] dlg = new AlertDialog[1];

        TextView btnCopier = bouton(ctx, "Copier dans le presse-papiers", true);
        btnCopier.setOnClickListener(v -> {
            String code = fab.creer();
            if (code == null) return;
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("yiwalo", code));
            T.toast(ctx, "Code copié dans le presse-papiers",
                    Toast.LENGTH_SHORT).show();
            dlg[0].dismiss();
        });
        racine.addView(btnCopier);

        TextView btnFichier = bouton(ctx, "Créer un fichier .YL", false);
        btnFichier.setOnClickListener(v -> {
            gros[0] = true;
            final String code = fab.creer();
            gros[0] = false;
            if (code == null) return;
            String base = nomFichier.getText().toString().trim()
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            if (base.isEmpty()) base = "config";
            dlg[0].dismiss();
            SelecteurFichier.creer(ctx, base + ".YL", uri -> {
                try (java.io.OutputStream out =
                             ctx.getContentResolver().openOutputStream(uri)) {
                    out.write(code.getBytes("UTF-8"));
                    T.toast(ctx, "Fichier enregistré", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    T.toast(ctx, "Échec de l'enregistrement", Toast.LENGTH_LONG).show();
                }
            });
        });
        racine.addView(btnFichier);

        TextView btnAnnuler = new TextView(ctx);
        btnAnnuler.setText("ANNULER");
        btnAnnuler.setTextSize(14f);
        btnAnnuler.setGravity(Gravity.CENTER);
        btnAnnuler.setTextColor(ctx.getColor(R.color.bleu_clair));
        btnAnnuler.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 8));
        btnAnnuler.setOnClickListener(v -> dlg[0].dismiss());
        racine.addView(btnAnnuler);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(racine);

        dlg[0] = new AlertDialog.Builder(ctx).setView(sv).create();
        dlg[0].show();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ctx.getColor(R.color.carte));
        bg.setCornerRadius(dp(ctx, 20));
        dlg[0].getWindow().setBackgroundDrawable(bg);
    }
}
