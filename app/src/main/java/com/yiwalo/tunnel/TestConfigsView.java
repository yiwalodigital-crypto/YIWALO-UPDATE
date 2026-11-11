package com.yiwalo.tunnel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.net.Network;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.vpn.GenerateurXray;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TestConfigsView extends LinearLayout {
    private static final class Res { int etat; long ms; boolean icmp; int recus; String note; String detail; boolean relaye; String reseau; }
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context ctx;
    private final ConfigStore store;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, Res> res = new HashMap<>();
    private final Map<String, TextView> puces = new HashMap<>();
    private final Map<String, TextView> subs = new HashMap<>();
    private final Map<String, String> bases = new HashMap<>();
    private List<Configuration> liste = new ArrayList<>();
    private ExecutorService pool;
    private int gen, faits;
    private boolean enCours;
    private TextView resume, bouton, supprimer, rapportBtn;
    private ProgressBar barre;
    private LinearLayout conteneur;

    TestConfigsView(Context ctx, ConfigStore store, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        this.store = store;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 24));
        addView(Ui.entete(ctx, "Ping des serveurs", retour));

        LinearLayout c = Ui.carte(ctx);
        int p = Ui.dp(ctx, 16);
        c.setPadding(p, p, p, p);
        resume = Ui.texte(ctx, "", 14f, Ui.TEXTE, true);
        c.addView(resume);
        barre = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        barre.setProgressTintList(ColorStateList.valueOf(Ui.VERT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, Ui.dp(ctx, 6));
        bp.topMargin = Ui.dp(ctx, 10);
        c.addView(barre, bp);
        bouton = Ui.bouton(ctx, "Lancer le ping", Ui.BLEU, this::basculer);
        c.addView(bouton);
        supprimer = Ui.bouton(ctx, "Supprimer les échecs", Ui.FOND2, this::confirmerSuppression);
        supprimer.setBackground(Ui.fond(ctx, Ui.FOND2, 12, Ui.ROUGE));
        supprimer.setTextColor(Ui.ROUGE);
        supprimer.setVisibility(GONE);
        c.addView(supprimer);
        rapportBtn = Ui.bouton(ctx, "Copier le rapport", Ui.FOND2, this::copierRapport);
        rapportBtn.setBackground(Ui.fond(ctx, Ui.FOND2, 12, Ui.BORD));
        rapportBtn.setVisibility(GONE);
        c.addView(rapportBtn);
        addView(c);

        conteneur = Ui.carte(ctx);
        addView(conteneur);
        try {
            if (ctx instanceof android.app.Activity
                    && ctx.checkSelfPermission("android.permission.READ_PHONE_STATE")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ((android.app.Activity) ctx).requestPermissions(
                        new String[]{"android.permission.READ_PHONE_STATE"}, 77);
            }
        } catch (RuntimeException ignore) {
            // permission indisponible
        }
        demarrer();
    }

    private long score(Configuration c) {
        Res r = res.get(c.id);
        if (r == null) return 500000;
        switch (r.etat) {
            case 2: return r.ms;
            case 5: return 100000;
            case 4: return 200000;
            case 3: return 300000;
            default: return 400000;
        }
    }

    private void majPuce(String id) {
        TextView t = puces.get(id);
        if (t == null) return;
        Res r = res.get(id);
        String s = "—";
        int col = Ui.DISCRET;
        if (r != null) {
            switch (r.etat) {
                case 1: s = "Test…"; col = Ui.BLEU; break;
                case 2:
                    s = r.ms + " ms";
                    col = r.ms < 150 ? Ui.VERT : r.ms < 400 ? Ui.ORANGE : Ui.ROUGE;
                    break;
                case 3: s = "Échec"; col = Ui.ROUGE; break;
                case 4: s = "Non testable"; break;
                case 5: s = "Hôte OK"; col = Ui.BLEU; break;
                default: break;
            }
        }
        t.setText(s);
        t.setTextColor(col);
        t.setBackground(Ui.fond(ctx, (col & 0x00FFFFFF) | 0x26000000, 14, 0));
        TextView sv = subs.get(id);
        String base = bases.get(id);
        if (sv != null && base != null) {
            String extra = "";
            if (r != null) {
                if (r.note != null) extra = " · " + r.note;
                else if (r.etat == 2) extra = r.icmp ? " · ping " + r.recus + "/3" : " · TCP";
                if (r.etat == 2 && r.reseau != null) extra += " · " + r.reseau;
            }
            sv.setText(base + extra);
        }
    }

    private View ligne(Configuration c) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11));
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(VERTICAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView n = Ui.texte(ctx, c.nom, 14f, Ui.TEXTE, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        TextView s = Ui.texte(ctx, c.type.name() + " · " + c.serveur + ":" + c.port,
                12f, Ui.DISCRET, false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(s);
        subs.put(c.id, s);
        bases.put(c.id, c.type.name() + " · " + c.serveur + ":" + c.port);
        r.addView(t);
        TextView puce = Ui.texte(ctx, "", 12f, Ui.DISCRET, true);
        puce.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(WRAP, WRAP);
        pp.leftMargin = Ui.dp(ctx, 10);
        puce.setLayoutParams(pp);
        puces.put(c.id, puce);
        r.addView(puce);
        majPuce(c.id);
        final Configuration cf = c;
        r.setOnClickListener(v -> detail(cf));
        return r;
    }

    private void construireListe(boolean trie) {
        conteneur.removeAllViews();
        puces.clear();
        subs.clear();
        if (liste.isEmpty()) {
            TextView v = Ui.texte(ctx, "Aucune configuration.", 14f, Ui.DISCRET, false);
            v.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14));
            conteneur.addView(v);
            return;
        }
        List<Configuration> l = new ArrayList<>(liste);
        if (trie) Collections.sort(l, (a, b) -> Long.compare(score(a), score(b)));
        boolean premier = true;
        for (Configuration c : l) {
            if (!premier) {
                View sep = new View(ctx);
                sep.setBackgroundColor(Ui.BORD);
                conteneur.addView(sep, new LinearLayout.LayoutParams(MATCH, 1));
            }
            premier = false;
            conteneur.addView(ligne(c));
        }
    }

    private void detail(Configuration c) {
        Res r = res.get(c.id);
        StringBuilder sb = new StringBuilder();
        sb.append("Type : ").append(c.type.name()).append('\n');
        sb.append("Serveur : ").append(c.serveur).append(':').append(c.port).append('\n');
        if (c.sniManuel != null && !c.sniManuel.isEmpty()) {
            sb.append("SNI : ").append(c.sniManuel).append('\n');
        }
        if (r == null) {
            sb.append("\nPas encore testé.");
        } else {
            if (r.note != null) sb.append("\nRésultat : ").append(r.note);
            if (r.detail != null) sb.append("\n\n").append(r.detail);
        }
        try {
            new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(T.t(c.nom))
                    .setMessage(T.t(sb.toString()))
                    .setPositiveButton(T.t("Fermer"), null)
                    .show();
        } catch (RuntimeException ignore) {
            // fenêtre indisponible
        }
    }

    private static String filtrer(String j) {
        StringBuilder sb = new StringBuilder();
        for (String l : j.split("\n")) {
            String m = l.toLowerCase(Locale.ROOT).trim();
            if (m.isEmpty() || m.contains("deprecated") || m.contains("reading config")
                    || m.contains("started") || m.startsWith("xray ")
                    || m.contains("unified platform")) continue;
            sb.append(l.trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String causeXray(String journal) {
        String ligne = "";
        for (String l : journal.split("\n")) {
            String m = l.toLowerCase(Locale.ROOT);
            if (m.contains("failed") || m.contains("dial") || m.contains("tls")
                    || m.contains("refused") || m.contains("no such host")
                    || m.contains("timeout") || m.contains("reset") || m.contains("eof")
                    || m.contains("certificate")) {
                ligne = l;
            }
        }
        String m = ligne.toLowerCase(Locale.ROOT);
        if (m.isEmpty()) return "";
        if (m.contains("no such host")) return "nom de domaine introuvable";
        if (m.contains("certificate")) return "certificat TLS refusé";
        if (m.contains("timeout")) return "délai dépassé : le serveur ne répond pas";
        if (m.contains("refused")) return "connexion refusée par le serveur";
        if (m.contains("reset")) return "connexion coupée par le réseau";
        if (m.contains("eof")) return "fermé par le serveur";
        if (m.contains("tls")) return "erreur TLS";
        int i = ligne.lastIndexOf(" > ");
        String c = (i >= 0 ? ligne.substring(i + 3) : ligne).trim();
        return c.length() > 70 ? c.substring(0, 70) : c;
    }

    private void copierRapport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Réseaux détectés : ").append(RESEAUX_DETECTES.isEmpty() ? "aucun" : RESEAUX_DETECTES)
                .append("\nDiagnostic SIM : ").append(DIAG_SIM.isEmpty() ? "-" : DIAG_SIM).append("\n\n");
        for (Configuration c : liste) {
            Res r = res.get(c.id);
            sb.append(c.nom).append(" | ").append(c.type.name()).append(" | ")
                    .append(c.serveur).append(':').append(c.port).append('\n');
            if (r == null) {
                sb.append("  pas testé\n");
            } else {
                sb.append("  ").append(r.etat == 2 ? r.ms + " ms" : "échec");
                if (r.note != null) sb.append(" - ").append(r.note);
                if (r.reseau != null) sb.append(" [").append(r.reseau).append("]");
                sb.append('\n');
                if (r.detail != null && !r.detail.isEmpty()) {
                    sb.append("  ").append(r.detail.replace("\n", "\n  ")).append('\n');
                }
            }
            sb.append('\n');
        }
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(
                android.content.ClipData.newPlainText("Rapport ping", sb.toString()));
        T.toast(ctx, "Rapport copié", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void majBouton() {
        bouton.setText(enCours ? "Arrêter" : (faits > 0 ? "Relancer" : "Lancer le ping"));
        bouton.setBackground(Ui.fond(ctx, enCours ? Ui.ROUGE : Ui.BLEU, 12, 0));
    }

    private void basculer() {
        if (enCours) arreter(); else demarrer();
    }

    private void arreter() {
        gen++;
        if (pool != null) pool.shutdownNow();
        enCours = false;
        List<String> aRetirer = new ArrayList<>();
        for (Map.Entry<String, Res> e : res.entrySet()) {
            if (e.getValue().etat == 1) aRetirer.add(e.getKey());
        }
        for (String id : aRetirer) {
            res.remove(id);
            ResultatsPing.retirer(id);
        }
        for (Configuration c : liste) majPuce(c.id);
        resume.setText("Arrêté : " + faits + "/" + liste.size());
        majBouton();
    }

    private void demarrer() {
        liste = store.lister();
        res.clear();
        faits = 0;
        supprimer.setVisibility(GONE);
        if (liste.isEmpty()) {
            resume.setText("Aucune configuration");
            barre.setVisibility(GONE);
            bouton.setVisibility(GONE);
            construireListe(false);
            return;
        }
        if (pool != null) pool.shutdownNow();
        final int g = ++gen;
        enCours = true;
        barre.setMax(liste.size());
        barre.setProgress(0);
        resume.setText("0/" + liste.size());
        majBouton();
        construireListe(false);
        pool = Executors.newFixedThreadPool(3);
        for (final Configuration c : ResultatsPing.ordreMixte(liste)) {
            pool.execute(() -> {
                if (g != gen) return;
                h.post(() -> {
                    if (g != gen) return;
                    Res enCoursRes = new Res();
                    enCoursRes.etat = 1;
                    res.put(c.id, enCoursRes);
                    ResultatsPing.noter(c.id, ResultatsPing.EN_COURS);
                    majPuce(c.id);
                });
                final Res r = testerSim(c);
                h.post(() -> {
                    if (g != gen) return;
                    res.put(c.id, r);
                    ResultatsPing.noter(c.id, r.etat == 2 ? r.ms : (r.etat == 5 ? ResultatsPing.SANS_MESURE : ResultatsPing.ECHEC));
                    majPuce(c.id);
                    faits++;
                    barre.setProgress(faits);
                    resume.setText(faits + "/" + liste.size());
                    if (faits == liste.size()) fin();
                });
            });
        }
    }

    private Res pingIcmp(String ip) {
        try {
            Process p = new ProcessBuilder("/system/bin/ping", "-c", "3", "-W", "2", "-w", "8", ip)
                    .redirectErrorStream(true).start();
            Pattern pat = Pattern.compile("time[=<]([0-9.]+)\\s*ms");
            double somme = 0;
            int n = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    Matcher m = pat.matcher(l);
                    if (m.find()) {
                        somme += Double.parseDouble(m.group(1));
                        n++;
                    }
                }
            }
            p.destroy();
            if (n == 0) return null;
            Res r = new Res();
            r.etat = 2;
            r.icmp = true;
            r.recus = n;
            r.ms = Math.max(1, Math.round(somme / n));
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private String raison(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) return "TCP : délai dépassé";
        if (e instanceof java.net.ConnectException) return "TCP : connexion refusée";
        if (e instanceof java.net.NoRouteToHostException) return "TCP : pas de route";
        if (e instanceof java.net.UnknownHostException) return "hôte introuvable";
        return "TCP : " + e.getClass().getSimpleName();
    }

    private byte[] requeteDns(String domaine) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        java.util.Random rnd = new java.util.Random();
        int id = rnd.nextInt(65536);
        o.write(id >> 8);
        o.write(id & 0xFF);
        o.write(0x01); o.write(0x00);
        o.write(0); o.write(1);
        o.write(0); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0);
        String nom = "yw" + Integer.toHexString(rnd.nextInt(0xFFFFF)) + "." + domaine;
        for (String lab : nom.split("\\.")) {
            if (lab.isEmpty()) continue;
            byte[] b = lab.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int n = Math.min(b.length, 63);
            o.write(n);
            o.write(b, 0, n);
        }
        o.write(0);
        o.write(0); o.write(16);
        o.write(0); o.write(1);
        return o.toByteArray();
    }

    /** Slow DNS : vraie requête DNS vers le résolveur pour le domaine NS de la configuration. */
    private Res dnsTest(Configuration c, android.net.Network net) {
        Res r = new Res();
        String[] noms = {"NOERROR", "FORMERR", "SERVFAIL", "NXDOMAIN", "NOTIMP", "REFUSED"};
        String dnsRes = net != null ? dnsDe(getContext(), net) : dnsReseau(getContext());
        String liste = (c.slowResolveur == null || c.slowResolveur.trim().isEmpty() ? "" : c.slowResolveur + ",")
                + dnsRes + ",8.8.8.8:53,1.1.1.1";
        String domaine = c.slowNs.trim();
        long best = Long.MAX_VALUE;
        String note = null;
        StringBuilder detail = new StringBuilder();
        String echec = "résolveur sans réponse";
        for (String tok : liste.split("[\\s,;]+")) {
            if (tok.isEmpty()) continue;
            String hote = tok;
            int port = 53;
            int k = tok.lastIndexOf(':');
            if (k > 0 && tok.indexOf(':') == k) {
                try {
                    port = Integer.parseInt(tok.substring(k + 1));
                    hote = tok.substring(0, k);
                } catch (NumberFormatException ignore) {
                    // pas de port
                }
            }
            DatagramSocket so = null;
            String etatRes = "réponse invalide";
            try {
                InetAddress a = net != null ? net.getByName(hote) : InetAddress.getByName(hote);
                byte[] q = requeteDns(domaine);
                so = socketUdpSur(getContext(), net, a);
                long t0 = System.nanoTime();
                so.send(new DatagramPacket(q, q.length, a, port));
                byte[] buf = new byte[1500];
                DatagramPacket rep = new DatagramPacket(buf, buf.length);
                // on ignore les paquets qui ne répondent pas à notre requête (autre identifiant)
                long limite = t0 + (net != null ? 2000L : 3000L) * 1000000L;
                boolean recu = false;
                while (!recu) {
                    long reste = (limite - System.nanoTime()) / 1000000L;
                    if (reste <= 0) throw new java.net.SocketTimeoutException();
                    so.setSoTimeout((int) reste);
                    rep.setLength(buf.length);
                    so.receive(rep);
                    recu = rep.getLength() >= 12 && buf[0] == q[0] && buf[1] == q[1];
                }
                long ms = (System.nanoTime() - t0) / 1000000L;
                if ((buf[2] & 0x80) != 0) {
                    int rc = buf[3] & 0x0F;
                    if (rc == 2) {
                        etatRes = "SERVFAIL : serveur NS injoignable";
                    } else if (rc == 5) {
                        etatRes = "résolveur : REFUSED";
                    } else {
                        etatRes = "OK " + ms + " ms";
                        if (ms < best) {
                            best = ms;
                            note = "DNS " + (rc < noms.length ? noms[rc] : "rc" + rc);
                        }
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                etatRes = "résolveur : délai dépassé";
            } catch (Exception e) {
                etatRes = "résolveur : " + e.getClass().getSimpleName();
            } finally {
                if (so != null) so.close();
                if (!etatRes.startsWith("OK")) echec = etatRes;
                detail.append(hote).append(':').append(port).append(" → ").append(etatRes).append(" ; ");
            }
        }
        if (best != Long.MAX_VALUE) {
            r.etat = 2;
            r.ms = Math.max(1, best);
            r.note = note;
        } else {
            r.etat = 3;
            r.note = echec + " | essayés : " + detail;
        }
        return r;
    }

    private static String court(String s) {
        if (s == null) return "";
        s = s.trim();
        int n = s.indexOf('\n');
        if (n > 0) s = s.substring(0, n);
        return s.length() > 70 ? s.substring(0, 70) : s;
    }

    private static void lireTout(java.io.InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) {
            int k = in.read(b, n, b.length - n);
            if (k < 0) throw new IOException("connexion coupée par le serveur");
            n += k;
        }
    }

    private static String raisonSocks(int code) {
        switch (code) {
            case 1: return "échec général du serveur";
            case 2: return "règle du serveur : refusé";
            case 3: return "réseau injoignable";
            case 4: return "serveur injoignable";
            case 5: return "connexion refusée par le serveur";
            case 6: return "délai dépassé (serveur injoignable)";
            default: return "erreur SOCKS " + code;
        }
    }

    /** Requête HTTP réelle à travers le moteur (SOCKS local), temps total en ms. */
    private long viaSocks(int port, String user, String pass) throws IOException {
        Socket s = new Socket();
        try {
            long t0 = System.nanoTime();
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(8000);
            java.io.OutputStream o = s.getOutputStream();
            java.io.InputStream in = s.getInputStream();
            o.write(new byte[]{5, 1, 2});
            o.flush();
            byte[] rep = new byte[2];
            lireTout(in, rep);
            if (rep[0] != 5 || rep[1] != 2) throw new IOException("moteur : authentification refusée");
            byte[] u = user.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] pw = pass.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            java.io.ByteArrayOutputStream a = new java.io.ByteArrayOutputStream();
            a.write(1);
            a.write(u.length);
            a.write(u, 0, u.length);
            a.write(pw.length);
            a.write(pw, 0, pw.length);
            o.write(a.toByteArray());
            o.flush();
            lireTout(in, rep);
            if (rep[1] != 0) throw new IOException("moteur : identifiants refusés");
            byte[] h = "cp.cloudflare.com".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            java.io.ByteArrayOutputStream q = new java.io.ByteArrayOutputStream();
            q.write(5);
            q.write(1);
            q.write(0);
            q.write(3);
            q.write(h.length);
            q.write(h, 0, h.length);
            q.write(0);
            q.write(80);
            o.write(q.toByteArray());
            o.flush();
            byte[] e = new byte[4];
            lireTout(in, e);
            if (e[1] != 0) throw new IOException(raisonSocks(e[1]));
            int atyp = e[3];
            int reste = atyp == 1 ? 6 : atyp == 4 ? 18 : in.read() + 2;
            byte[] saut = new byte[Math.max(0, reste)];
            lireTout(in, saut);
            byte[] req = ("GET /generate_204 HTTP/1.1\r\nHost: cp.cloudflare.com\r\n"
                    + "Connection: keep-alive\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            // 1re requete : etablit la connexion vers le serveur (TCP/TLS/WS), non comptee
            o.write(req);
            o.flush();
            lireEntetes(in);
            // 2e requete sur la meme connexion : latence reelle (RTT)
            long t1 = System.nanoTime();
            o.write(req);
            o.flush();
            byte[] b = new byte[12];
            lireTout(in, b);
            String st = new String(b, java.nio.charset.StandardCharsets.US_ASCII);
            if (!st.startsWith("HTTP/1.")) throw new IOException("réponse inattendue du serveur");
            return (System.nanoTime() - t1) / 1000000L;
        } finally {
            try { s.close(); } catch (Exception ignore) { }
        }
    }

    /** Lit jusqu'a la fin des entetes HTTP (CRLF CRLF). */
    private static void lireEntetes(java.io.InputStream in) throws IOException {
        int etat = 0;
        int n = 0;
        while (etat < 4 && n < 8192) {
            int c = in.read();
            if (c < 0) throw new IOException("connexion fermée par le serveur");
            n++;
            if ((etat % 2 == 0 && c == '\r') || (etat % 2 == 1 && c == '\n')) etat++;
            else etat = (c == '\r') ? 1 : 0;
        }
    }

    /** Test réel : lance le moteur Xray avec cette configuration, sans interface TUN. */
    private Res xrayTest(Configuration c, android.net.Network net) {
        Res r = new Res();
        final List<RelaisReseau> relais = new ArrayList<>();
        File xray = new File(ctx.getApplicationInfo().nativeLibraryDir, "libxray.so");
        if (!xray.exists()) return null;
        Process p = null;
        File cfg = null;
        try {
            GenerateurXray.Resultat g = GenerateurXray.generer(c, c.serveur);
            JSONObject o = new JSONObject(g.json);
            JSONArray in = o.getJSONArray("inbounds");
            JSONArray garde = new JSONArray();
            for (int i = 0; i < in.length(); i++) {
                JSONObject x = in.getJSONObject(i);
                if (!"tun".equals(x.optString("protocol"))) garde.put(x);
            }
            o.put("inbounds", garde);
            o.put("log", new JSONObject().put("loglevel", "info").put("access", "none"));
            if (net != null) r.relaye = RelaisReseau.rediriger(o, net, relais);
            File dossier = new File(ctx.getCacheDir(), "tests");
            dossier.mkdirs();
            cfg = File.createTempFile("yiwalo", ".json", dossier);
            try (java.io.FileOutputStream fo = new java.io.FileOutputStream(cfg)) {
                fo.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            p = new ProcessBuilder(xray.getAbsolutePath(), "run", "-c", cfg.getAbsolutePath())
                    .redirectErrorStream(true).start();
            boolean pret = false;
            for (int i = 0; i < 30 && !pret && p.isAlive(); i++) {
                Socket s = new Socket();
                try {
                    s.connect(new InetSocketAddress("127.0.0.1", g.portTest), 200);
                    pret = true;
                } catch (Exception e) {
                    Thread.sleep(150);
                } finally {
                    try { s.close(); } catch (Exception ignore) { }
                }
            }
            if (!pret) {
                String sortie = "";
                try {
                    java.io.InputStream es = p.getInputStream();
                    if (es.available() > 0) {
                        byte[] b = new byte[400];
                        sortie = new String(b, 0, es.read(b), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception ignore) {
                    // pas de sortie
                }
                r.etat = 3;
                r.note = "moteur : " + (sortie.isEmpty() ? "démarrage impossible" : court(sortie));
                r.detail = sortie.isEmpty() ? "Le moteur s'est arrêté sans message." : sortie;
                return r;
            }
            long best = Long.MAX_VALUE;
            String erreur = null;
            String detail = null;
            for (int k = 0; k < 2; k++) {
                try {
                    best = Math.min(best, viaSocks(g.portTest, g.utilisateurTest, g.motDePasseTest));
                } catch (java.net.SocketTimeoutException e) {
                    erreur = "délai dépassé (serveur injoignable)";
                    detail = String.valueOf(e);
                    break;
                } catch (Exception e) {
                    erreur = court(e.getMessage());
                    detail = String.valueOf(e);
                    break;
                }
            }
            if (best == Long.MAX_VALUE) {
                r.etat = 3;
                try { Thread.sleep(400); } catch (InterruptedException ignore) { }
                String journal = "";
                try {
                    java.io.InputStream es = p.getInputStream();
                    int n = es.available();
                    if (n > 0) {
                        byte[] b = new byte[Math.min(n, 8000)];
                        int k = es.read(b);
                        if (k > 0) journal = new String(b, 0, k,
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception ignore) {
                    // pas de journal
                }
                journal = filtrer(journal);
                String cause = causeXray(journal);
                r.note = !cause.isEmpty() ? cause
                        : (erreur == null || erreur.isEmpty() ? "échec" : erreur);
                String errRelais = "";
                for (RelaisReseau rr : relais) {
                    if (rr.erreur() != null) errRelais = "\n\nConnexion au serveur depuis ce réseau : " + rr.erreur();
                }
                r.detail = (detail == null ? "" : detail) + errRelais
                        + (journal.isEmpty() ? "" : "\n\nJournal du moteur :\n" + journal);
            } else {
                r.etat = 2;
                r.ms = Math.max(1, best);
                r.note = "test réel";
            }
            return r;
        } catch (Exception e) {
            r.etat = 3;
            r.note = court(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            r.detail = e.toString();
            return r;
        } finally {
            if (p != null) p.destroyForcibly();
            if (cfg != null) cfg.delete();
            for (RelaisReseau rr : relais) rr.fermer();
        }
    }

    private Res tester(Configuration c, android.net.Network net, String nomReseau) {
        Res r = new Res();
        String t = c.type.name().toUpperCase(Locale.ROOT);
        boolean slow = c.slowNs != null && !c.slowNs.trim().isEmpty();
        boolean udp = t.contains("UDP") || t.contains("HYSTERIA")
                || t.contains("QUIC") || t.contains("DNS") || t.equals("TUIC");
        if (slow) return dnsTest(c, net);
        if (!t.equals("SLOWDNS") && !t.equals("SSH")
                && !t.equals("HYSTERIA2") && !t.equals("TUIC")) {
            Res x = xrayTest(c, net);
            if (x != null) return x;
        }
        InetAddress a;
        try {
            a = net != null ? net.getByName(c.serveur) : InetAddress.getByName(c.serveur);
        } catch (Exception e) {
            r.etat = udp ? 4 : 3;
            r.note = "hôte introuvable";
            return r;
        }
        if (net == null && a instanceof Inet4Address) {
            Res i = pingIcmp(a.getHostAddress());
            if (i != null) return i;
        }
        if (udp) {
            r.etat = 5;
            r.note = "hôte résolu (UDP)";
            return r;
        }
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 2; k++) {
            Socket so = socketSur(getContext(), net);
            try {
                long t0 = System.nanoTime();
                so.connect(new InetSocketAddress(a, c.port), 4000);
                best = Math.min(best, (System.nanoTime() - t0) / 1000000L);
            } catch (Exception e) {
                if (k == 0) {
                    r.etat = 3;
                    r.note = raison(e);
                    return r;
                }
            } finally {
                try { so.close(); } catch (Exception ignore) { }
            }
        }
        r.etat = 2;
        r.ms = best;
        return r;
    }

    private void fin() {
        ResultatsPing.terminer();
        enCours = false;
        int ok = 0, ech = 0;
        Configuration meilleure = null;
        long mm = Long.MAX_VALUE;
        for (Configuration c : liste) {
            Res r = res.get(c.id);
            if (r == null) continue;
            if (r.etat == 2) {
                ok++;
                if (r.ms < mm) { mm = r.ms; meilleure = c; }
            } else if (r.etat == 3) {
                ech++;
            }
        }
        String s = ok + " sur " + liste.size() + " répondent";
        if (meilleure != null) s += "\nLa plus rapide : " + meilleure.nom + " (" + mm + " ms)";
        resume.setText(s);
        majBouton();
        construireListe(true);
        rapportBtn.setVisibility(VISIBLE);
        if (ech > 0) {
            supprimer.setText("Supprimer les échecs (" + ech + ")");
            supprimer.setVisibility(VISIBLE);
        }
    }

    private void confirmerSuppression() {
        final List<Configuration> mauvaises = new ArrayList<>();
        for (Configuration c : liste) {
            Res r = res.get(c.id);
            if (r != null && r.etat == 3) mauvaises.add(c);
        }
        if (mauvaises.isEmpty()) return;
        try {
            new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(T.t("Supprimer " + mauvaises.size()
                            + (mauvaises.size() > 1 ? " configurations ?" : " configuration ?")))
                    .setMessage(T.t("Elles ne répondent pas. Cette action est définitive."))
                    .setPositiveButton(T.t("Supprimer"), (x, y) -> {
                        for (Configuration c : mauvaises) {
                            store.supprimer(c.id);
                            res.remove(c.id);
                            liste.remove(c);
                        }
                        supprimer.setVisibility(GONE);
                        resume.setText(liste.size()
                                + (liste.size() > 1 ? " configurations restantes"
                                : " configuration restante"));
                        construireListe(true);
                    })
                    .setNegativeButton(T.t("Annuler"), null)
                    .show();
        } catch (RuntimeException ignore) {
            // fenêtre indisponible
        }
    }

    @Override protected void onDetachedFromWindow() {
        gen++;
        if (pool != null) pool.shutdownNow();
        super.onDetachedFromWindow();
    }

    private static boolean estReseauInternet(android.net.NetworkCapabilities nc) {
        return nc != null
                && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && !nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
    }

    /** Socket TCP hors VPN : sur le réseau demandé, sinon sur le premier réseau réel. */
    private static Socket socketSur(android.content.Context c, android.net.Network net) {
        try {
            if (net != null) return net.getSocketFactory().createSocket();
        } catch (Exception ignore) { }
        return socketHorsVpn(c);
    }

    private static Socket socketHorsVpn(android.content.Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            for (android.net.Network n : cm.getAllNetworks()) {
                if (estReseauInternet(cm.getNetworkCapabilities(n))) {
                    return n.getSocketFactory().createSocket();
                }
            }
        } catch (Exception ignore) { }
        return new Socket();
    }

    /** Socket UDP lié au réseau demandé ; sans réseau, celui qui possède le DNS testé. */
    private static DatagramSocket socketUdpSur(android.content.Context c, android.net.Network net,
            InetAddress a) throws java.io.IOException {
        if (net != null) {
            DatagramSocket ds = new DatagramSocket();
            try {
                net.bindSocket(ds);
            } catch (java.io.IOException e) {
                ds.close();
                throw e;
            }
            return ds;
        }
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.Network defaut = null;
            for (android.net.Network n : cm.getAllNetworks()) {
                if (!estReseauInternet(cm.getNetworkCapabilities(n))) continue;
                if (defaut == null) defaut = n;
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null && lp.getDnsServers().contains(a)) {
                    DatagramSocket ds = new DatagramSocket();
                    n.bindSocket(ds);
                    return ds;
                }
            }
            if (defaut != null) {
                DatagramSocket ds = new DatagramSocket();
                defaut.bindSocket(ds);
                return ds;
            }
        } catch (Exception ignore) { }
        return new DatagramSocket();
    }

    private static String dnsReseau(android.content.Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            StringBuilder sb = new StringBuilder();
            for (android.net.Network n : cm.getAllNetworks()) {
                if (!estReseauInternet(cm.getNetworkCapabilities(n))) continue;
                String d = dnsDe(c, n);
                if (!d.isEmpty()) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(d);
                }
            }
            if (sb.length() > 0) return sb.toString();
        } catch (Exception ignore) { }
        return "8.8.8.8";
    }

    /** DNS IPv4 d'un réseau précis, séparés par des virgules. */
    private static String dnsDe(android.content.Context c, android.net.Network n) {
        StringBuilder sb = new StringBuilder();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                for (InetAddress d : lp.getDnsServers()) {
                    if (d instanceof Inet4Address) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(d.getHostAddress());
                    }
                }
            }
        } catch (Exception ignore) { }
        return sb.toString();
    }

    // ---------- Réseaux disponibles : SIM Orange, SIM Moov, Wi-Fi ----------

    static final class Reseau {
        final android.net.Network net;
        final String nom;
        Reseau(android.net.Network net, String nom) { this.net = net; this.nom = nom; }
    }

    private static final java.util.List<Reseau> SIMS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<android.net.ConnectivityManager.NetworkCallback> CB_SIM = new ArrayList<>();
    private static final Map<String, String> DERNIER = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());
    private static boolean simActif = false;
    private static android.net.ConnectivityManager cmSim;
    private static long simJusqua = 0;
    static volatile String RESEAUX_DETECTES = "";
    static volatile String DIAG_SIM = "";

    private static final Runnable VERIF_SIM = new Runnable() {
        @Override public void run() {
            synchronized (TestConfigsView.class) {
                if (System.currentTimeMillis() > simJusqua) {
                    libererSim(cmSim);
                } else {
                    PRINCIPAL.postDelayed(this, 30000);
                }
            }
        }
    };

    private static final java.util.Set<Integer> SUB_PRETS = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
    private static final Map<Integer, Long> SUB_DEMANDE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Integer, String> SUB_DIAG = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Integer, String> SUB_NOMS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Noms des SIM dont le réseau mobile n'est pas ouvert (données non activables en même temps). */
    private static String simsAbsentes() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : SUB_NOMS.entrySet()) {
            if (!SUB_PRETS.contains(e.getKey())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getValue());
            }
        }
        return sb.toString();
    }
    private static final Map<Integer, android.net.ConnectivityManager.NetworkCallback> CB_SUB =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final ExecutorService RESEAUX = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "yiwalo-reseau");
        t.setDaemon(true);
        return t;
    });

    private static void libererSim(android.net.ConnectivityManager cmDonne) {
        for (android.net.ConnectivityManager.NetworkCallback cb : CB_SUB.values()) {
            try {
                if (cmDonne != null) cmDonne.unregisterNetworkCallback(cb);
            } catch (Exception ignore) { }
        }
        CB_SUB.clear();
        SUB_PRETS.clear();
        SUB_DEMANDE.clear();
        SUB_DIAG.clear();
        SIMS.clear();
        simActif = false;
    }

    private static void majDiag() {
        StringBuilder sb = new StringBuilder();
        for (String d : SUB_DIAG.values()) sb.append(d).append(" ; ");
        DIAG_SIM = sb.toString();
    }

    /** Prépare en arrière-plan le réseau de chaque SIM, avant même le premier ping. */
    static void preparerReseaux(final Context c) {
        try {
            if (c instanceof android.app.Activity
                    && c.checkSelfPermission("android.permission.READ_PHONE_STATE")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ((android.app.Activity) c).requestPermissions(
                        new String[]{"android.permission.READ_PHONE_STATE"}, 77);
                return;
            }
        } catch (RuntimeException ignore) {
            // permission indisponible
        }
        final Context app = c.getApplicationContext();
        new Thread(() -> ouvrirSim(app), "yiwalo-sim").start();
    }

    /**
     * Ouvre le réseau de chaque SIM (Android 12+) et le garde ouvert 3 minutes après le dernier test.
     * Une SIM dont le réseau n'est pas prêt est redemandée au plus une fois par minute.
     */
    private static synchronized void ouvrirSim(final android.content.Context c) {
        final long maintenant = System.currentTimeMillis();
        simJusqua = maintenant + 180000;
        if (android.os.Build.VERSION.SDK_INT < 31) { DIAG_SIM = "Android inférieur à 12 : SIM non gérée"; return; }
        try {
            if (c.checkSelfPermission("android.permission.READ_PHONE_STATE")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                DIAG_SIM = "permission Téléphone non accordée";
                return;
            }
            android.telephony.SubscriptionManager sm = (android.telephony.SubscriptionManager)
                c.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            java.util.List<android.telephony.SubscriptionInfo> l = sm.getActiveSubscriptionInfoList();
            if (l == null || l.isEmpty()) { DIAG_SIM = "aucune SIM lisible"; return; }
            final android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            cmSim = cm;
            final List<android.telephony.SubscriptionInfo> aDemander = new ArrayList<>();
            for (android.telephony.SubscriptionInfo si : l) {
                int id = si.getSubscriptionId();
                SUB_NOMS.put(id, String.valueOf(si.getCarrierName()));
                if (SUB_PRETS.contains(id)) continue;
                if (CB_SUB.containsKey(id)) continue; // demande déjà en attente : on la laisse aboutir
                Long dernier = SUB_DEMANDE.get(id);
                if (dernier != null && maintenant - dernier < 15000) continue;
                aDemander.add(si);
            }
            if (!simActif) {
                simActif = true;
                PRINCIPAL.postDelayed(VERIF_SIM, 30000);
            }
            if (aDemander.isEmpty()) return;
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(aDemander.size());
            final boolean premiereFois = SIMS.isEmpty();
            for (android.telephony.SubscriptionInfo si : aDemander) {
                final int id = si.getSubscriptionId();
                final String nom = String.valueOf(si.getCarrierName());
                android.net.ConnectivityManager.NetworkCallback ancien = CB_SUB.remove(id);
                if (ancien != null) {
                    try { cm.unregisterNetworkCallback(ancien); } catch (Exception ignore) { }
                }
                SUB_DEMANDE.put(id, maintenant);
                SUB_DIAG.put(id, nom + " : demande en cours");
                android.net.NetworkRequest rq = new android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(new android.net.TelephonyNetworkSpecifier.Builder()
                            .setSubscriptionId(id).build())
                    .build();
                android.net.ConnectivityManager.NetworkCallback cb = new android.net.ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(android.net.Network n) {
                        boolean connu = false;
                        for (Reseau r : SIMS) if (r.net.equals(n)) connu = true;
                        if (!connu) SIMS.add(new Reseau(n, nom));
                        SUB_PRETS.add(id);
                        SUB_DIAG.put(id, nom + " : réseau ouvert");
                        majDiag();
                        latch.countDown();
                    }
                    @Override public void onLost(android.net.Network n) {
                        for (Reseau r : SIMS) if (r.net.equals(n)) SIMS.remove(r);
                        SUB_PRETS.remove(id);
                        SUB_DEMANDE.remove(id);
                        SUB_DIAG.put(id, nom + " : réseau perdu");
                        majDiag();
                    }
                    @Override public void onUnavailable() {
                        CB_SUB.remove(id);
                        SUB_DIAG.put(id, nom + " : réseau mobile indisponible (le téléphone n'active pas les données de cette SIM en même temps)");
                        majDiag();
                        latch.countDown();
                    }
                };
                CB_SUB.put(id, cb);
                cm.requestNetwork(rq, cb, 30000);
            }
            majDiag();
            latch.await(premiereFois ? 12 : 4, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            DIAG_SIM = "erreur : " + e.getClass().getSimpleName();
        }
    }

    private static String nomDuReseau(android.content.Context c, android.net.Network n) {
        for (Reseau r : SIMS) if (r.net.equals(n)) return r.nom;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            if (nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                    c.getSystemService(android.content.Context.TELEPHONY_SERVICE);
                String op = tm == null ? "" : tm.getNetworkOperatorName();
                return op == null || op.isEmpty() ? "Données mobiles" : op;
            }
        } catch (Exception ignore) { }
        return "réseau";
    }

    /** Réseaux réels (hors VPN) : réseau actif d'abord, puis les autres, sans doublon. */
    static java.util.List<Reseau> reseauxActifs(android.content.Context c) {
        ouvrirSim(c);
        java.util.List<Reseau> l = new ArrayList<>();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.Network actif = cm.getActiveNetwork();
            java.util.List<android.net.Network> tous = new ArrayList<>();
            for (android.net.Network n : cm.getAllNetworks()) {
                if (estReseauInternet(cm.getNetworkCapabilities(n))) tous.add(n);
            }
            for (Reseau r : SIMS) {
                if (!tous.contains(r.net)) tous.add(r.net);
            }
            for (android.net.Network n : tous) {
                Reseau r = new Reseau(n, nomDuReseau(c, n));
                if (n.equals(actif)) l.add(0, r); else l.add(r);
            }
        } catch (Exception ignore) { }
        StringBuilder sb = new StringBuilder();
        for (Reseau r : l) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.nom);
        }
        RESEAUX_DETECTES = sb.toString();
        return l;
    }

    /** Réseaux réels utilisables (SIM Orange, SIM Moov, Wi-Fi), hors VPN : pour le ping silencieux. */
    static java.util.List<Network> reseauxReels(android.content.Context c) {
        java.util.List<Network> r = new ArrayList<>();
        for (Reseau x : reseauxActifs(c)) r.add(x.net);
        return r;
    }

    /**
     * Teste la configuration sur tous les réseaux disponibles (SIM Orange, SIM Moov, Wi-Fi) en même temps
     * et garde le premier qui répond. Le dernier réseau qui a marché est essayé seul d'abord.
     */
    private Res testerSim(final Configuration c) {
        final java.util.List<Reseau> rs = reseauxActifs(getContext());
        if (rs.isEmpty()) return tester(c, null, null);
        String pref = DERNIER.get(c.id);
        final java.util.List<Reseau> restants = new ArrayList<>(rs);
        Res premier = null;
        StringBuilder essais = new StringBuilder();
        if (pref != null) {
            for (int i = 0; i < restants.size(); i++) {
                if (restants.get(i).nom.equals(pref)) {
                    Reseau n = restants.remove(i);
                    Res r = tester(c, n.net, n.nom);
                    if (r.etat == 2 || r.etat == 5) {
                        r.reseau = n.nom;
                        return r;
                    }
                    essais.append(n.nom).append(" : ").append(r.note == null ? "échec" : r.note).append(" ; ");
                    premier = r;
                    break;
                }
            }
        }
        if (!restants.isEmpty()) {
            java.util.concurrent.ExecutorCompletionService<Res> ecs =
                    new java.util.concurrent.ExecutorCompletionService<>(RESEAUX);
            for (final Reseau n : restants) {
                ecs.submit(() -> {
                    Res r = tester(c, n.net, n.nom);
                    r.reseau = n.nom;
                    return r;
                });
            }
            for (int i = 0; i < restants.size(); i++) {
                try {
                    Res r = ecs.take().get();
                    if (r.etat == 2 || r.etat == 5) {
                        DERNIER.put(c.id, r.reseau);
                        return r;
                    }
                    essais.append(r.reseau).append(" : ").append(r.note == null ? "échec" : r.note).append(" ; ");
                    if (premier == null) premier = r;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    essais.append("erreur ; ");
                }
            }
        }
        if (premier != null && premier.relaye) {
            // dernier recours : le chemin d'origine, sans relais, sur le réseau par défaut
            Res r = tester(c, null, null);
            if (r.etat == 2 || r.etat == 5) {
                r.reseau = "réseau par défaut";
                return r;
            }
        }
        if (premier != null && rs.size() > 1) {
            premier.note = "échec sur " + rs.size() + " réseaux | " + essais;
        }
        if (premier != null && premier.etat == 3) {
            String absents = simsAbsentes();
            if (!absents.isEmpty()) {
                // une SIM n'a pas de réseau : l'échec n'est pas prouvé, on ne la marque pas comme défaillante
                premier.etat = 4;
                premier.note = "pas de réponse sur les réseaux ouverts ; " + absents
                        + " hors ligne : test impossible sur cette SIM";
            }
        }
        return premier != null ? premier : tester(c, null, null);
    }
}

