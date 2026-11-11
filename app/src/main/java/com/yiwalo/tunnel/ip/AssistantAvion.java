package com.yiwalo.tunnel.ip;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
public class AssistantAvion extends VoiceInteractionService {
    static volatile AssistantAvion instance;
    @Override public void onReady() { super.onReady(); instance = this; }
    @Override public void onShutdown() { instance = null; super.onShutdown(); }
    public static boolean actif(Context c) {
        return isActiveService(c, new ComponentName(c, AssistantAvion.class));
    }
    public static boolean basculer(boolean activer) {
        AssistantAvion a = instance;
        if (a == null) return false;
        Bundle b = new Bundle();
        b.putBoolean("activer", activer);
        a.showSession(b, 0);
        return true;
    }
}
