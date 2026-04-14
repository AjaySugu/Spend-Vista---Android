package com.spendvista.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NotificationListener")
public class NotificationListenerPlugin extends Plugin {

    /**
     * Check if the Notification Listener permission is granted.
     * Returns { enabled: boolean }
     */
    @PluginMethod
    public void isPermissionGranted(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", isNotificationServiceEnabled(getContext()));
        call.resolve(ret);
    }

    /**
     * Open Android's Notification Access settings screen so the user can grant
     * permission manually (required — there is no programmatic grant).
     */
    @PluginMethod
    public void requestPermission(PluginCall call) {
        try {
            // 🚥 Set native flag to delay Push Notification popups
            MainActivity.isRedirectingToSettings = true;
            
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY); // Ensure it doesn't linger in backstack
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            MainActivity.isRedirectingToSettings = false; // Reset on failure
            call.reject("Failed to open settings: " + e.getMessage());
        }
    }

    /**
     * Programmatically toggle the service state to force Android to re-bind it.
     * This is a common fix for NotificationListenerService not triggering.
     */
    @PluginMethod
    public void rebindService(PluginCall call) {
        try {
            PackageManager pm = getContext().getPackageManager();
            ComponentName componentName = new ComponentName(getContext(), BankNotificationService.class);
            
            // Disable and then re-enable the component
            Log.d("[SPENDVISTA][Plugin]", "Rebinding Notification Listener service...");
            pm.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            
            pm.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Log.d("[SPENDVISTA][Plugin]", "Notification Listener service rebind successful.");
            
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to rebind service", e);
        }
    }

    /**
     * Check if the app is currently in the middle of a redirection to settings.
     * Used by the JS bridge guard to sequence permission popups.
     */
    @PluginMethod
    public void isRedirectStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("isRedirecting", MainActivity.isRedirectingToSettings);
        call.resolve(ret);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        String flat = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (!TextUtils.isEmpty(flat)) {
            for (String name : flat.split(":")) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && pkgName.equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
