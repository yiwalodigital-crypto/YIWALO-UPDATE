package com.yiwalo.tunnel.ip;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.voice.VoiceInteractionSession;
public class SessionAvion extends VoiceInteractionSession {
    SessionAvion(Context c) { super(c); }
    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        setUiEnabled(false);
        boolean on = args != null && args.getBoolean("activer");
        Intent i = new Intent(Settings.ACTION_VOICE_CONTROL_AIRPLANE_MODE)
                .addCategory(Intent.CATEGORY_VOICE)
                .putExtra(Settings.EXTRA_AIRPLANE_MODE_ENABLED, on);
        startVoiceActivity(i);
    }
    @Override public void onRequestConfirmation(ConfirmationRequest r) {
        r.sendConfirmationResult(true, null);
        hide();
    }
    @Override public void onRequestCompleteVoice(CompleteVoiceRequest r) {
        hide();
    }
}
