package com.yiwalo.tunnel;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Journal {

    public enum Niveau {
        INFO, AVERTISSEMENT, ERREUR
    }

    public static final class Ligne {
        public final long heure;
        public final Niveau niveau;
        public final String message;

        Ligne(long heure, Niveau niveau, String message) {
            this.heure = heure;
            this.niveau = niveau;
            this.message = message;
        }
    }

    private static final int MAXIMUM = 500;
    private static final List<Ligne> LIGNES = new ArrayList<>();

    private static final Pattern MOTIF_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern MOTIF_SECRET = Pattern.compile(
            "(?i)(password|pass|pbk|sid|key)\\s*[=:]\\s*\\S+");

    private Journal() {
    }

    public static void info(String message) {
        ajouter(Niveau.INFO, message);
    }

    public static void avertissement(String message) {
        ajouter(Niveau.AVERTISSEMENT, message);
    }

    public static void erreur(String message) {
        ajouter(Niveau.ERREUR, message);
    }

    private static final String[] MOTIFS_RESEAU = {
            "i/o timeout", "timed out", "connection reset", "no route to host",
            "network is unreachable", "broken pipe", "unexpected eof",
            "use of closed network connection", "connection refused",
            "connection abort", "software caused", "failed to dial", "dial tcp",
            "eof", "deadline exceeded", "no such host", "connection closed",
            "read tcp", "write tcp", "handshake timeout", "operation timed out"};

    /** Vrai si la ligne du moteur décrit un simple souci de réseau (pas un bug ni un certificat). */
    private static boolean estErreurReseau(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("x509") || m.contains("certificate") || m.contains("tls: failed")) {
            return false;
        }
        for (String motif : MOTIFS_RESEAU) {
            if (m.contains(motif)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void ajouter(Niveau niveau, String message) {
        if (message != null) {
            for (String prefixe : INFOS_SERVEUR) {
                if (message.startsWith(prefixe)) {
                    return;
                }
            }
        }
        if (message != null && message.startsWith("[moteur]") && estErreurReseau(message)) {
            niveau = Niveau.INFO;
            message = "Ping timeout";
        }
        String propre = masquer(message == null ? "" : message);
        LIGNES.add(new Ligne(System.currentTimeMillis(), niveau, propre));
        while (LIGNES.size() > MAXIMUM) {
            LIGNES.remove(0);
        }
        int priorite = niveau == Niveau.ERREUR ? Log.ERROR
                : niveau == Niveau.AVERTISSEMENT ? Log.WARN : Log.INFO;
        Log.println(priorite, "YIWALO", propre);
    }

    private static final Pattern MOTIF_IPV4 =
            Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static final List<String> TERMES = new ArrayList<>();
    private static final String[] INFOS_SERVEUR = {
            "Adresse du serveur", "Paramètres du lien", "Transport :"};

    /** Termes à masquer dans tous les messages (nom du serveur, SNI, Host...). */
    public static synchronized void definirTermes(String... termes) {
        TERMES.clear();
        for (String t : termes) {
            if (t != null && t.trim().length() >= 3) {
                TERMES.add(t.trim());
            }
        }
        TERMES.sort((a, b) -> b.length() - a.length());
    }

    public static synchronized String masquer(String texte) {
        String resultat = MOTIF_UUID.matcher(texte).replaceAll("****");
        resultat = MOTIF_SECRET.matcher(resultat).replaceAll("$1=****");
        resultat = MOTIF_IPV4.matcher(resultat).replaceAll("***");
        for (String t : TERMES) {
            resultat = resultat.replace(t, "***");
        }
        return resultat;
    }

    private static volatile long latenceMs = -1;
    private static volatile long latenceHeure = 0;

    public static void definirLatence(long ms) {
        latenceMs = ms;
        latenceHeure = System.currentTimeMillis();
    }

    public static long latenceMs() {
        return latenceMs;
    }

    public static long latenceHeure() {
        return latenceHeure;
    }

    public static synchronized List<Ligne> lignes() {
        return new ArrayList<>(LIGNES);
    }

    public static synchronized void effacer() {
        LIGNES.clear();
    }

    public static synchronized String texte() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);
        StringBuilder sb = new StringBuilder();
        for (Ligne l : LIGNES) {
            sb.append(format.format(new Date(l.heure)))
                    .append(" [").append(l.niveau.name()).append("] ")
                    .append(l.message).append("\n");
        }
        return sb.toString();
    }
}