/**
 * Le moteur Xray est un processus séparé : il ne suit pas le réseau choisi par l'appli.
 * Ce relais écoute en local, puis ouvre la vraie connexion avec un socket lié au réseau voulu
 * (SIM Orange, SIM Moov, Wi-Fi), hors VPN. Xray se connecte au relais au lieu du serveur.
 */
final class RelaisReseau {
    private final Network net;
    private final String hote;
    private final int port;
    private final String ipForcee;
    private final ServerSocket serveur;
    private final List<Socket> ouverts = Collections.synchronizedList(new ArrayList<Socket>());
    private volatile boolean ferme;
    private volatile String derniereErreur;

    private RelaisReseau(Network net, String hote, int port, String ipForcee) throws IOException {
        this.net = net;
        this.hote = hote;
        this.port = port;
        this.ipForcee = ipForcee;
        this.serveur = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::accepter, "relais-" + hote);
        t.setDaemon(true);
        t.start();
    }

    int portLocal() {
        return serveur.getLocalPort();
    }

    String erreur() {
        return derniereErreur;
    }

    void fermer() {
        ferme = true;
        try { serveur.close(); } catch (Exception ignore) { }
        synchronized (ouverts) {
            for (Socket s : ouverts) {
                try { s.close(); } catch (Exception ignore) { }
            }
            ouverts.clear();
        }
    }

    private void accepter() {
        while (!ferme) {
            try {
                final Socket client = serveur.accept();
                ouverts.add(client);
                Thread t = new Thread(() -> servir(client), "relais-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void servir(Socket client) {
        Socket distant = null;
        try {
            distant = ouvrir();
            ouverts.add(distant);
            final Socket c = client;
            final Socket d = distant;
            Thread retour = new Thread(() -> copier(d, c), "relais-retour");
            retour.setDaemon(true);
            retour.start();
            copier(c, d);
        } catch (Exception e) {
            derniereErreur = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " : " + e.getMessage());
            try { client.close(); } catch (Exception ignore) { }
            if (distant != null) try { distant.close(); } catch (Exception ignore) { }
        }
    }

    private Socket ouvrir() throws IOException {
        InetAddress[] adresses = ipForcee != null
                ? new InetAddress[]{InetAddress.getByName(ipForcee)}
                : net.getAllByName(hote);
        List<InetAddress> ordre = new ArrayList<>();
        for (InetAddress a : adresses) if (a instanceof java.net.Inet4Address) ordre.add(a);
        for (InetAddress a : adresses) if (!(a instanceof java.net.Inet4Address)) ordre.add(a);
        IOException derniere = null;
        for (int tour = 0; tour < 2; tour++) {
            for (InetAddress a : ordre) {
                Socket s = null;
                try {
                    s = net.getSocketFactory().createSocket();
                    s.connect(new InetSocketAddress(a, port), 6000);
                    s.setTcpNoDelay(true);
                    return s;
                } catch (IOException e) {
                    derniere = e;
                    if (s != null) try { s.close(); } catch (Exception ignore) { }
                }
            }
        }
        throw derniere != null ? derniere : new IOException("aucune adresse pour " + hote);
    }

    private void copier(Socket de, Socket vers) {
        byte[] b = new byte[16384];
        try {
            InputStream in = de.getInputStream();
            OutputStream out = vers.getOutputStream();
            int n;
            while ((n = in.read(b)) >= 0) {
                out.write(b, 0, n);
                out.flush();
            }
        } catch (Exception ignore) {
            // connexion terminée
        } finally {
            try { de.close(); } catch (Exception ignore) { }
            try { vers.close(); } catch (Exception ignore) { }
        }
    }

    /**
     * Réécrit les sorties Xray pour qu'elles passent par un relais lié au réseau.
     * Retourne false si rien n'a pu être redirigé (transports UDP, par exemple).
     */
    static boolean rediriger(JSONObject cfg, Network net, List<RelaisReseau> relais) throws Exception {
        JSONArray sorties = cfg.optJSONArray("outbounds");
        if (sorties == null) return false;
        JSONObject dns = cfg.optJSONObject("dns");
        JSONObject hotes = dns == null ? null : dns.optJSONObject("hosts");
        boolean fait = false;
        for (int i = 0; i < sorties.length(); i++) {
            JSONObject so = sorties.optJSONObject(i);
            if (so == null) continue;
            String proto = so.optString("protocol", "");
            if (proto.equals("freedom") || proto.equals("blackhole")
                    || proto.equals("dns") || proto.equals("loopback")) continue;
            JSONObject st = so.optJSONObject("streamSettings");
            String transport = st == null ? "tcp" : st.optString("network", "tcp");
            if (transport.equals("kcp") || transport.equals("mkcp")
                    || transport.equals("quic") || transport.equals("hysteria")) continue;
            JSONObject reglages = so.optJSONObject("settings");
            if (reglages == null) continue;
            List<JSONObject> cibles = new ArrayList<>();
            ajouterObjets(cibles, reglages.optJSONArray("vnext"));
            ajouterObjets(cibles, reglages.optJSONArray("servers"));
            if (reglages.has("address") && reglages.has("port")) cibles.add(reglages);
            for (JSONObject t : cibles) {
                String hote = t.optString("address", "");
                int port = t.optInt("port", 0);
                if (hote.isEmpty() || port <= 0
                        || hote.equals("127.0.0.1") || hote.equals("localhost")) continue;
                String ip = ipDeclaree(hotes, hote);
                RelaisReseau r = new RelaisReseau(net, hote, port, ip);
                relais.add(r);
                t.put("address", "127.0.0.1");
                t.put("port", r.portLocal());
                completerEntetes(st, hote);
                fait = true;
            }
        }
        return fait;
    }

    private static void ajouterObjets(List<JSONObject> l, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) l.add(o);
        }
    }

    private static String ipDeclaree(JSONObject hotes, String hote) {
        if (hotes == null || !hotes.has(hote)) return null;
        Object v = hotes.opt(hote);
        if (v instanceof JSONArray) v = ((JSONArray) v).opt(0);
        if (v instanceof String) {
            String s = (String) v;
            if (!s.isEmpty() && Character.isDigit(s.charAt(0))) return s;
        }
        return null;
    }

    private static JSONObject sous(JSONObject parent, String cle) throws Exception {
        JSONObject o = parent.optJSONObject(cle);
        if (o == null) {
            o = new JSONObject();
            parent.put(cle, o);
        }
        return o;
    }

    /** Garde le nom d'hôte réel dans SNI / Host / authority, puisque l'adresse devient 127.0.0.1. */
    private static void completerEntetes(JSONObject st, String hote) throws Exception {
        if (st == null) return;
        boolean estIp = hote.contains(":") || hote.matches("[0-9.]+");
        String securite = st.optString("security", "none");
        String sni = "";
        if (securite.equals("tls") || securite.equals("reality")) {
            JSONObject t = sous(st, securite.equals("tls") ? "tlsSettings" : "realitySettings");
            if (t.optString("serverName", "").isEmpty() && !estIp) t.put("serverName", hote);
            sni = t.optString("serverName", "");
        }
        String valeurHote = sni.isEmpty() ? hote : sni;
        String transport = st.optString("network", "tcp");
        switch (transport) {
            case "ws": {
                JSONObject w = sous(st, "wsSettings");
                JSONObject h = sous(w, "headers");
                if (w.optString("host", "").isEmpty() && h.optString("Host", "").isEmpty()
                        && h.optString("host", "").isEmpty()) {
                    h.put("Host", valeurHote);
                }
                break;
            }
            case "grpc": {
                JSONObject g = sous(st, "grpcSettings");
                if (g.optString("authority", "").isEmpty()) g.put("authority", valeurHote);
                break;
            }
            case "httpupgrade":
            case "xhttp":
            case "splithttp": {
                JSONObject g = sous(st, transport + "Settings");
                if (g.optString("host", "").isEmpty()) g.put("host", valeurHote);
                break;
            }
            case "h2":
            case "http": {
                JSONObject g = sous(st, "httpSettings");
                JSONArray a = g.optJSONArray("host");
                if (a == null || a.length() == 0) g.put("host", new JSONArray().put(valeurHote));
                break;
            }
            default:
                break;
        }
    }
}
