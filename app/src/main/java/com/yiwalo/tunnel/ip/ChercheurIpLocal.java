package com.yiwalo.tunnel.ip;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
public final class ChercheurIpLocal {
    public interface Ecoute {
        void etat(String message);
        void fini(boolean trouve, String ip);
    }
    public static final class Entree {
        public final String ip;
        public final boolean voulue;
        Entree(String ip, boolean voulue) { this.ip = ip; this.voulue = voulue; }
    }
    public static final class Ligne {
        public final String heure;
        public final String texte;
        public final int type;
        Ligne(String heure, String texte, int type) { this.heure = heure; this.texte = texte; this.type = type; }
    }
    private static final String PREFS = "yiwalo_reglages";
    private static final Handler h = new Handler(Looper.getMainLooper());
    public static final List<Entree> historique = new ArrayList<>();
    public static final List<Ligne> journal = new ArrayList<>();
    public static volatile String texteEtat = "En attente";
    public static volatile Runnable observateur;
    private static volatile boolean actif;
    private static int gen;
    private static int essai;
    private static String derniere;
    private static Ecoute ecoute;
    private ChercheurIpLocal() { }

    public static boolean enCours() { return actif; }
    public static void arreter() {
        if (!actif) return;
        actif = false;
        gen++;
        texteEtat = "Arrêté";
        log(3, "Recherche arrêtée");
        notifier();
    }
    public static String texteSauve(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ips_cibles", "");
    }
    public static void sauver(Context c, String t) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("ips_cibles", t).apply();
    }
    public static int essaisSauve(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("ips_essais", 50);
    }
    public static void sauverEssais(Context c, int n) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("ips_essais", n).apply();
    }
    public static int delaiSauve(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("ips_delai", 5);
    }
    public static void sauverDelai(Context c, int n) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("ips_delai", n).apply();
    }
    public static String ipCourante(Context c) {
        try {
            return ipActuelle(c.getApplicationContext());
        } catch (RuntimeException e) {
            return null;
        }
    }
    private static void log(int type, String t) {
        String hh = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        journal.add(new Ligne(hh, t, type));
        while (journal.size() > 300) journal.remove(0);
    }
    private static final java.util.regex.Pattern PLAGE_IP = java.util.regex.Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,3}){0,3})(?:\\s*(?:\\.\\.|[-–~])\\s*|\\s+(?:à|a|au|to)\\s+)"
                    + "(\\d{1,3}(?:\\.\\d{1,3}){0,3})",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** Remplace les plages (10.196..10.210) par la liste de leurs adresses. */
    private static String etendrePlages(String texte) {
        if (texte == null) {
            return "";
        }
        // tolère une virgule tapée à la place du point : 10.196 à 10,210
        texte = texte.replaceAll("(\\d{1,3}\\.\\d{1,3}\\s*(?:\\.\\.|[-–~]|\\s(?:à|a|au|to)\\s)\\s*\\d{1,3}),(\\d{1,3})(?![\\d.])", "$1.$2");
        java.util.regex.Matcher m = PLAGE_IP.matcher(texte);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] pa = m.group(1).split("\\.");
            String[] pb = m.group(2).split("\\.");
            String remplacement = m.group();
            if (pa.length == pb.length) {
                int n = pa.length;
                int[] va = new int[n];
                int[] vb = new int[n];
                boolean ok = true;
                for (int i = 0; i < n; i++) {
                    va[i] = Integer.parseInt(pa[i]);
                    vb[i] = Integer.parseInt(pb[i]);
                    if (va[i] > 255 || vb[i] > 255 || (i < n - 1 && va[i] != vb[i])) {
                        ok = false;
                    }
                }
                if (ok) {
                    int debut = Math.min(va[n - 1], vb[n - 1]);
                    int fin = Math.max(va[n - 1], vb[n - 1]);
                    StringBuilder pre = new StringBuilder();
                    for (int i = 0; i < n - 1; i++) {
                        pre.append(va[i]).append('.');
                    }
                    StringBuilder liste = new StringBuilder(" ");
                    for (int k = debut; k <= fin; k++) {
                        liste.append(pre).append(k).append(' ');
                    }
                    remplacement = liste.toString();
                }
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(remplacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static List<String> cibles(String texte) {
        List<String> l = new ArrayList<>();
        if (texte == null) return l;
        for (String x : etendrePlages(texte).split("[\\s,;]+")) {
            String t = x.trim();
            if (!t.isEmpty()) l.add(t);
        }
        return l;
    }
    /**
     * Comparaison intelligente, segment par segment.
     * "10.143", "10.143." ou "10.143.*" trouvent 10.143.16.8.
     * "10.143.16" trouve 10.143.16.x. "10.143.16.8" = adresse exacte.
     * "*" ou "x" remplace un segment : "10.*.16" ou "10.x.16".
     */
    public static boolean correspond(String ip, List<String> cibles) {
        if (ip == null) return false;
        String[] a = ip.trim().split("\\.");
        for (String c : cibles) {
            String t = c.trim();
            while (t.endsWith("*") || t.endsWith(".")) t = t.substring(0, t.length() - 1);
            if (t.isEmpty()) continue;
            String[] b = t.split("\\.");
            if (b.length > a.length) continue;
            boolean ok = true;
            for (int i = 0; i < b.length && ok; i++) {
                String s = b[i];
                if (s.equals("*") || s.equalsIgnoreCase("x")) continue;
                ok = s.equals(a[i]);
            }
            if (ok) return true;
        }
        return false;
    }
    private static String ipActuelle(Context app) {
        ConnectivityManager cm =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        return ReseauMobile.ipv4Mobile(cm);
    }
    private static void notifier() {
        Runnable r = observateur;
        if (r != null) r.run();
    }
    private static void etat(String m) {
        texteEtat = m;
        Ecoute e = ecoute;
        if (e != null) e.etat(m);
        notifier();
    }
    private static void noter(String ip, boolean ok) {
        derniere = ip;
        historique.add(0, new Entree(ip, ok));
        while (historique.size() > 200) historique.remove(historique.size() - 1);
        log(ok ? 2 : 0, "IP : " + ip);
        notifier();
    }
    /** Appelé quand tu appuies sur Lancer et que tu es déjà sur une IP voulue. */
    public static void signalerDejaBonne(String ip) {
        derniere = ip;
        historique.clear();
        historique.add(0, new Entree(ip, true));
        journal.clear();
        log(2, "IP : " + ip);
        log(2, "IP " + ip + " déjà voulue : trouvée avec succès !");
        log(3, "Recherche arrêtée");
        texteEtat = "IP voulue trouvée : " + ip;
        notifier();
    }
    private static void terminer(boolean trouve, String ip) {
        actif = false;
        texteEtat = trouve ? "IP voulue trouvée : " + ip : "Terminé sans trouver d'IP voulue";
        if (trouve) {
            log(2, "IP " + ip + " trouvée avec succès !");
            log(3, "Recherche arrêtée");
        } else {
            log(3, "Terminé sans trouver d'IP voulue");
        }
        notifier();
        Ecoute e = ecoute;
        if (e != null) e.fini(trouve, ip);
    }
    /** À appeler sur le thread principal. */
    public static boolean demarrer(Context c, int max, Ecoute e) {
        if (actif) return false;
        ecoute = e;
        Context app = c.getApplicationContext();
        List<String> l = cibles(texteSauve(app));
        if (l.isEmpty()) { etat("Liste vide : colle au moins une adresse."); return false; }
        essai = 0;
        derniere = null;
        historique.clear();
        journal.clear();
        log(0, "Recherche démarrée");
        String ip = ipActuelle(app);
        if (ip != null && correspond(ip, l)) {
            actif = true;
            gen++;
            noter(ip, true);
            terminer(true, ip);
            return true;
        }
        if (!AvionIp.automatiquePossible(app)) {
            etat("Active l'assistant ou l'accessibilité.");
            return false;
        }
        actif = true;
        final int g = ++gen;
        if (ip != null) noter(ip, false);
        suivant(app, l, max, g);
        return true;
    }
    private static void suivant(final Context app, final List<String> l, final int max, final int g) {
        if (!actif || g != gen) return;
        if (essai >= max) { terminer(false, derniere); return; }
        essai++;
        String nb = max < 1000000 ? "/" + max : "";
        etat("Essai " + essai + nb + " : mode avion en cours…");
        log(1, "Essai " + essai + " : mode avion ON puis OFF");
        if (!AvionIp.renouveler(app, null)) { terminer(false, derniere); return; }
        h.postDelayed(() -> attendre(app, l, max, g, 0, false), 2000);
    }
    private static void attendre(final Context app, final List<String> l, final int max,
                                 final int g, final int n, final boolean vuNull) {
        if (!actif || g != gen) return;
        String ip = ipActuelle(app);
        if (ip != null && (vuNull || n >= 15)) {
            boolean ok = correspond(ip, l);
            noter(ip, ok);
            if (ok) {
                terminer(true, ip);
            } else {
                etat("IP obtenue : " + ip + " (non voulue)");
                final int dl = delaiSauve(app);
                if (dl <= 0) suivant(app, l, max, g);
                else h.postDelayed(() -> suivant(app, l, max, g), dl * 1000L);
            }
            return;
        }
        if (n >= 60) {
            etat("Pas de réseau, nouvel essai…");
            suivant(app, l, max, g);
            return;
        }
        final boolean vn = vuNull || ip == null;
        h.postDelayed(() -> attendre(app, l, max, g, n + 1, vn), 1000);
    }
}
