package com.yiwalo.tunnel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class SelecteurFichier extends Activity {
    public interface Retour { void surUri(Uri uri); }

    private static Retour retour;

    public static void creer(Context c, String nom, Retour r) {
        retour = r;
        Intent i = new Intent(c, SelecteurFichier.class);
        i.putExtra("mode", "creer");
        i.putExtra("nom", nom);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static void ouvrir(Context c, Retour r) {
        retour = r;
        Intent i = new Intent(c, SelecteurFichier.class);
        i.putExtra("mode", "ouvrir");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) return;
        Intent i;
        if ("creer".equals(getIntent().getStringExtra("mode"))) {
            i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, getIntent().getStringExtra("nom"));
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
        }
        startActivityForResult(i, 1);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Retour r = retour;
        retour = null;
        if (res == RESULT_OK && data != null && data.getData() != null && r != null) {
            r.surUri(data.getData());
        }
        finish();
    }
}
