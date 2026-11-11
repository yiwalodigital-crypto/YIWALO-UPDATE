# Supprime les messages inutiles du Host checker et du Testeur de ports
import os, re, shutil, sys
J = "app/src/main/java/com/yiwalo/tunnel/"

def maj(nom, regles):
    p = J + nom
    if os.path.exists(p + ".avant_epure"):
        print("déjà fait :", nom)
        return
    s = open(p, encoding="utf-8").read()
    for motif, remplacement, marque in regles:
        if marque in s:
            continue
        s2, n = re.subn(motif, lambda m: remplacement, s, count=1, flags=re.S)
        if n != 1:
            sys.exit("ERREUR : bloc introuvable dans " + nom + " (" + motif[:40] + ")")
        s = s2
    if not os.path.exists(p + ".avant_epure"):
        shutil.copy(p, p + ".avant_epure")
    open(p, "w", encoding="utf-8").write(s)
    print("ok :", nom)

maj("HostCheckerView.java", [
    (r"        TextView aide = Ui\.texte\(ctx,.*?saisie\.addView\(aide\);\n", "", "@@jamais@@"),
    (r'        String diag = "Réseaux testés : ".*?DIAG_SIM\);\n', '        String diag = "";\n', 'String diag = "";'),
    (r"vDiag\.setPadding\(p, Ui\.dp\(ctx, 8\), p, p\);", "vDiag.setPadding(p, 0, p, Ui.dp(ctx, 8));", "vDiag.setPadding(p, 0, p"),
])
maj("PortsView.java", [
    (r'"Indique un hôte ou une IP : seuls les ports ouverts s\'affichent\."', '""', 'progression = Ui.texte(ctx,\n                ""'),
    (r'\n\s*\+ " · calibrage…"', "", "@@jamais@@"),
    (r'final String diag = "\\nOuverts ".*?: ""\);\n', 'final String diag = parReseau.toString();\n', "final String diag = parReseau.toString();"),
])
print("Terminé. Lance maintenant le build.")
