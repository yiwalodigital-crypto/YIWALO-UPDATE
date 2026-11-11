package com.yiwalo.tunnel.config;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EditeurLien {

    private static final Set<String> GERES = new HashSet<>(Arrays.asList(
            "type", "security", "sni", "host", "authority", "path", "serviceName",
            "alpn", "fp", "flow", "pbk", "sid", "spx"));

    public static final class Champs {
        public String nom = "";
        public String hote = "";
        public String port = "";
        public String identifiant = "";
        public String reseau = "tcp";
        public String securite = "none";
        public String sni = "";
        public String hostEntete = "";
        public String chemin = "";
        public String alpn = "";
        public String empreinte = "";
        public String flow = "";
        public String pbk = "";
        public String sid = "";
        public String spx = "";
        final Map<String, String> autres = new LinkedHashMap<>();
    }

    private EditeurLien() {
    }

    public static boolean modifiable(TypeConnexion type) {
        return type == TypeConnexion.VLESS || type == TypeConnexion.TROJAN
                || type == TypeConnexion.VMESS;
    }

    public static Champs lire(Configuration c) throws ErreurImport {
        if (!modifiable(c.type)) {
            throw new ErreurImport("La modification de ce type de configuration "
                    + "n'est pas encore disponible.");
        }
        if (c.type == TypeConnexion.VMESS) {
            return lireVmess(c);
        }
        String lien = c.lienOriginal;
        int debut = lien.indexOf("://");
        if (debut < 0) {
            throw new ErreurImport("Configuration illisible.");
        }
        String reste = lien.substring(debut + 3);
        int diese = reste.indexOf('#');
        if (diese >= 0) {
            reste = reste.substring(0, diese);
        }
        String requete = "";
        int interro = reste.indexOf('?');
        if (interro >= 0) {
            requete = reste.substring(interro + 1);
            reste = reste.substring(0, interro);
        }
        int arobase = reste.lastIndexOf('@');
        if (arobase < 0) {
            throw new ErreurImport("Configuration illisible.");
        }

        Champs ch = new Champs();
        ch.nom = c.nom;
        ch.hote = c.serveur;
        ch.port = String.valueOf(c.port);
        ch.identifiant = decoder(reste.substring(0, arobase));

        Map<String, String> p = new LinkedHashMap<>();
        for (String paire : requete.split("&")) {
            if (paire.isEmpty()) {
                continue;
            }
            int egal = paire.indexOf('=');
            if (egal < 0) {
                p.put(decoder(paire), "");
            } else {
                p.put(decoder(paire.substring(0, egal)), decoder(paire.substring(egal + 1)));
            }
        }
        ch.reseau = valeur(p, "type", "tcp");
        ch.securite = valeur(p, "security", "none");
        ch.sni = valeur(p, "sni", "");
        boolean grpc = ch.reseau.equalsIgnoreCase("grpc");
        String authority = valeur(p, "authority", "");
        ch.hostEntete = (grpc && !authority.isEmpty()) ? authority : valeur(p, "host", "");
        ch.chemin = grpc ? valeur(p, "serviceName", "") : valeur(p, "path", "");
        ch.alpn = valeur(p, "alpn", "");
        ch.empreinte = valeur(p, "fp", "");
        ch.flow = valeur(p, "flow", "");
        ch.pbk = valeur(p, "pbk", "");
        ch.sid = valeur(p, "sid", "");
        ch.spx = valeur(p, "spx", "");
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (!GERES.contains(e.getKey()) && !e.getValue().isEmpty()) {
                ch.autres.put(e.getKey(), e.getValue());
            }
        }
        return ch;
    }

    public static String construire(TypeConnexion type, Champs ch) throws ErreurImport {
        if (type == TypeConnexion.VMESS) {
            return construireVmess(ch);
        }
        String hote = ch.hote.trim();
        if (hote.isEmpty()) {
            throw new ErreurImport("L'adresse du serveur est vide.");
        }
        String id = ch.identifiant.trim();
        if (id.isEmpty()) {
            throw new ErreurImport("L'identifiant (UUID ou mot de passe) est vide.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(type == TypeConnexion.VLESS ? "vless://" : "trojan://");
        sb.append(encoder(id)).append('@');
        sb.append(hote.contains(":") ? "[" + hote + "]" : hote);
        sb.append(':').append(ch.port.trim());

        Map<String, String> p = new LinkedHashMap<>(ch.autres);
        String reseau = ch.reseau.trim().toLowerCase(Locale.ROOT);
        ajouter(p, "type", reseau);
        ajouter(p, "security", ch.securite.trim().toLowerCase(Locale.ROOT));
        ajouter(p, "sni", ch.sni);
        if (reseau.equals("grpc")) {
            ajouter(p, "authority", ch.hostEntete);
            ajouter(p, "serviceName", ch.chemin);
        } else {
            ajouter(p, "host", ch.hostEntete);
            ajouter(p, "path", ch.chemin);
        }
        ajouter(p, "alpn", ch.alpn);
        ajouter(p, "fp", ch.empreinte);
        ajouter(p, "flow", ch.flow);
        ajouter(p, "pbk", ch.pbk);
        ajouter(p, "sid", ch.sid);
        ajouter(p, "spx", ch.spx);

        boolean premier = true;
        for (Map.Entry<String, String> e : p.entrySet()) {
            sb.append(premier ? '?' : '&');
            premier = false;
            sb.append(encoder(e.getKey())).append('=').append(encoder(e.getValue()));
        }
        String nom = ch.nom.trim();
        if (!nom.isEmpty()) {
            sb.append('#').append(encoder(nom));
        }
        return sb.toString();
    }

    private static final Set<String> VMESS_GERES = new HashSet<>(Arrays.asList(
            "ps", "add", "port", "id", "net", "tls", "sni", "host", "path", "alpn", "fp", "v"));

    private static Champs lireVmess(Configuration c) throws ErreurImport {
        String contenu = c.lienOriginal.substring("vmess://".length()).trim();
        contenu = contenu.replace('-', '+').replace('_', '/');
        while (contenu.length() % 4 != 0) contenu += "=";
        org.json.JSONObject j;
        try {
            byte[] octets = android.util.Base64.decode(contenu, android.util.Base64.DEFAULT);
            j = new org.json.JSONObject(new String(octets, "UTF-8"));
        } catch (Exception e) {
            throw new ErreurImport("Le lien VMess est mal formé.");
        }
        Champs ch = new Champs();
        ch.nom = j.optString("ps", "");
        ch.hote = j.optString("add", "");
        ch.port = j.optString("port", "");
        ch.identifiant = j.optString("id", "");
        ch.reseau = j.optString("net", "tcp");
        if (ch.reseau.isEmpty()) ch.reseau = "tcp";
        ch.securite = j.optString("tls", "").equalsIgnoreCase("tls") ? "tls" : "none";
        ch.sni = j.optString("sni", "");
        ch.hostEntete = j.optString("host", "");
        ch.chemin = j.optString("path", "");
        ch.alpn = j.optString("alpn", "");
        ch.empreinte = j.optString("fp", "");
        java.util.Iterator<String> it = j.keys();
        while (it.hasNext()) {
            String cle = it.next();
            if (!VMESS_GERES.contains(cle)) {
                ch.autres.put(cle, j.optString(cle, ""));
            }
        }
        return ch;
    }

    private static String construireVmess(Champs ch) throws ErreurImport {
        String hote = ch.hote.trim();
        if (hote.isEmpty()) {
            throw new ErreurImport("L'adresse du serveur est vide.");
        }
        String id = ch.identifiant.trim();
        if (id.isEmpty()) {
            throw new ErreurImport("L'identifiant (UUID) est vide.");
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("v", "2");
            o.put("ps", ch.nom.trim());
            o.put("add", hote);
            o.put("port", ch.port.trim());
            o.put("id", id);
            o.put("net", ch.reseau.trim().toLowerCase(Locale.ROOT));
            o.put("tls", ch.securite.trim().equalsIgnoreCase("tls") ? "tls" : "");
            o.put("host", ch.hostEntete.trim());
            o.put("path", ch.chemin.trim());
            o.put("sni", ch.sni.trim());
            o.put("alpn", ch.alpn.trim());
            o.put("fp", ch.empreinte.trim());
            for (Map.Entry<String, String> e : ch.autres.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
            if (!o.has("aid")) o.put("aid", "0");
            if (!o.has("scy")) o.put("scy", "auto");
            if (!o.has("type")) o.put("type", "none");
            byte[] octets = o.toString().getBytes("UTF-8");
            return "vmess://" + android.util.Base64.encodeToString(
                    octets, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            throw new ErreurImport("Impossible de reconstruire le lien VMess.");
        }
    }

    private static void ajouter(Map<String, String> p, String cle, String valeur) {
        String v = valeur == null ? "" : valeur.trim();
        if (!v.isEmpty()) {
            p.put(cle, v);
        }
    }

    private static String valeur(Map<String, String> p, String cle, String defaut) {
        String v = p.get(cle);
        return v == null ? defaut : v;
    }

    private static String encoder(String texte) {
        try {
            return URLEncoder.encode(texte, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return texte;
        }
    }

    private static String decoder(String texte) {
        try {
            return URLDecoder.decode(texte.replace("+", "%2B"), "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return texte;
        }
    }
}
