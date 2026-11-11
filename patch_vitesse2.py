#!/usr/bin/env python3
# Patch YIWALO : moins de coupures + plus de vitesse.
# Usage (dans ~/YIWALO-TUNNEL) :  python3 patch_vitesse2.py
import os, sys, shutil

BASE = "app/src/main/java/com/yiwalo/tunnel/vpn/"
SVC = BASE + "YiwaloVpnService.java"
GEN = BASE + "GenerateurXray.java"

# (fichier, ancien, nouveau, nombre d'occurrences attendues)
CHANGEMENTS = [
    # 1. Sonde "lente" : 3000 ms -> 8000 ms (un lien saturé par un téléchargement dépasse 3 s)
    (SVC, "if (l > 3000) {", "if (l > 8000) {", 1),
    # 2. Il faut 6 sondes dégradées (au lieu de 3) avant de relancer le moteur
    (SVC, "if (sondesDegradees >= 3) {", "if (sondesDegradees >= 6) {", 2),
    # 3. Sonde toutes les 15 s au lieu de 5 s (moins de connexions parasites dans le tunnel)
    (SVC, "prochain = maintenant + 5000;", "prochain = maintenant + 15000;", 1),
    # 4. Sniffing : garde l'IP de destination (le serveur ne refait pas de DNS pour chaque site)
    (GEN,
     '.put("sniffing", new JSONObject().put("enabled", true)\n                .put("destOverride"',
     '.put("sniffing", new JSONObject().put("enabled", true).put("routeOnly", true)\n                .put("destOverride"', 2),
    # 5. Tampon par connexion 64 Ko -> 512 Ko (64 Ko bride le débit sur 4G)
    (GEN, '.put("bufferSize", 64)', '.put("bufferSize", 512)', 2),
    # 6. Mux désactivé par défaut (un seul flux TCP partagé = ralentissements en chaîne sur mobile)
    (GEN, "public static volatile boolean muxActif = true;",
          "public static volatile boolean muxActif = false;", 1),
]

def main():
    for f in (SVC, GEN):
        if not os.path.exists(f):
            sys.exit("Fichier introuvable : %s\nLance ce script depuis ~/YIWALO-TUNNEL." % f)
    contenus = {f: open(f, encoding="utf-8").read() for f in (SVC, GEN)}
    for f, ancien, nouveau, n in CHANGEMENTS:
        trouve = contenus[f].count(ancien)
        if trouve == 0 and contenus[f].count(nouveau) >= 1:
            print("déjà appliqué :", nouveau[:50])
            continue
        if trouve != n:
            sys.exit("ERREUR : %d occurrence(s) de %r trouvée(s), %d attendue(s). "
                     "Rien n'a été modifié." % (trouve, ancien[:60], n))
    for f in (SVC, GEN):
        if not os.path.exists(f + ".bak_vitesse2"):
            shutil.copy(f, f + ".bak_vitesse2")
    nb = 0
    for f, ancien, nouveau, n in CHANGEMENTS:
        if contenus[f].count(ancien) == n:
            contenus[f] = contenus[f].replace(ancien, nouveau)
            nb += 1
    if nb == 0:
        sys.exit("Tout était déjà appliqué, rien à faire.")
    for f in (SVC, GEN):
        open(f, "w", encoding="utf-8").write(contenus[f])
    print("OK : %d changement(s) appliqué(s) (sauvegardes .bak_vitesse2)." % nb)
    print("Recompile maintenant, puis teste.")

main()
