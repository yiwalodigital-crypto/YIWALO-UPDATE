package com.yiwalo.tunnel.vpn;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lit le trafic des appareils branchés sur le partage, via l'API de statistiques du moteur. */
public final class StatsPartage {
    public static volatile long recu = 0;
    public static volatile long envoye = 0;

    private static final Pattern MOTIF = Pattern.compile(
            "\"name\"\\s*:\\s*\"inbound>>>partage-in>>>traffic>>>(uplink|downlink)\""
            + "(?:\\s*,\\s*\"value\"\\s*:\\s*\"?(\\d+)\"?)?");

    private StatsPartage() {
    }

    public static boolean lire(Context c) {
        int port = GenerateurXray.portApi;
        if (port <= 0) {
            return false;
        }
        File xray = new File(c.getApplicationInfo().nativeLibraryDir, "libxray.so");
        if (!xray.exists()) {
            return false;
        }
        Process p = null;
        try {
            p = new ProcessBuilder(xray.getPath(), "api", "statsquery",
                    "--server=127.0.0.1:" + port, "-pattern", "partage-in")
                    .redirectErrorStream(true).start();
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            byte[] tampon = new byte[2048];
            int n;
            while ((n = in.read(tampon)) > 0) {
                sortie.write(tampon, 0, n);
            }
            if (!p.waitFor(4, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            if (p.exitValue() != 0) {
                return false;
            }
            long montant = 0;
            long descendant = 0;
            Matcher m = MOTIF.matcher(sortie.toString("UTF-8"));
            while (m.find()) {
                long v = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
                if (m.group(1).equals("uplink")) {
                    montant = v;
                } else {
                    descendant = v;
                }
            }
            envoye = montant;
            recu = descendant;
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
