import os, sys, shutil
B = "app/src/main/"
J = B + "java/com/yiwalo/tunnel/"

def edit(path, old, new):
    s = open(path, encoding="utf-8").read()
    if new in s:
        print("déjà fait :", os.path.basename(path)); return
    if s.count(old) != 1:
        sys.exit("ERREUR : bloc introuvable dans " + path)
    shutil.copy(path, path + ".avant_stabilite")
    open(path, "w", encoding="utf-8").write(s.replace(old, new))
    print("ok :", os.path.basename(path))

V = J + "vpn/YiwaloVpnService.java"

# 1. Un changement de réseau ne coupe plus le tunnel s'il répond encore
edit(V, """                if (reseauChange) {
                    reseauChange = false;
                    reconnexionRapide = true;
""", """                if (reseauChange) {
                    reseauChange = false;
                    Journal.info("Changement de réseau détecté : vérification du tunnel.");
                    Thread.sleep(2500);
                    if (tester(r) >= 0) {
                        Journal.info("Tunnel toujours actif : connexion conservée.");
                        echecs = 0;
                        prochain = SystemClock.elapsedRealtime() + 10000;
                        continue;
                    }
                    reconnexionRapide = true;
""")

# 2. Plus tolérant aux micro-coupures mobiles
edit(V, "SEUIL_ECHECS = 3;", "SEUIL_ECHECS = 4;")

# 3. La reconnexion automatique n'abandonne plus après 3 essais
edit(V, "final int[] delais = {3, 8, 20};", "final int[] delais = {3, 8, 20, 30, 45, 60, 60, 60};")

# 4. Délai TCP moins agressif sur réseau mobile
edit(J + "vpn/GenerateurXray.java", '.put("tcpUserTimeout", 30000)', '.put("tcpUserTimeout", 60000)')

# 5. Autorisation batterie (évite que le téléphone tue le VPN en veille)
edit(B + "AndroidManifest.xml",
     '<uses-permission android:name="android.permission.WAKE_LOCK" />',
     '<uses-permission android:name="android.permission.WAKE_LOCK" />\n'
     '    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />')

M = J + "MainActivity.java"
edit(M, """            SuiviVpn.changer(EtatVpn.ERREUR, "Impossible de démarrer le service VPN.");
        }
    }
""", """            SuiviVpn.changer(EtatVpn.ERREUR, "Impossible de démarrer le service VPN.");
        }
        demanderOptimisationBatterie();
    }

    private void demanderOptimisationBatterie() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            android.content.SharedPreferences sp =
                    getSharedPreferences("yiwalo_reglages", MODE_PRIVATE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())
                    && !sp.getBoolean("batterie_demandee", false)) {
                sp.edit().putBoolean("batterie_demandee", true).apply();
                startActivity(new Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
            }
        } catch (RuntimeException ignore) {
            // réglage indisponible sur ce téléphone
        }
    }
""")
print("Terminé. Lance maintenant le build.")
