package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class PartageApk {
    static final String AUTORITE = "com.yiwalo.tunnel.apk";
    static final String NOM = "YIWALO-Tunnel.apk";

    private PartageApk() { }

    static void partager(final Context ctx) {
        T.toast(ctx, "Préparation du fichier…", Toast.LENGTH_SHORT).show();
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            try {
                File dossier = new File(ctx.getCacheDir(), "partage");
                if (!dossier.exists()) dossier.mkdirs();
                File out = new File(dossier, NOM);
                try (InputStream in = new FileInputStream(ctx.getApplicationInfo().sourceDir);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                }
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean fin = ok;
            h.post(() -> {
                if (!fin) {
                    T.toast(ctx, "Impossible de préparer l’application",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    Uri u = Uri.parse("content://" + AUTORITE + "/" + NOM);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/vnd.android.package-archive");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.putExtra(Intent.EXTRA_SUBJECT, "YIWALO TUNNEL");
                    i.setClipData(ClipData.newRawUri(NOM, u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent ch = Intent.createChooser(i, "Partager l’application");
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(ch);
                } catch (RuntimeException e) {
                    T.toast(ctx, "Partage indisponible", Toast.LENGTH_SHORT).show();
                }
            });
        }, "yiwalo-partage").start();
    }
}
