package com.yiwalo.tunnel.config;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class ConfigStore {

    private static final String FICHIER = "yiwalo_configurations";
    private static final String CLE_LISTE = "liste";
    private static final String CLE_ACTIVE = "active";

    private final SharedPreferences prefs;

    public ConfigStore(Context contexte) {
        prefs = contexte.getApplicationContext()
                .getSharedPreferences(FICHIER, Context.MODE_PRIVATE);
    }

    public synchronized List<Configuration> lister() {
        List<Configuration> liste = new ArrayList<>();
        try {
            JSONArray tableau = new JSONArray(prefs.getString(CLE_LISTE, "[]"));
            for (int i = 0; i < tableau.length(); i++) {
                liste.add(Configuration.depuisJson(tableau.getJSONObject(i)));
            }
        } catch (JSONException | IllegalArgumentException e) {
            // Données illisibles : on renvoie ce qui a pu être lu.
        }
        return liste;
    }

    /** Retourne false si une configuration portant le même nom existe déjà. */
    public synchronized boolean ajouter(Configuration configuration) {
        List<Configuration> liste = lister();
        for (Configuration c : liste) {
            String n1 = c.nom == null ? "" : c.nom.trim();
            String n2 = configuration.nom == null ? "" : configuration.nom.trim();
            if (!n2.isEmpty() && n1.equalsIgnoreCase(n2)) {
                return false;
            }
        }
        liste.add(0, configuration);
        enregistrer(liste);
        if (active() == null) {
            selectionner(configuration.id);
        }
        return true;
    }

    public synchronized void ajouterCopie(Configuration configuration) {
        List<Configuration> liste = lister();
        liste.add(0, configuration);
        enregistrer(liste);
        if (active() == null) {
            selectionner(configuration.id);
        }
    }

    public synchronized void supprimer(String id) {
        { String etatVpn = com.yiwalo.tunnel.SuiviVpn.etat().name();
          if (!"DECONNECTE".equals(etatVpn) && !"ERREUR".equals(etatVpn)) return; }
        List<Configuration> liste = lister();
        List<Configuration> restantes = new ArrayList<>();
        for (Configuration c : liste) {
            if (!c.id.equals(id)) {
                restantes.add(c);
            }
        }
        enregistrer(restantes);
        if (id.equals(prefs.getString(CLE_ACTIVE, ""))) {
            prefs.edit().remove(CLE_ACTIVE).apply();
            if (!restantes.isEmpty()) {
                selectionner(restantes.get(0).id);
            }
        }
    }

    public synchronized void selectionner(String id) {
        { String etatVpn = com.yiwalo.tunnel.SuiviVpn.etat().name();
          if (!"DECONNECTE".equals(etatVpn) && !"ERREUR".equals(etatVpn)) return; }
        prefs.edit().putString(CLE_ACTIVE, id).apply();
    }

    public synchronized Configuration active() {
        String id = prefs.getString(CLE_ACTIVE, "");
        for (Configuration c : lister()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public synchronized void remplacer(Configuration nouvelle) {
        List<Configuration> resultat = new ArrayList<>();
        for (Configuration c : lister()) {
            resultat.add(c.id.equals(nouvelle.id) ? nouvelle : c);
        }
        enregistrer(resultat);
    }

    public synchronized void modifierSni(String id, String sni) {
        List<Configuration> nouvelle = new ArrayList<>();
        for (Configuration c : lister()) {
            if (c.id.equals(id)) {
                nouvelle.add(new Configuration(c.id, c.nom, c.type, c.serveur,
                        c.port, c.lienOriginal, sni));
            } else {
                nouvelle.add(c);
            }
        }
        enregistrer(nouvelle);
    }

    private void enregistrer(List<Configuration> liste) {
        try {
            JSONArray tableau = new JSONArray();
            for (Configuration c : liste) {
                tableau.put(c.versJson());
            }
            prefs.edit().putString(CLE_LISTE, tableau.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Enregistrement impossible", e);
        }
    }
}
