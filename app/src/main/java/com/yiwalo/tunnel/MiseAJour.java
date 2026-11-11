package com.yiwalo.tunnel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import com.yiwalo.tunnel.vpn.YiwaloVpnService;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Vérifie en ligne s'il existe une version plus récente, affiche la fenêtre
 * « Mise à jour disponible », télécharge l'APK puis lance l'installation.
 *
 * L'application est exclue du VPN (pour ne pas boucler) : quand le VPN est connecté,
 * les requêtes passent donc par l'entrée SOCKS locale du moteur, pour utiliser le tunnel
 * (utile quand le réseau mobile n'a pas de forfait hors tunnel).
 *
 * Le fichier JSON hébergé en ligne (URL_JSON) doit ressembler à :
 * {
 *   "versionCode": 19,
 *   "versionName": "2.9.0",
 *   "apkUrl": "https://exemple.com/YIWALO-Tunnel.apk",
 *   "sha256": "(optionnel) empreinte SHA-256 de l'APK",
 *   "changelog": "- Résolution des reconnexions brutales\n- Amélioration de la fluidité VPN",
 *   "force": false
 * }
 */
final class MiseAJour {
    static final String URL_JSON =
            "https://raw.githubusercontent.com/yiwalodigital-crypto/YIWALO-UPDATE/main/update.json";

    private static final String PREFS = "yiwalo_maj";
    private static final String CLE_CACHE = "derniere_info";
    private static final long DELAI_ENTRE_VERIFS_MS = 10L * 60 * 1000; // 10 min

    private static volatile boolean enCours = false;
    private static volatile boolean aRelancer = false;
    private static volatile long dernierSucces = 0;
    private static AlertDialog fenetre;
    private static Activity fenetreActivite;

    private MiseAJour() { }

    // ------------------------------------------------------------------
    // Vérification
    // ------------------------------------------------------------------

    /**
     * À appeler à chaque ouverture de l'appli (et quand le VPN se connecte).
     * 1) Affiche tout de suite la mise à jour déjà connue (mémorisée), même sans réseau.
     * 2) Rafraîchit l'information en ligne quand c'est possible.
     */
    static void verifier(final Activity act) {
        try {
            JSONObject connu = lireCache(act);
            if (connu != null && connu.getLong("versionCode") > versionLocale(act)) {
                afficher(act, connu);
            }
        } catch (Exception ignore) {
            // cache absent ou illisible : on attend la vérification en ligne
        }
        actualiser(act);
    }

    static void actualiser(final Activity act) {
        if (enCours) {
            // le VPN vient de se connecter pendant un essai en réseau direct : on refera l'essai
            if (viaTunnel()) aRelancer = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (dernierSucces > 0 && now - dernierSucces < DELAI_ENTRE_VERIFS_MS) return;
        enCours = true;
        new Thread(() -> {
            try {
                String txt = lireTexte(URL_JSON);
                final JSONObject j = new JSONObject(txt);
                long distant = j.getLong("versionCode");
                long local = versionLocale(act);
                dernierSucces = System.currentTimeMillis();
                if (distant > local) {
                    act.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(CLE_CACHE, txt).apply();
                    act.runOnUiThread(() -> {
                        if (!act.isFinishing()) afficher(act, j);
                    });
                } else {
                    act.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .remove(CLE_CACHE).apply();
                }
            } catch (Exception ignore) {
                // pas de réseau : on réessaiera à la prochaine ouverture ou connexion du VPN
            } finally {
                enCours = false;
                if (aRelancer) {
                    aRelancer = false;
                    actualiser(act); // ignoré si la vérification précédente a réussi
                }
            }
        }).start();
    }

    private static JSONObject lireCache(Context ctx) {
        try {
            String t = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(CLE_CACHE, null);
            return t == null ? null : new JSONObject(t);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static long versionLocale(Context ctx) throws Exception {
        android.content.pm.PackageInfo pi =
                ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }

    private static void afficher(final Activity act, final JSONObject j) {
        if (act.isFinishing() || act.isDestroyed()) return;
        if (fenetre != null && fenetreActivite == act && fenetre.isShowing()) return;

        final String nom = j.optString("versionName", "?");
        final String notes = j.optString("changelog", "").trim();
        final boolean force = j.optBoolean("force", false);

        StringBuilder m = new StringBuilder();
        m.append("Une nouvelle version (v").append(nom).append(") est disponible.");
        if (!notes.isEmpty()) m.append("\n\nChangements :\n\uD83D\uDD25 Nouveautés :\n").append(notes);
        m.append("\n\nVoulez-vous la télécharger et l'installer maintenant ?");

        AlertDialog.Builder b = new AlertDialog.Builder(act,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(T.t("Mise à jour disponible"))
                .setMessage(T.t(m.toString()))
                .setCancelable(!force)
                .setPositiveButton(T.t("METTRE À JOUR"), (d, w) -> telecharger(act, j));
        if (!force) {
            b.setNegativeButton(T.t("PLUS TARD"), null);
        }
        fenetre = b.show();
        fenetreActivite = act;
    }

    // ------------------------------------------------------------------
    // Téléchargement et installation
    // ------------------------------------------------------------------

    private static void telecharger(final Activity act, final JSONObject j) {
        // Android demande une autorisation « installer des applis inconnues » une seule fois.
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !act.getPackageManager().canRequestPackageInstalls()) {
            T.toast(act, "Autorise l'installation depuis YIWALO TUNNEL, puis reviens ici",
                    Toast.LENGTH_LONG).show();
            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName())));
            return;
        }

        final String url = j.optString("apkUrl", "");
        final String sha = j.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
        if (!url.startsWith("https://")) {
            T.toast(act, "Lien de mise à jour invalide", Toast.LENGTH_LONG).show();
            return;
        }

        final boolean[] annule = {false};
        final AlertDialog dlg = new AlertDialog.Builder(act,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(T.t("Téléchargement…"))
                .setMessage(T.t("Connexion au serveur…"))
                .setCancelable(false)
                .setNegativeButton(T.t("ANNULER"), (d, w) -> annule[0] = true)
                .create();
        dlg.show();

        new Thread(() -> {
            String erreur = null;
            try {
                File dossier = new File(act.getCacheDir(), "partage");
                if (!dossier.exists()) dossier.mkdirs();
                // même emplacement que le partage d'APK : déjà exposé par ApkProvider
                File out = new File(dossier, PartageApk.NOM);
                out.delete();

                long deja = 0;
                long total = -1;
                int sansProgres = 0;
                boolean fini = false;
                IOException derniere = null;
                // Reprise automatique : si le tunnel coupe, on repart de l'octet déjà reçu.
                while (!annule[0]) {
                    long avant = deja;
                    try {
                        Reponse r = ouvrir(url, 0, deja);
                        if (r.debut != deja) deja = 0; // le serveur a ignoré la reprise
                        if (r.longueur > 0) total = r.longueur;
                        try (InputStream in = r.corps;
                             OutputStream os = new FileOutputStream(out, deja > 0)) {
                            byte[] buf = new byte[65536];
                            int n, dernier = -1;
                            while (!annule[0] && (n = in.read(buf)) > 0) {
                                os.write(buf, 0, n);
                                deja += n;
                                if (total > 0) {
                                    final int pc = (int) (deja * 100 / total);
                                    if (pc != dernier) {
                                        dernier = pc;
                                        final String m = pc + " %  (" + (deja / 1048576L) + " / "
                                                + (total / 1048576L) + " Mo)";
                                        act.runOnUiThread(() -> dlg.setMessage(T.t(m)));
                                    }
                                }
                            }
                        } finally {
                            r.fermer();
                        }
                        if (annule[0]) break;
                        if (total <= 0 || deja >= total) {
                            fini = true;
                            break;
                        }
                        derniere = new IOException("flux interrompu à " + deja + "/" + total);
                    } catch (IOException e) {
                        derniere = e;
                    }
                    sansProgres = deja > avant ? 0 : sansProgres + 1;
                    if (sansProgres >= 6) throw derniere;
                    act.runOnUiThread(() -> dlg.setMessage(T.t("Connexion instable, reprise…")));
                    Thread.sleep(2000);
                }
                if (annule[0]) {
                    out.delete();
                    erreur = "";
                } else if (!fini || out.length() < 100 * 1024) {
                    out.delete();
                    erreur = "Fichier reçu invalide";
                } else if (!sha.isEmpty()) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = new java.io.FileInputStream(out)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                    }
                    StringBuilder hex = new StringBuilder();
                    for (byte x : md.digest()) hex.append(String.format("%02x", x));
                    if (!hex.toString().equals(sha)) {
                        out.delete();
                        erreur = "Fichier corrompu, réessaie plus tard";
                    }
                }
            } catch (java.net.UnknownHostException | java.net.ConnectException
                     | java.net.SocketTimeoutException e) {
                erreur = "Pas d'accès à Internet : connecte le VPN puis réessaie";
            } catch (Exception e) {
                String d = e.getMessage() == null ? "" : ": " + e.getMessage();
                if (d.length() > 80) d = d.substring(0, 80);
                erreur = "Échec du téléchargement (" + e.getClass().getSimpleName() + d + ")";
            }
            final String err = erreur;
            act.runOnUiThread(() -> {
                dlg.dismiss();
                if (err == null) {
                    installer(act);
                } else if (!err.isEmpty()) {
                    T.toast(act, err, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static void installer(Activity act) {
        try {
            Uri u = Uri.parse("content://" + PartageApk.AUTORITE + "/" + PartageApk.NOM);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (RuntimeException e) {
            T.toast(act, "Impossible de lancer l'installation", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------
    // Petit client HTTPS : réseau direct, ou tunnel via l'entrée SOCKS du moteur
    // ------------------------------------------------------------------

    private static final class Reponse {
        InputStream corps;
        long longueur = -1; // taille totale du fichier
        long debut = 0;     // position réellement servie (0 si la reprise est ignorée)
        Closeable ferme;

        void fermer() {
            try {
                if (ferme != null) ferme.close();
            } catch (IOException ignore) {
                // déjà fermé
            }
        }
    }

    private static boolean viaTunnel() {
        return SuiviVpn.etat() == EtatVpn.CONNECTE && YiwaloVpnService.portTest > 0;
    }

    private static String lireTexte(String url) throws IOException {
        Reponse r = ouvrir(url, 0, 0);
        try (InputStream in = r.corps) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) {
                bo.write(b, 0, n);
                if (bo.size() > 256 * 1024) throw new IOException("réponse trop grande");
            }
            return bo.toString("UTF-8");
        } finally {
            r.fermer();
        }
    }

    private static Reponse ouvrir(String adresse, int redirections, long debut) throws IOException {
        if (redirections > 5) throw new IOException("trop de redirections");
        final URL u = new URL(adresse);
        if (!"https".equals(u.getProtocol())) throw new IOException("lien non HTTPS");
        if (!viaTunnel()) {
            final HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            if (debut > 0) c.setRequestProperty("Range", "bytes=" + debut + "-");
            int code = c.getResponseCode();
            if (code != 200 && code != 206) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            Reponse r = new Reponse();
            r.corps = c.getInputStream();
            long cl = c.getContentLengthLong();
            r.debut = code == 206 ? debut : 0;
            r.longueur = cl > 0 ? r.debut + cl : -1;
            r.ferme = c::disconnect;
            return r;
        }

        // --- via le tunnel : SOCKS5 local (avec identifiants) puis TLS ---
        final String hote = u.getHost();
        final int port = u.getPort() > 0 ? u.getPort() : 443;
        final Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", YiwaloVpnService.portTest), 3000);
            s.setSoTimeout(30000);
            socks5(s, hote, port);
            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(s, hote, port, true);
            ssl.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(hote, ssl.getSession())) {
                throw new IOException("certificat invalide pour " + hote);
            }
            String chemin = u.getFile().isEmpty() ? "/" : u.getFile();
            String req = "GET " + chemin + " HTTP/1.1\r\nHost: " + hote
                    + "\r\nUser-Agent: YiwaloTunnel\r\nAccept-Encoding: identity\r\n"
                    + (debut > 0 ? "Range: bytes=" + debut + "-\r\n" : "")
                    + "Connection: close\r\n\r\n";
            OutputStream os = ssl.getOutputStream();
            os.write(req.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            InputStream in = new BufferedInputStream(ssl.getInputStream(), 65536);
            String statut = ligne(in);
            String[] p = statut.split(" ", 3);
            if (p.length < 2) throw new IOException("réponse invalide");
            int code = Integer.parseInt(p[1]);
            Map<String, String> h = new HashMap<>();
            String l;
            while (!(l = ligne(in)).isEmpty()) {
                int i = l.indexOf(':');
                if (i > 0) h.put(l.substring(0, i).trim().toLowerCase(Locale.ROOT),
                        l.substring(i + 1).trim());
            }
            if (code >= 300 && code < 400 && h.containsKey("location")) {
                try {
                    s.close();
                } catch (IOException ignore) {
                    // fermeture avant redirection
                }
                return ouvrir(new URL(u, h.get("location")).toString(), redirections + 1, debut);
            }
            if (code != 200 && code != 206) throw new IOException("HTTP " + code);
            Reponse r = new Reponse();
            r.ferme = s;
            r.debut = code == 206 ? debut : 0;
            if ("chunked".equalsIgnoreCase(h.get("transfer-encoding"))) {
                r.corps = new Morceaux(in);
            } else {
                r.corps = in;
                String cl = h.get("content-length");
                if (cl != null) {
                    try {
                        r.longueur = r.debut + Long.parseLong(cl);
                    } catch (NumberFormatException ignore) {
                        r.longueur = -1;
                    }
                }
            }
            return r;
        } catch (IOException | RuntimeException e) {
            try {
                s.close();
            } catch (IOException ignore) {
                // déjà fermé
            }
            throw e;
        }
    }

    /** Négociation SOCKS5 (identifiant + mot de passe) puis demande de connexion à hote:port. */
    private static void socks5(Socket s, String hote, int port) throws IOException {
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        out.write(new byte[]{5, 1, 2});
        out.flush();
        byte[] rep = lireN(in, 2);
        if (rep[0] != 5 || rep[1] != 2) throw new IOException("SOCKS refusé");

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
        rep = lireN(in, 2);
        if (rep[1] != 0) throw new IOException("SOCKS : identifiants refusés");

        byte[] h = hote.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream c = new ByteArrayOutputStream();
        c.write(new byte[]{5, 1, 0, 3});
        c.write(h.length);
        c.write(h);
        c.write((port >> 8) & 0xff);
        c.write(port & 0xff);
        out.write(c.toByteArray());
        out.flush();
        byte[] e = lireN(in, 4);
        if (e[1] != 0) throw new IOException("tunnel : connexion refusée (code " + e[1] + ")");
        int reste;
        if (e[3] == 1) reste = 4 + 2;
        else if (e[3] == 4) reste = 16 + 2;
        else reste = (lireN(in, 1)[0] & 0xff) + 2;
        lireN(in, reste);
    }

    private static byte[] lireN(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("connexion fermée");
            off += r;
        }
        return b;
    }

    /** Lit une ligne terminée par CRLF (sans le CRLF). */
    private static String ligne(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("en-tête trop long");
        }
        if (c < 0 && sb.length() == 0) throw new IOException("connexion fermée");
        return sb.toString();
    }

    /** Décode un corps HTTP en « Transfer-Encoding: chunked ». */
    private static final class Morceaux extends InputStream {
        private final InputStream in;
        private long reste = 0;
        private boolean fin = false;

        Morceaux(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n < 0 ? -1 : (b[0] & 0xff);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (fin) return -1;
            if (reste == 0) {
                String l = ligne(in);
                if (l.isEmpty()) l = ligne(in); // CRLF après le morceau précédent
                int pv = l.indexOf(';');
                if (pv >= 0) l = l.substring(0, pv);
                try {
                    reste = Long.parseLong(l.trim(), 16);
                } catch (NumberFormatException e) {
                    throw new IOException("morceau invalide");
                }
                if (reste == 0) {
                    fin = true;
                    return -1;
                }
            }
            int n = in.read(b, off, (int) Math.min(len, reste));
            if (n < 0) throw new IOException("connexion fermée");
            reste -= n;
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
