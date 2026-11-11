package com.yiwalo.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwalo.tunnel.config.ConfigStore;
import com.yiwalo.tunnel.config.Configuration;
import com.yiwalo.tunnel.config.EditeurLien;
import com.yiwalo.tunnel.config.ErreurImport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

public class JournauxView extends LinearLayout {

    private final TextView texte;
    private final ScrollView defilement;
    private final TextView pied;
    private int derniereTaille = -1;
    private boolean suivre = true;
    private Journal.Ligne derniereLigne;
    private final long heureOuverture = System.currentTimeMillis();
    private static final Pattern DATE_PAT = Pattern.compile(
            "\\d{4}[/-]\\d{2}[/-]\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\s*");
    private static final Pattern HEURE_PAT = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\s*");
    private static final Pattern MS_PAT = Pattern.compile("\\((\\d+) ms\\)");

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            rafraichir(false);
            postDelayed(this, 1000);
        }
    };

    public JournauxView(Context contexte) {
        super(contexte);
        setOrientation(VERTICAL);

        TextView titre = new TextView(contexte);
        titre.setText(R.string.journal_titre);
        titre.setTextSize(20f);
        titre.setTextColor(contexte.getColor(R.color.texte));
        titre.setTypeface(titre.getTypeface(), Typeface.BOLD);
        titre.setPadding(dp(16), dp(8), dp(16), 0);
        addView(titre, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(contexte);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(8), 0, dp(8), dp(4));
        actions.addView(action(contexte, R.string.journal_actualiser,
                v -> rafraichir(true)), poids());
        actions.addView(action(contexte, R.string.journal_copier,
                v -> copier(T.t(Journal.texte()), R.string.journal_copies)), poids());
        actions.addView(action(contexte, R.string.journal_rapport,
                v -> copier(T.t(rapport()), R.string.journal_rapport_copie)), poids());
        actions.addView(action(contexte, R.string.journal_effacer, v -> {
            Journal.effacer();
            rafraichir(true);
        }), poids());
        addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        defilement = new ScrollView(contexte);
        texte = new TextView(contexte);
        texte.setTypeface(Typeface.MONOSPACE);
        texte.setTextSize(11f);
        texte.setTextColor(contexte.getColor(R.color.texte));
        texte.setTextIsSelectable(true);
        texte.setPadding(dp(16), dp(8), dp(16), dp(16));
        defilement.addView(texte, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(defilement, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pied = new TextView(contexte);
        pied.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pied.setTextSize(13f);
        pied.setPadding(dp(16), dp(10), dp(16), dp(10));
        pied.setVisibility(View.GONE);
        texte.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (suivre) {
                defilement.post(() -> {
                    View c = defilement.getChildAt(0);
                    if (c != null) {
                        defilement.scrollTo(0, Math.max(0, c.getHeight() - defilement.getHeight()));
                    }
                });
            }
        });
        defilement.setOnTouchListener((v, ev) -> {
            int act = ev.getAction();
            if (act == android.view.MotionEvent.ACTION_UP
                    || act == android.view.MotionEvent.ACTION_CANCEL) {
                View c = defilement.getChildAt(0);
                if (c != null) {
                    suivre = c.getHeight() - defilement.getHeight() - defilement.getScrollY() < dp(80);
                }
            }
            return false;
        });
        addView(pied, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rafraichir(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(tic, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tic);
        super.onDetachedFromWindow();
    }

    private LinearLayout.LayoutParams poids() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView action(Context contexte, int libelle, View.OnClickListener ecouteur) {
        TextView t = new TextView(contexte);
        t.setText(libelle);
        t.setTextSize(13f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(contexte.getColor(R.color.bleu_clair));
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        t.setOnClickListener(ecouteur);
        return t;
    }

    private static boolean estEchecTunnel(String m) {
        String b = m.toLowerCase(Locale.ROOT);
        if (b.contains("ping http 200")) return false;
        String[] mots = {"tunnel", "ping", "latence", "sonde", "réponse", "reponse",
                "test", "délai", "timeout", "lent", "injoignable"};
        for (String mot : mots) {
            if (b.contains(mot)) return true;
        }
        return false;
    }

    private void rafraichir(boolean force) {
        List<Journal.Ligne> source = Journal.lignes();
        Journal.Ligne fin = source.isEmpty() ? null : source.get(source.size() - 1);
        if (!force && source.size() == derniereTaille && fin == derniereLigne) {
            return;
        }
        derniereTaille = source.size();
        derniereLigne = fin;

        List<Journal.Ligne> lignes = new ArrayList<>(source);
        Collections.sort(lignes, (a, b) -> Long.compare(a.heure, b.heure));

        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        enteteAppareil(sb, format);

        for (Journal.Ligne l : lignes.subList(Math.max(0, lignes.size() - 250), lignes.size())) {
            String message = nettoyer(String.valueOf(l.message));
            boolean timeout = l.niveau != Journal.Niveau.INFO && estEchecTunnel(message);
            if (timeout) message = "Ping timeout";
            final String original = message;
            message = T.t(message);
            String heure = "[" + format.format(new Date(l.heure)) + "]";
            int debut = sb.length();
            sb.append(heure).append(" ").append(message).append("\n");
            int finLigne = sb.length();

            int couleur = Ui.TEXTE;
            if (timeout || l.niveau == Journal.Niveau.ERREUR) {
                couleur = Ui.ROUGE;
            } else if (l.niveau == Journal.Niveau.AVERTISSEMENT) {
                couleur = Ui.ORANGE;
            }
            sb.setSpan(new ForegroundColorSpan(couleur), debut, finLigne,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), debut, debut + heure.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (original.equals("Connecté")) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), debut, finLigne,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (l.niveau == Journal.Niveau.INFO) {
                java.util.regex.Matcher mm = MS_PAT.matcher(message);
                if (mm.find()) {
                    int ms = Integer.parseInt(mm.group(1));
                    int col = ms < 200 ? Ui.VERT : ms < 700 ? Ui.ORANGE : Ui.ROUGE;
                    int base = debut + heure.length() + 1;
                    sb.setSpan(new ForegroundColorSpan(col),
                            base + mm.start(), base + mm.end(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        final int yAvant = defilement.getScrollY();
        texte.setText(sb);
        final Runnable aligner = () -> {
            View c = defilement.getChildAt(0);
            if (c == null) return;
            int bas = Math.max(0, c.getHeight() - defilement.getHeight());
            defilement.scrollTo(0, suivre ? bas : Math.min(yAvant, bas));
        };
        defilement.post(aligner);
        defilement.postDelayed(aligner, 60);
        defilement.postDelayed(aligner, 250);
    }




    private static String nettoyer(String m) {
        m = DATE_PAT.matcher(m).replaceAll("");
        m = HEURE_PAT.matcher(m.trim()).replaceFirst("");
        return m.trim();
    }

    @SuppressWarnings("deprecation")
    private void enteteAppareil(SpannableStringBuilder sb, SimpleDateFormat format) {
        Context c = getContext();
        String version = "";
        long build = 0;
        try {
            android.content.pm.PackageInfo pi =
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = pi.versionName;
            build = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception ignore) { }
        String marque = Build.BRAND == null ? "" : Build.BRAND;
        if (!marque.isEmpty()) {
            marque = Character.toUpperCase(marque.charAt(0)) + marque.substring(1);
        }
        List<Journal.Ligne> ls = Journal.lignes();
        long h = ls.isEmpty() ? heureOuverture : ls.get(0).heure;
        String heure = "[" + format.format(new Date(h)) + "]";
        int d = sb.length();
        sb.append(heure).append(T.t(" Exécution sur ")).append(marque).append(" ")
                .append(Build.MODEL).append(" (").append(Build.DEVICE).append("), Android ")
                .append(Build.VERSION.RELEASE).append(" (").append(Build.ID).append(") API ")
                .append(String.valueOf(Build.VERSION.SDK_INT)).append(". Version ")
                .append(version).append(" Build ").append(String.valueOf(build)).append(".\n");
        sb.setSpan(new ForegroundColorSpan(Ui.TEXTE), d, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), d, d + heure.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String ipLocale() {
        try {
            for (java.net.NetworkInterface ni :
                    Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && !ni.getName().startsWith("tun") && a instanceof java.net.Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) { }
        return "inconnue";
    }

    @SuppressWarnings("deprecation")
    private String rapport() {
        Context contexte = getContext();
        StringBuilder sb = new StringBuilder();
        sb.append("YIWALO TUNNEL - rapport de diagnostic\n");
        try {
            String version = contexte.getPackageManager()
                    .getPackageInfo(contexte.getPackageName(), 0).versionName;
            sb.append("Application : ").append(version).append("\n");
        } catch (Exception ignore) {
            // version indisponible : le rapport reste utile
        }
        sb.append("Android : ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("État : ").append(SuiviVpn.etat().name()).append("\n");
        Configuration active = new ConfigStore(contexte).active();
        if (active == null) {
            sb.append("Configuration : aucune\n");
        } else {
            sb.append("Protocole : ").append(active.type.libelle).append("\n");
            try {
                EditeurLien.Champs ch = EditeurLien.lire(active);
                sb.append("Transport : ").append(ch.reseau).append("\n");
                sb.append("Sécurité : ").append(ch.securite).append("\n");
            } catch (ErreurImport e) {
                sb.append("Transport et sécurité : non détaillés pour ce protocole\n");
            }
        }
        String message = SuiviVpn.message();
        if (!message.isEmpty()) {
            sb.append("Dernier message : ").append(Journal.masquer(message)).append("\n");
        }
        sb.append("\nJournal :\n").append(Journal.texte());
        return sb.toString();
    }
    private void majPied() {
        long ms = Journal.latenceMs();
        boolean connecte = "CONNECTE".equals(SuiviVpn.etat().name());
        if (!connecte || ms < 0) {
            pied.setVisibility(View.GONE);
            return;
        }
        long age = System.currentTimeMillis() - Journal.latenceHeure();
        if (age > 25000) {
            pied.setTextColor(Ui.ROUGE);
            pied.setText("Latence : aucune réponse");
        } else {
            pied.setTextColor(ms < 300 ? Ui.VERT : ms < 700 ? Ui.ORANGE : Ui.ROUGE);
            pied.setText("Latence : " + ms + " ms");
        }
        pied.setVisibility(View.VISIBLE);
    }


    private void copier(String contenu, int confirmation) {
        Context contexte = getContext();
        ClipboardManager cm =
                (ClipboardManager) contexte.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("YIWALO TUNNEL", contenu));
            T.toast(contexte, confirmation, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int valeur) {
        return Math.round(valeur * getResources().getDisplayMetrics().density);
    }
}
