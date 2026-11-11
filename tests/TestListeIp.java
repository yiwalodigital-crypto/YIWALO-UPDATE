import com.yiwalo.tunnel.ip.ListeIp;

import java.util.Arrays;

public class TestListeIp {
    static int ok = 0;
    static int ko = 0;

    static void verifier(String nom, boolean condition) {
        if (condition) {
            ok++;
            System.out.println("OK   " + nom);
        } else {
            ko++;
            System.out.println("ECHEC " + nom);
        }
    }

    public static void main(String[] args) {
        verifier("IP exacte valide", "10.197.158.178".equals(ListeIp.normaliser("10.197.158.178")));
        verifier("espaces autour ignorés", "10.1.2.3".equals(ListeIp.normaliser("  10.1.2.3  ")));
        verifier("zéros initiaux normalisés", "10.1.2.3".equals(ListeIp.normaliser("010.001.002.003")));
        verifier("joker à droite", "10.197.*.*".equals(ListeIp.normaliser("10.197.*.*")));
        verifier("joker dernier octet", "10.197.158.*".equals(ListeIp.normaliser("10.197.158.*")));
        verifier("joker au milieu refusé", ListeIp.normaliser("10.*.158.1") == null);
        verifier("octet > 255 refusé", ListeIp.normaliser("10.197.158.256") == null);
        verifier("trois octets refusés", ListeIp.normaliser("10.197.158") == null);
        verifier("cinq octets refusés", ListeIp.normaliser("10.197.158.1.1") == null);
        verifier("lettres refusées", ListeIp.normaliser("10.a.158.1") == null);
        verifier("vide refusé", ListeIp.normaliser("") == null);
        verifier("null refusé", ListeIp.normaliser(null) == null);
        verifier("octet vide refusé", ListeIp.normaliser("10..158.1") == null);
        verifier("signe moins refusé", ListeIp.normaliser("10.-1.158.1") == null);

        verifier("correspondance exacte", ListeIp.correspond("10.197.158.178", "10.197.158.178"));
        verifier("pas de correspondance", !ListeIp.correspond("10.197.158.179", "10.197.158.178"));
        verifier("correspondance par joker", ListeIp.correspond("10.197.158.5", "10.197.158.*"));
        verifier("correspondance joker large", ListeIp.correspond("10.197.9.9", "10.197.*.*"));
        verifier("joker ne correspond pas à un autre réseau", !ListeIp.correspond("10.198.9.9", "10.197.*.*"));
        verifier("adresse avec joker n'est pas une IP", !ListeIp.correspond("10.197.*.*", "10.197.*.*"));
        verifier("IP invalide ne correspond pas", !ListeIp.correspond("abc", "10.197.158.178"));

        java.util.List<String> liste = Arrays.asList("10.205.44.1", "10.197.158.*");
        verifier("première correspondance (exacte)", "10.205.44.1".equals(ListeIp.premiereCorrespondance("10.205.44.1", liste)));
        verifier("première correspondance (joker)", "10.197.158.*".equals(ListeIp.premiereCorrespondance("10.197.158.200", liste)));
        verifier("aucune correspondance", ListeIp.premiereCorrespondance("10.205.44.122", liste) == null);

        ListeIp.Extraction e = ListeIp.extraire("10.1.1.1, 10.2.2.2\n10.1.1.1 abc 10.3.3.*;999.1.1.1");
        verifier("extraction : 3 valides sans doublon", e.valides.size() == 3);
        verifier("extraction : 2 invalides", e.invalides.size() == 2);
        verifier("extraction : texte vide", ListeIp.extraire("   ").valides.isEmpty());

        System.out.println("\n" + ok + " réussis, " + ko + " échoués");
        if (ko > 0) {
            System.exit(1);
        }
    }
}
