package com.yiwalo.tunnel.ip;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import com.yiwalo.tunnel.MainActivity;
import com.yiwalo.tunnel.R;
public class RechercheIpService extends Service {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        com.yiwalo.tunnel.T.init(base);
        super.attachBaseContext(com.yiwalo.tunnel.T.contexte(base));
    }
    public static final String ACTION_STOP = "com.yiwalo.tunnel.action.STOP_RECHERCHE";
    private static final String CANAL_SUIVI = "yiwalo_recherche";
    private static final String CANAL_ALERTE = "yiwalo_ip_trouvee";
    private static final int ID_SUIVI = 31;
    private static final int ID_ALERTE = 32;
    private final Handler h = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock verrou;
    private Ringtone sonnerie;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ChercheurIpLocal.arreter();
            finir();
            return START_NOT_STICKY;
        }
        if (ChercheurIpLocal.enCours()) return START_NOT_STICKY;
        creerCanaux();
        try {
            startForeground(ID_SUIVI, suivi("Recherche en cours…"));
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        prendreVerrou();
        int max = intent == null ? 50 : intent.getIntExtra("max", 50);
        boolean ok = ChercheurIpLocal.demarrer(this, max, new ChercheurIpLocal.Ecoute() {
            @Override public void etat(String m) {
                getSystemService(NotificationManager.class).notify(ID_SUIVI, suivi(m));
            }
            @Override public void fini(boolean trouve, String ip) {
                if (trouve) {
                    alerter(ip);
                } else {
                    finir();
                }
            }
        });
        if (!ok) finir();
        return START_NOT_STICKY;
    }

    private void creerCanaux() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CANAL_SUIVI, "Recherche d'IP", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel a = new NotificationChannel(
                CANAL_ALERTE, "IP trouvée", NotificationManager.IMPORTANCE_HIGH);
        a.enableVibration(true);
        a.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        a.setSound(null, null);
        nm.createNotificationChannel(a);
    }

    private PendingIntent ouvrir() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private Notification suivi(String texte) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RechercheIpService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CANAL_SUIVI)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle("Recherche d'IP en cours")
                .setContentText(texte)
                .setContentIntent(ouvrir())
                .setOngoing(true)
                .addAction(R.drawable.ic_power, "Arrêter", stop)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void alerter(String ip) {
        Notification n = new Notification.Builder(this, CANAL_ALERTE)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle("IP trouvée !")
                .setContentText(ip + " est dans ta liste. Mode avion désactivé.")
                .setContentIntent(ouvrir())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build();
        getSystemService(NotificationManager.class).notify(ID_ALERTE, n);
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AlerteIp.sonner(this);
            sonnerie = null;
            if (sonnerie != null) {
                sonnerie.setStreamType(AudioManager.STREAM_ALARM);
                sonnerie.play();
            }
        } catch (RuntimeException ignore) {
            // pas de sonnerie disponible
        }
        h.postDelayed(this::finir, 10000);
    }

    private void prendreVerrou() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            verrou = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yiwalo:recherche");
            verrou.acquire(2 * 60 * 60 * 1000L);
        } catch (RuntimeException ignore) {
            // la recherche continue sans verrou
        }
    }

    private void libererVerrou() {
        try {
            if (verrou != null && verrou.isHeld()) verrou.release();
        } catch (RuntimeException ignore) {
            // deja libere
        }
        verrou = null;
    }

    @SuppressWarnings("deprecation")
    private void finir() {
        h.removeCallbacksAndMessages(null);
        if (sonnerie != null) {
            try {
                sonnerie.stop();
            } catch (RuntimeException ignore) {
                // deja arretee
            }
            sonnerie = null;
        }
        libererVerrou();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        libererVerrou();
        if (ChercheurIpLocal.enCours()) ChercheurIpLocal.arreter();
        super.onDestroy();
    }
}
