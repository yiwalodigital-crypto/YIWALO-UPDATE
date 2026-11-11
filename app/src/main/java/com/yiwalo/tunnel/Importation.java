package com.yiwalo.tunnel;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.config.ImportateurLiens;

public final class Importation {

    private Importation() {
    }

    private static void importerCodeYl(final Context ctx, final ConfigStore store,
            final String texte, final String mdp, final Runnable apres) {
        if (mdp == null && PartageConfig.necessiteMotDePasse(texte)) {
            final android.widget.EditText e = new android.widget.EditText(ctx);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            e.setHint("Mot de passe");
            new android.app.AlertDialog.Builder(ctx)
                    .setTitle(T.t("Configuration protégée"))
                    .setView(e)
                    .setPositiveButton(T.t("OK"), (d, w) ->
                            importerCodeYl(ctx, store, texte, e.getText().toString(), apres))
                    .setNegativeButton(T.t("Annuler"), null)
                    .show();
            return;
        }
        try {
            Configuration c = PartageConfig.importer(texte, mdp == null ? "" : mdp);
            if (store.ajouter(c)) {
                store.selectionner(c.id);
                T.toast(ctx, "Configuration importée", Toast.LENGTH_LONG).show();
                apres.run();
            } else {
                T.toast(ctx, "Cette configuration existe déjà", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            T.toast(ctx, "Code invalide ou mot de passe incorrect",
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void depuisPressePapiers(Context contexte, ConfigStore store, Runnable apres) {
        ClipboardManager cm =
                (ClipboardManager) contexte.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        CharSequence texte = (clip != null && clip.getItemCount() > 0)
                ? clip.getItemAt(0).coerceToText(contexte) : null;
        if (texte == null || texte.toString().trim().isEmpty()) {
            T.toast(contexte, R.string.cfg_presse_vide, Toast.LENGTH_SHORT).show();
            return;
        }

        if (PartageConfig.estCode(texte.toString())) {
            importerCodeYl(contexte, store, texte.toString(), null, apres);
            return;
        }

        ImportateurLiens.Resultat r = ImportateurLiens.importer(texte.toString());
        int ajoutees = 0;
        int doublons = 0;
        for (Configuration c : r.configurations) {
            if (store.ajouter(c)) {
                ajoutees++;
                store.selectionner(c.id);
            } else {
                doublons++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(contexte.getString(R.string.cfg_ajoutees, ajoutees));
        if (ajoutees > 0) {
            sb.append("\n").append(contexte.getString(R.string.acc_import_pret));
        }
        if (doublons > 0) {
            sb.append("\n").append(contexte.getString(R.string.cfg_doublons, doublons));
        }
        if (!r.erreurs.isEmpty()) {
            sb.append("\n\n").append(contexte.getString(R.string.cfg_erreurs));
            for (String e : r.erreurs) {
                sb.append("\n• ").append(e);
            }
        }
        String msgToast;
        if (ajoutees > 0) msgToast = contexte.getString(R.string.cfg_ajoutees, ajoutees);
        else if (!r.erreurs.isEmpty()) msgToast = "Format non reconnu";
        else msgToast = contexte.getString(R.string.cfg_doublons, doublons);
        T.toast(contexte, msgToast, Toast.LENGTH_LONG).show();
        String etatImp = com.yiwalo.tunnel.SuiviVpn.etat().name();
        if (ajoutees > 0 && !"DECONNECTE".equals(etatImp) && !"ERREUR".equals(etatImp)) {
            T.toast(contexte, "Ajoutée. Sélection possible après déconnexion",
                    Toast.LENGTH_LONG).show();
        }
        apres.run();
    }
}
