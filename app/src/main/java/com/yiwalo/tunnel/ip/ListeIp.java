package com.yiwalo.tunnel.ip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Logique pure (sans Android) : validation et comparaison des adresses IP à rechercher. */
public final class ListeIp {

    private ListeIp() {
    }

    public static final class Extraction {
        public final List<String> valides = new ArrayList<>();
        public final List<String> invalides = new ArrayList<>();
    }

    /**
     * Retourne l'entrée normalisée : une IPv4 exacte (10.197.158.178) ou un motif
     * avec jokers à droite (10.197.*.*). Retourne null si l'entrée est invalide.
     */
    public static String normaliser(String saisie) {
        if (saisie == null) {
            return null;
        }
        String texte = saisie.trim();
        if (texte.isEmpty()) {
            return null;
        }
        String[] parties = texte.split("\\.", -1);
        if (parties.length != 4) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean joker = false;
        for (int i = 0; i < 4; i++) {
            String p = parties[i];
            if (p.equals("*")) {
                joker = true;
            } else {
                if (joker || p.isEmpty() || p.length() > 3) {
                    return null;
                }
                for (int k = 0; k < p.length(); k++) {
                    char c = p.charAt(k);
                    if (c < '0' || c > '9') {
                        return null;
                    }
                }
                int valeur = Integer.parseInt(p);
                if (valeur > 255) {
                    return null;
                }
                p = Integer.toString(valeur);
            }
            if (i > 0) {
                sb.append('.');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** Vrai si l'adresse (exacte) correspond au motif (exact ou avec jokers). */
    public static boolean correspond(String ip, String motif) {
        String adresse = normaliser(ip);
        String modele = normaliser(motif);
        if (adresse == null || modele == null || adresse.contains("*")) {
            return false;
        }
        String[] a = adresse.split("\\.");
        String[] m = modele.split("\\.");
        for (int i = 0; i < 4; i++) {
            if (!m[i].equals("*") && !m[i].equals(a[i])) {
                return false;
            }
        }
        return true;
    }

    /** Retourne le premier motif de la liste qui correspond, ou null. */
    public static String premiereCorrespondance(String ip, Collection<String> liste) {
        for (String motif : liste) {
            if (correspond(ip, motif)) {
                return motif;
            }
        }
        return null;
    }

    /** Sépare un texte collé (espaces, retours, virgules, points-virgules). */
    private static final java.util.regex.Pattern PLAGE = java.util.regex.Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,3}){0,3})(?:\\s*(?:\\.\\.|[-–~])\\s*|\\s+(?:à|a|au|to)\\s+)"
                    + "(\\d{1,3}(?:\\.\\d{1,3}){0,3})",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** Développe une plage (10.203 à 10.210) en motifs (10.203.*.* ... 10.210.*.*). */
    private static boolean developper(String a, String b, List<String> sortie) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = pa.length;
        if (n != pb.length) {
            return false;
        }
        int[] va = new int[n];
        int[] vb = new int[n];
        for (int i = 0; i < n; i++) {
            try {
                va[i] = Integer.parseInt(pa[i]);
                vb[i] = Integer.parseInt(pb[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (va[i] > 255 || vb[i] > 255) {
                return false;
            }
        }
        for (int i = 0; i < n - 1; i++) {
            if (va[i] != vb[i]) {
                return false;
            }
        }
        int debut = Math.min(va[n - 1], vb[n - 1]);
        int fin = Math.max(va[n - 1], vb[n - 1]);
        for (int k = debut; k <= fin; k++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n - 1; i++) {
                sb.append(va[i]).append('.');
            }
            sb.append(k);
            for (int j = n; j < 4; j++) {
                sb.append(".*");
            }
            sortie.add(sb.toString());
        }
        return true;
    }

    /** Sépare un texte collé et comprend les plages (10.203 à 10.210). */
    public static Extraction extraire(String texte) {
        Extraction resultat = new Extraction();
        if (texte == null) {
            return resultat;
        }
        java.util.regex.Matcher m = PLAGE.matcher(texte);
        StringBuffer reste = new StringBuffer();
        while (m.find()) {
            List<String> liste = new ArrayList<>();
            if (developper(m.group(1), m.group(2), liste)) {
                for (String e : liste) {
                    String n = normaliser(e);
                    if (n != null && !resultat.valides.contains(n)) {
                        resultat.valides.add(n);
                    }
                }
            } else {
                resultat.invalides.add(m.group().trim());
            }
            m.appendReplacement(reste, " ");
        }
        m.appendTail(reste);
        Extraction autres = extraireSimple(reste.toString());
        for (String v : autres.valides) {
            if (!resultat.valides.contains(v)) {
                resultat.valides.add(v);
            }
        }
        resultat.invalides.addAll(autres.invalides);
        return resultat;
    }

    private static Extraction extraireSimple(String texte) {
        Extraction resultat = new Extraction();
        if (texte == null) {
            return resultat;
        }
        for (String morceau : texte.split("[\\s,;]+")) {
            if (morceau.isEmpty()) {
                continue;
            }
            String normalise = normaliser(morceau);
            if (normalise == null) {
                resultat.invalides.add(morceau);
            } else if (!resultat.valides.contains(normalise)) {
                resultat.valides.add(normalise);
            }
        }
        return resultat;
    }
}
