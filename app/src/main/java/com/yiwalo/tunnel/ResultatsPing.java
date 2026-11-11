package com.yiwalo.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Ping silencieux : v2ray (TCP) et SlowDNS (requête DNS) testés en même temps. */
final class ResultatsPing {
    static final long ECHEC = -1;
    static final long SANS_MESURE = -2;
    static final long EN_COURS = -3;

    private static final Map<String, Long> VALEURS = new ConcurrentHashMap<>();
    private static final Map<String, WeakReference<TextView>> VUES = new ConcurrentHashMap<>();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Random RND = new Random();
    private static final Runnable SECURITE = ResultatsPing::terminer;
    private static volatile int GEN = 0;
    private static volatile ExecutorService pool;
    private static final ExecutorService PAR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "yiwalo-ping-reseau");
        t.setDaemon(true);
        return t;
    });
    private static Runnable surFin;
    private static volatile boolean enCours = false;

    private ResultatsPing() { }

    static boolean enCours() {
        return enCours;
    }

    /** Résultat agrégé d'une configuration : la meilleure mesure parmi tous les réseaux testés. */
    private static final class Mesure {
        final Configuration c;
        final AtomicInteger restantes;
        volatile long meilleur = ECHEC;

        Mesure(Configuration c, int taches) {
            this.c = c;
            this.restantes = new AtomicInteger(taches);
        }

        synchronized void ajouter(long ms) {
            if (ms >= 0) {
                if (meilleur < 0 || ms < meilleur) {
                    meilleur = ms;
                }
            } else if (ms == SANS_MESURE && meilleur < 0) {
                meilleur = SANS_MESURE;
            }
        }
    }

    private static boolean estSlowDns(Configuration c) {
        return c.slowNs != null && !c.slowNs.trim().isEmpty();
    }

    /** Protocoles UDP (sans port TCP à mesurer) : on vérifie seulement que l'hôte se résout. */
    private static boolean estUdp(Configuration c) {
        if (estSlowDns(c) || c.type == null) {
            return false;
        }
        return c.type == com.yiwalo.tunnel.config.TypeConnexion.HYSTERIA2
                || c.type == com.yiwalo.tunnel.config.TypeConnexion.TUIC;
    }

    static void lancer(MainActivity act, ConfigStore store, Runnable fin) {
        if (enCours) {
            return;
        }
        enCours = true;
        surFin = fin;
        VALEURS.clear();
        for (WeakReference<TextView> w : VUES.values()) {
            TextView t = w.get();
            if (t != null) {
                appliquer(t, null);
            }
        }
        final List<Configuration> liste = ordreMixte(store.lister());
        if (liste.isEmpty()) {
            Journal.info("Ping : aucune configuration à tester.");
            terminer();
            return;
        }
        final int generation = ++GEN;
        Journal.info("Ping : " + liste.size() + " configuration(s), v2ray, SSH et SlowDNS en même temps.");
        if (SuiviVpn.etat() == EtatVpn.CONNECTE) {
            Journal.info("Ping : VPN connecté, les mesures peuvent passer par le tunnel.");
        }
        final Context ctx = act.getApplicationContext();
        for (Configuration c : liste) {
            noter(c.id, EN_COURS);
        }
        H.postDelayed(SECURITE, 90000);
        final AtomicInteger configsRestantes = new AtomicInteger(liste.size());
        final CountDownLatch depart = new CountDownLatch(1);
        PAR.execute(() -> {
            // La liste des réseaux est lue une seule fois : toutes les configs sont mesurées sur les mêmes réseaux.
            List<Network> snap;
            try {
                snap = TestConfigsView.reseauxReels(ctx);
            } catch (Throwable e) {
                snap = new ArrayList<>();
            }
            final List<Network> nets = new ArrayList<>();
            if (snap != null) {
                nets.addAll(snap);
            }
            if (nets.isEmpty()) {
                nets.add(null); // réseau par défaut
            }
            if (generation != GEN) {
                return; // test arrêté pendant la préparation des réseaux
            }
            // Une tâche par (configuration x réseau). Toutes partent au même signal, sans thread
            // intermédiaire qui attend : aucun serveur n'attend la fin d'un autre.
            final List<Runnable> taches = new ArrayList<>();
            for (final Configuration c : liste) {
                final boolean udp = estUdp(c);
                final List<Network> cibles = udp ? java.util.Collections.singletonList(nets.get(0)) : nets;
                final Mesure m = new Mesure(c, cibles.size());
                for (final Network n : cibles) {
                    taches.add(() -> {
                        try {
                            depart.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (generation != GEN) {
                            return;
                        }
                        long ms;
                        try {
                            if (estSlowDns(c)) {
                                ms = pingDnsSur(c, ctx, n);
                            } else if (udp) {
                                ms = hoteResolu(c.serveur, n) ? SANS_MESURE : ECHEC;
                            } else {
                                ms = pingTcpSur(n, c.serveur == null ? "" : c.serveur.trim(), c.port);
                            }
                        } catch (Throwable e) {
                            ms = ECHEC;
                        }
                        m.ajouter(ms);
                        final boolean derniere = m.restantes.decrementAndGet() == 0;
                        H.post(() -> {
                            if (generation != GEN) {
                                return;
                            }
                            long v = m.meilleur; // lu ici : un résultat plus ancien n'écrase jamais un meilleur
                            if (v != ECHEC || derniere) {
                                noter(c.id, v);
                            }
                            if (derniere) {
                                Journal.info("Ping " + c.nom + " : " + (v >= 0 ? v + " ms" : (v == SANS_MESURE ? "OK" : "échec")));
                                if (configsRestantes.decrementAndGet() == 0) {
                                    terminer();
                                }
                            }
                        });
                    });
                }
            }
            final ExecutorService p = Executors.newFixedThreadPool(Math.max(1, Math.min(taches.size(), 400)), r -> {
                Thread t = new Thread(r, "yiwalo-ping");
                t.setDaemon(true);
                return t;
            });
            pool = p;
            if (generation != GEN) {
                p.shutdownNow(); // arrêté entre-temps
                return;
            }
            try {
                for (Runnable t : taches) {
                    p.execute(t);
                }
            } catch (RejectedExecutionException e) {
                // test arrêté pendant le démarrage
            } finally {
                depart.countDown(); // départ simultané de tous les serveurs
            }
        });
    }

    static void terminer() {
        H.removeCallbacks(SECURITE);
        if (!enCours) {
            return;
        }
        enCours = false;
        GEN++;
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        for (Map.Entry<String, Long> e : new ArrayList<>(VALEURS.entrySet())) {
            if (e.getValue() == EN_COURS) {
                noter(e.getKey(), ECHEC);
            }
        }
        Journal.info("Ping : terminé.");
        Runnable r = surFin;
        surFin = null;
        if (r != null) {
            r.run();
        }
    }

    static void noter(String id, long valeur) {
        VALEURS.put(id, valeur);
        WeakReference<TextView> w = VUES.get(id);
        TextView t = w == null ? null : w.get();
        if (t != null) {
            appliquer(t, valeur);
        }
    }

    /** Efface la mesure d'une configuration (ex. test arrêté en cours de route). */
    static void retirer(String id) {
        VALEURS.remove(id);
        WeakReference<TextView> w = VUES.get(id);
        TextView t = w == null ? null : w.get();
        if (t != null) {
            appliquer(t, null);
        }
    }

    static void lier(String id, TextView t) {
        VUES.put(id, new WeakReference<>(t));
        appliquer(t, VALEURS.get(id));
    }

    /** Alterne les configs SlowDNS et v2ray pour les tester en même temps. */
    static List<Configuration> ordreMixte(List<Configuration> source) {
        List<Configuration> slow = new ArrayList<>();
        List<Configuration> autres = new ArrayList<>();
        for (Configuration c : source) {
            if (c.slowNs != null && !c.slowNs.trim().isEmpty()) {
                slow.add(c);
            } else {
                autres.add(c);
            }
        }
        List<Configuration> mix = new ArrayList<>();
        int n = Math.max(slow.size(), autres.size());
        for (int i = 0; i < n; i++) {
            if (i < autres.size()) {
                mix.add(autres.get(i));
            }
            if (i < slow.size()) {
                mix.add(slow.get(i));
            }
        }
        return mix;
    }

    // ---------- Mesures

    /** Meilleur de 2 mesures sur un réseau précis (null = réseau par défaut), délai de 4 s. */
    private static long pingTcpSur(Network net, String hote, int port) {
        if (hote == null || hote.isEmpty() || port <= 0) {
            return ECHEC;
        }
        InetAddress adr;
        try {
            InetAddress[] toutes;
            try {
                toutes = net != null ? net.getAllByName(hote) : InetAddress.getAllByName(hote);
            } catch (Exception e) {
                toutes = InetAddress.getAllByName(hote); // DNS de ce réseau muet : DNS par défaut
            }
            adr = toutes[0];
            for (InetAddress a : toutes) {
                if (a instanceof java.net.Inet4Address) {
                    adr = a;
                    break;
                }
            }
        } catch (Exception e) {
            return ECHEC;
        }
        long meilleur = Long.MAX_VALUE;
        int ok = 0;
        int ko = 0;
        for (int k = 0; k < 3 && ok < 2 && !(ok == 0 && ko >= 2); k++) {
            Socket s = null;
            try {
                s = net != null ? net.getSocketFactory().createSocket() : new Socket();
                long t0 = System.nanoTime();
                s.connect(new InetSocketAddress(adr, port), 4000);
                meilleur = Math.min(meilleur, (System.nanoTime() - t0) / 1000000L);
                ok++;
            } catch (IOException e) {
                ko++;
            } finally {
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException ignore) {
                        // fermeture sans importance
                    }
                }
            }
        }
        return meilleur == Long.MAX_VALUE ? ECHEC : Math.max(1, meilleur);
    }

    /** L'hôte se résout-il ? (pour les protocoles UDP, sans port TCP à mesurer) */
    private static boolean hoteResolu(String hote, Network net) {
        if (hote == null || hote.trim().isEmpty()) {
            return false;
        }
        try {
            if (net != null) {
                net.getAllByName(hote.trim());
            } else {
                InetAddress.getAllByName(hote.trim());
            }
            return true;
        } catch (Exception e) {
            try {
                InetAddress.getAllByName(hote.trim());
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /**
     * SlowDNS sur un réseau précis : la requête part vers TOUS les résolveurs en même temps
     * (celui configuré + ceux du réseau, Orange, Moov ou Wi-Fi) et la première réponse valide gagne.
     */
    private static long pingDnsSur(Configuration c, Context ctx, Network net) {
        String domaine = c.slowNs.trim();
        while (domaine.endsWith(".")) {
            domaine = domaine.substring(0, domaine.length() - 1);
        }
        if (domaine.isEmpty()) {
            return ECHEC;
        }
        List<String> res = new ArrayList<>();
        if (c.slowResolveur != null) {
            for (String s : c.slowResolveur.split("[,;\\s]+")) {
                String v = s.trim();
                if (!v.isEmpty() && !res.contains(v)) {
                    res.add(v);
                }
            }
        }
        if (res.isEmpty()) {
            res.add("8.8.8.8");
        }
        for (String s : dnsDe(ctx, net)) {
            if (!res.contains(s)) {
                res.add(s);
            }
        }
        if (res.size() > 5) {
            res = new ArrayList<>(res.subList(0, 5));
        }
        List<InetSocketAddress> cibles = new ArrayList<>();
        for (String r : res) {
            String hote = r;
            int port = 53;
            int k = r.lastIndexOf(':');
            if (k > 0 && r.indexOf(':') == k) {
                try {
                    port = Integer.parseInt(r.substring(k + 1));
                    hote = r.substring(0, k);
                } catch (NumberFormatException ignore) {
                    // pas de port
                }
            }
            try {
                cibles.add(new InetSocketAddress(InetAddress.getByName(hote), port));
            } catch (Exception ignore) {
                // résolveur illisible : on passe au suivant
            }
        }
        if (cibles.isEmpty()) {
            return ECHEC;
        }
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
            if (net != null) {
                net.bindSocket(ds);
            }
            final boolean[] vivant = new boolean[cibles.size()];
            java.util.Arrays.fill(vivant, true);
            int nbVivants = cibles.size();
            byte[] buf = new byte[1500];
            for (int tour = 0; tour < 2 && nbVivants > 0; tour++) {
                // un identifiant par résolveur ; on envoie à tous d'un coup
                final int[] ids = new int[cibles.size()];
                final long[] t0 = new long[cibles.size()];
                for (int i = 0; i < cibles.size(); i++) {
                    if (!vivant[i]) {
                        continue;
                    }
                    ids[i] = RND.nextInt(65536);
                    byte[] q = requeteDns(domaine, ids[i]);
                    try {
                        t0[i] = System.nanoTime();
                        ds.send(new DatagramPacket(q, q.length, cibles.get(i)));
                    } catch (IOException e) {
                        vivant[i] = false;
                        nbVivants--;
                    }
                }
                long limite = System.nanoTime() + (tour == 0 ? 3500L : 5000L) * 1000000L;
                while (nbVivants > 0) {
                    long reste = (limite - System.nanoTime()) / 1000000L;
                    if (reste <= 0) {
                        break;
                    }
                    ds.setSoTimeout((int) reste);
                    DatagramPacket rep = new DatagramPacket(buf, buf.length);
                    try {
                        ds.receive(rep);
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                    long recu = System.nanoTime();
                    if (rep.getLength() < 12) {
                        continue;
                    }
                    int idRep = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
                    for (int i = 0; i < cibles.size(); i++) {
                        if (!vivant[i] || ids[i] != idRep) {
                            continue;
                        }
                        int rc = buf[3] & 0x0F;
                        if ((buf[2] & 0x80) == 0 || rc == 5) {
                            vivant[i] = false; // REFUSED : ce résolveur est écarté
                            nbVivants--;
                        } else {
                            return Math.max(1, (recu - t0[i]) / 1000000L);
                        }
                        break;
                    }
                }
            }
            return ECHEC;
        } catch (IOException e) {
            return ECHEC;
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }

    private static byte[] requeteDns(String domaine, int id) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(id >> 8);
        o.write(id & 0xFF);
        o.write(0x01);
        o.write(0x00);
        o.write(0);
        o.write(1);
        for (int i = 0; i < 6; i++) {
            o.write(0);
        }
        String nom = "yw" + Integer.toHexString(RND.nextInt(0xFFFFF)) + "." + domaine;
        for (String lab : nom.split("\\.")) {
            if (lab.isEmpty()) {
                continue;
            }
            byte[] b = lab.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int n = Math.min(b.length, 63);
            o.write(n);
            o.write(b, 0, n);
        }
        o.write(0);
        o.write(0);
        o.write(16);
        o.write(0);
        o.write(1);
        return o.toByteArray();
    }

    /** DNS du réseau actuel (Orange, Moov ou Wi-Fi), utilisés si le résolveur configuré ne répond pas. */
    private static List<String> dnsReseau(Context ctx) {
        List<String> r = new ArrayList<>();
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && cm.getActiveNetwork() != null) {
                LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
                if (lp != null) {
                    for (InetAddress a : lp.getDnsServers()) {
                        if (a instanceof java.net.Inet4Address) {
                            r.add(a.getHostAddress());
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
            // pas d'accès à l'état réseau : on garde seulement le résolveur configuré
        }
        return r;
    }

    /** DNS IPv4 d'un réseau précis ; sans réseau, ceux du réseau actif. */
    private static List<String> dnsDe(Context ctx, Network net) {
        if (net == null) {
            return dnsReseau(ctx);
        }
        List<String> r = new ArrayList<>();
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            LinkProperties lp = cm == null ? null : cm.getLinkProperties(net);
            if (lp != null) {
                for (InetAddress a : lp.getDnsServers()) {
                    if (a instanceof java.net.Inet4Address) {
                        r.add(a.getHostAddress());
                    }
                }
            }
        } catch (Throwable ignore) {
            // pas d'accès à ce réseau
        }
        return r;
    }

    private static void appliquer(TextView t, Long v) {
        if (v == null) {
            t.setText("");
            return;
        }
        long x = v;
        if (x >= 0) {
            t.setText(x + " ms");
            t.setTextColor(x <= 150 ? 0xFF4ADE80 : (x <= 400 ? 0xFFFB923C : 0xFFF87171));
        } else if (x == SANS_MESURE) {
            t.setText("OK");
            t.setTextColor(0xFF4ADE80);
        } else if (x == EN_COURS) {
            t.setText("…");
            t.setTextColor(0xFF9CA3AF);
        } else {
            t.setText("Échec");
            t.setTextColor(0xFFF87171);
        }
    }
}
