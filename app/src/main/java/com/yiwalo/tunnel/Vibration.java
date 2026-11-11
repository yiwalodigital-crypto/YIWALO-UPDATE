package com.yiwalo.tunnel;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Une seule vibration courte (120 ms, pleine puissance) à la connexion. */
public final class Vibration {
    private static long derniere = -100000L;

    private Vibration() { }

    public static synchronized void fort(Context ctx) {
        // jamais deux vibrations rapprochées (écran + service, ou changements d'état répétés)
        long now = SystemClock.elapsedRealtime();
        if (now - derniere < 4000L) return;
        derniere = now;
        try {
            Vibrator v;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)
                        ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            // usage « alarme » : Android l'autorise même quand l'appli est en arrière-plan
            AudioAttributes attributs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(120, 255), attributs);
            } else {
                v.vibrate(120, attributs);
            }
        } catch (Exception ignore) {
            // pas de vibreur ou refus du système : sans conséquence
        }
    }
}
