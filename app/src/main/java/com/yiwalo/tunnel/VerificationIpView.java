package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwalo.tunnel.vpn.YiwaloVpnService;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class VerificationIpView extends LinearLayout {
    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean attache = true;
    private final TextView vSous, vIp;
    private TextView vFai, vPays, vRegion, vVille, dernier;
    private String ip = "";

    VerificationIpView(final Context ctx, Runnable retour) {
        super(ctx);
        this.ctx = ctx;
        setOrientation(VERTICAL);
        addView(Ui.entete(ctx, "Vérification IP", retour));
        int p = Ui.dp(ctx, 20);

        LinearLayout carte = new LinearLayout(ctx);
        carte.setOrientation(VERTICAL);
        carte.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF1E3A8A, 0xFF2563EB});
        g.setCornerRadius(Ui.dp(ctx, 28));
        carte.setBackground(g);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = Ui.dp(ctx, 14);
        carte.setLayoutParams(cp);

        LinearLayout haut = new LinearLayout(ctx);
        haut.setOrientation(HORIZONTAL);
        haut.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_globe);
        ic.setColorFilter(Ui.TEXTE);
        ic.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14));
        GradientDrawable cercle = new GradientDrawable();
        cercle.setShape(GradientDrawable.OVAL);
        cercle.setColor(0x33FFFFFF);
        cercle.setStroke(Ui.dp(ctx, 1), 0x55FFFFFF);
        ic.setBackground(cercle);
        ic.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)));
        haut.addView(ic);
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Ui.dp(ctx, 16);
        t.setLayoutParams(tp);
        t.addView(Ui.texte(ctx, "Vérification IP", 22f, Ui.TEXTE, true));
        vSous = Ui.texte(ctx, "", 14f, 0xFFDCE6FF, false);
        t.addView(vSous);
        haut.addView(t);
        carte.addView(haut);

        TextView lib = Ui.texte(ctx, "IP :", 14f, Ui.TEXTE, false);
        lib.setPadding(0, Ui.dp(ctx, 18), 0, 0);
        carte.addView(lib);
        vIp = Ui.texte(ctx, "Recherche…", 36f, Ui.TEXTE, true);
        carte.addView(vIp);
        addView(carte);

        LinearLayout infos = Ui.carte(ctx);
        infos.addView(ligne("FAI :"));
        vFai = dernier;
        infos.addView(sep());
        infos.addView(ligne("Pays :"));
        vPays = dernier;
        infos.addView(sep());
        infos.addView(ligne("Région :"));
        vRegion = dernier;
        infos.addView(sep());
        infos.addView(ligne("Ville :"));
        vVille = dernier;
        addView(infos);

        addView(Ui.bouton(ctx, "Actualiser", Ui.BLEU, this::verifier));
        addView(Ui.bouton(ctx, "Copier l’IP", Ui.FOND2, this::copier));
        verifier();
    }

    private LinearLayout ligne(String label) {
        LinearLayout r = Ui.info(ctx, label, "—");
        r.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 14));
        dernier = (TextView) r.getChildAt(1);
        dernier.setTextSize(16f);
        return r;
    }

    private View sep() {
        View v = new View(ctx);
        v.setBackgroundColor(Ui.BORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(ctx, 16);
        lp.rightMargin = Ui.dp(ctx, 16);
        v.setLayoutParams(lp);
        return v;
    }

    private void verifier() {
        final boolean tunnel = SuiviVpn.etat() == EtatVpn.CONNECTE
                && YiwaloVpnService.portTest > 0;
        vSous.setText(tunnel ? "Tunnel connecté : IP vue par le serveur"
                : "Tunnel inactif : IP directe de l’appareil");
        vIp.setText("Recherche…");
        vFai.setText("—");
        vPays.setText("—");
        vRegion.setText("—");
        vVille.setText("—");
        new Thread(() -> {
            String[] r = null;
            if (tunnel) {
                try { r = viaTunnel(); } catch (Exception e) { r = null; }
            }
            if (r == null) {
                try { r = ipwho(); } catch (Exception e) { r = null; }
            }
            if (r == null) {
                try { r = ipapi(); } catch (Exception e) { r = null; }
            }
            final String[] f = r;
            h.post(() -> {
                if (!attache) return;
                if (f == null) {
                    vIp.setText("Indisponible");
                    vFai.setText("Réseau injoignable");
                    return;
                }
                ip = f[0];
                vIp.setText(f[0]);
                vFai.setText(f[1]);
                vPays.setText(f[2]);
                vRegion.setText(f[3]);
                vVille.setText(f[4]);
            });
        }, "yiwalo-verif-ip").start();
    }

    private static String v(String s) {
        return s == null || s.isEmpty() || s.equals("null") ? "—" : s;
    }

    // ---- Via le tunnel : SOCKS5 local authentifié -> serveur -> ip-api.com ----

    private static byte[] lire(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int k = in.read(b, off, n - off);
            if (k < 0) throw new IOException("flux fermé");
            off += k;
        }
        return b;
    }

    static String[] viaTunnel() throws Exception {
        int port = YiwaloVpnService.portTest;
        if (port <= 0) throw new IOException("pas de port");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write(new byte[]{5, 1, 2});
            out.flush();
            byte[] rep = lire(in, 2);
            if (rep[0] != 5 || rep[1] != 2) throw new IOException("socks");

            byte[] u = YiwaloVpnService.utilisateurTest.getBytes(StandardCharsets.UTF_8);
            byte[] m = YiwaloVpnService.motDePasseTest.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            a.write(1);
            a.write(u.length);
            a.write(u);
            a.write(m.length);
            a.write(m);
            out.write(a.toByteArray());
            out.flush();
            rep = lire(in, 2);
            if (rep[1] != 0) throw new IOException("auth");

            String hote = "ip-api.com";
            byte[] hb = hote.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream c = new ByteArrayOutputStream();
            c.write(new byte[]{5, 1, 0, 3});
            c.write(hb.length);
            c.write(hb);
            c.write(0);
            c.write(80);
            out.write(c.toByteArray());
            out.flush();
            rep = lire(in, 4);
            if (rep[1] != 0) throw new IOException("connexion");
            int type = rep[3];
            int reste = type == 1 ? 4 : type == 4 ? 16 : in.read();
            lire(in, reste + 2);

            String req = "GET /json/?lang=fr&fields=status,query,isp,country,regionName,city"
                    + " HTTP/1.0\r\nHost: " + hote + "\r\nUser-Agent: Yiwalo/1.0\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            ByteArrayOutputStream tout = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int k;
            while ((k = in.read(buf)) > 0) tout.write(buf, 0, k);
            String rawRep = new String(tout.toByteArray(), StandardCharsets.UTF_8);
            int i = rawRep.indexOf("\r\n\r\n");
            if (i < 0) throw new IOException("réponse");
            JSONObject o = new JSONObject(rawRep.substring(i + 4).trim());
            if (!"success".equals(o.optString("status"))) throw new IOException("echec");
            return new String[]{v(o.optString("query")), v(o.optString("isp")),
                    v(o.optString("country")), v(o.optString("regionName")),
                    v(o.optString("city"))};
        }
    }

    // ---- Direct (tunnel inactif) ----

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "Yiwalo/1.0");
        try {
            if (c.getResponseCode() != 200) throw new IOException("http");
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static String[] ipwho() throws Exception {
        JSONObject o = new JSONObject(get("https://ipwho.is/?lang=fr"));
        if (!o.optBoolean("success", false)) throw new IOException("echec");
        JSONObject conn = o.optJSONObject("connection");
        return new String[]{v(o.optString("ip")),
                conn != null ? v(conn.optString("isp")) : "—",
                v(o.optString("country")), v(o.optString("region")), v(o.optString("city"))};
    }

    static String[] ipapi() throws Exception {
        JSONObject o = new JSONObject(get("https://ipapi.co/json/"));
        if (o.optBoolean("error", false) || !o.has("ip")) throw new IOException("echec");
        return new String[]{v(o.optString("ip")), v(o.optString("org")),
                v(o.optString("country_name")), v(o.optString("region")), v(o.optString("city"))};
    }

    private void copier() {
        if (ip.isEmpty()) {
            T.toast(ctx, "IP non disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("IP publique", ip));
        T.toast(ctx, "IP copiée", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDetachedFromWindow() {
        attache = false;
        super.onDetachedFromWindow();
    }
}
