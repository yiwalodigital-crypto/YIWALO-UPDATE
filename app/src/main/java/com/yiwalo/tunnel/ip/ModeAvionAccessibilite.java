package com.yiwalo.tunnel.ip;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
public class ModeAvionAccessibilite extends AccessibilityService {
    static volatile ModeAvionAccessibilite instance;
    private volatile boolean attente;
    private final Handler h = new Handler(Looper.getMainLooper());
    @Override protected void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public boolean onUnbind(Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onInterrupt() { }
    public static boolean pret() { return instance != null; }
    public static boolean basculer(Context c) {
        ModeAvionAccessibilite s = instance;
        if (s == null) return false;
        s.attente = true;
        s.startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        s.h.postDelayed(() -> s.attente = false, 6000);
        return true;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!attente) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo cible = chercher(root);
        if (cible == null) return;
        AccessibilityNodeInfo n = cible;
        while (n != null && !n.isClickable()) n = n.getParent();
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            attente = false;
            h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 700);
        }
    }
    private AccessibilityNodeInfo chercher(AccessibilityNodeInfo n) {
        if (n == null) return null;
        CharSequence cn = n.getClassName();
        if (n.isCheckable() || (cn != null && cn.toString().contains("Switch"))) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = chercher(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }
}
