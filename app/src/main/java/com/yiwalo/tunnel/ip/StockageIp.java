package com.yiwalo.tunnel.ip;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class StockageIp {

    private final SharedPreferences prefs;

    public StockageIp(Context contexte) {
        prefs = contexte.getApplicationContext()
                .getSharedPreferences("yiwalo_ip_checker", Context.MODE_PRIVATE);
    }

    public List<String> lister() {
        List<String> liste = new ArrayList<>();
        for (String ligne : prefs.getString("cibles", "").split("\n")) {
            String normalise = ListeIp.normaliser(ligne);
            if (normalise != null && !liste.contains(normalise)) {
                liste.add(normalise);
            }
        }
        return liste;
    }

    public void enregistrer(List<String> liste) {
        prefs.edit().putString("cibles", String.join("\n", liste)).apply();
    }

    public int delai() {
        return prefs.getInt("delai", 5) == 3 ? 3 : 5;
    }

    public void definirDelai(int secondes) {
        prefs.edit().putInt("delai", secondes == 3 ? 3 : 5).apply();
    }
}
