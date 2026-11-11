package com.yiwalo.tunnel.vpn;

import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.config.TypeConnexion;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Génère la configuration sing-box (Hysteria2 / TUIC) : entrée SOCKS locale, sortie vers le serveur. */
public final class GenerateurSingBox {
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private GenerateurSingBox() {
    }

    private static String decoder(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }

    /**
     * Lit un lien hysteria2:// ou tuic:// : "info" = identifiants complets, "u" et "p" = avant et
     * après le premier « : » (TUIC : uuid et mot de passe), puis les paramètres de la requête.
     */
    public static Map<String, String> parser(String lien) {
        Map<String, String> m = new HashMap<>();
        if (lien == null) {
            return m;
        }
        int i = lien.indexOf("://");
        if (i < 0) {
            return m;
        }
        String reste = lien.substring(i + 3);
        int diese = reste.indexOf('#');
        if (diese >= 0) {
            reste = reste.substring(0, diese);
        }
        int q = reste.indexOf('?');
        if (q >= 0) {
            for (String kv : reste.substring(q + 1).split("&")) {
                int egal = kv.indexOf('=');
                if (egal > 0) {
                    m.put(kv.substring(0, egal), decoder(kv.substring(egal + 1)));
                }
            }
            reste = reste.substring(0, q);
        }
        int arobase = reste.lastIndexOf('@');
        if (arobase >= 0) {
            String info = reste.substring(0, arobase);
            m.put("info", decoder(info));
            int deux = info.indexOf(':');
            if (deux >= 0) {
                m.put("u", decoder(info.substring(0, deux)));
                m.put("p", decoder(info.substring(deux + 1)));
            } else {
                m.put("u", decoder(info));
            }
        }
        return m;
    }

    /** ip = adresse déjà résolue du serveur ; portSocks = port local de l'entrée SOCKS. */
    public static String generer(Configuration c, String ip, int portSocks) {
        Map<String, String> p = parser(c.lienOriginal);
        String sni = c.sniManuel == null ? "" : c.sniManuel.trim();
        if (sni.isEmpty()) {
            String s = p.get("sni");
            if (s != null) {
                sni = s.trim();
            }
        }
        if (sni.isEmpty() && !IPV4.matcher(c.serveur).matches()) {
            sni = c.serveur;
        }
        try {
            JSONObject tls = new JSONObject().put("enabled", true)
                    .put("alpn", new JSONArray().put("h3"));
            if (!sni.isEmpty()) {
                tls.put("server_name", sni);
            }
            if (c.certNonVerifie) {
                tls.put("insecure", true);
            }
            JSONObject sortie = new JSONObject()
                    .put("tag", "out")
                    .put("server", ip)
                    .put("server_port", c.port)
                    .put("tls", tls);
            if (c.type == TypeConnexion.HYSTERIA2) {
                String auth = p.get("info");
                if (auth == null || auth.isEmpty()) {
                    throw new IllegalArgumentException("Le mot de passe Hysteria2 est manquant.");
                }
                sortie.put("type", "hysteria2").put("password", auth);
                String obfs = p.get("obfs-password");
                if (obfs != null && !obfs.isEmpty()) {
                    sortie.put("obfs", new JSONObject()
                            .put("type", "salamander").put("password", obfs));
                }
            } else if (c.type == TypeConnexion.TUIC) {
                String uuid = p.get("u");
                String mdp = p.get("p");
                if (uuid == null || uuid.isEmpty() || mdp == null || mdp.isEmpty()) {
                    throw new IllegalArgumentException("L'UUID ou le mot de passe TUIC est manquant.");
                }
                sortie.put("type", "tuic").put("uuid", uuid).put("password", mdp)
                        .put("congestion_control", "bbr").put("udp_relay_mode", "native");
            } else {
                throw new IllegalArgumentException("Ce type n'est pas géré par sing-box.");
            }
            JSONObject entree = new JSONObject()
                    .put("type", "socks")
                    .put("tag", "in")
                    .put("listen", "127.0.0.1")
                    .put("listen_port", portSocks);
            JSONObject racine = new JSONObject()
                    .put("log", new JSONObject().put("level", "warn"))
                    .put("inbounds", new JSONArray().put(entree))
                    .put("outbounds", new JSONArray().put(sortie));
            return racine.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Configuration sing-box impossible : " + e.getMessage());
        }
    }
}
