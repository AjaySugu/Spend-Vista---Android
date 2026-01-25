package com.spendvista.app;

import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DeveloperMode")
public class DeveloperModePlugin extends Plugin {

    @PluginMethod
    public void check(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            int devOptions = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            int adbOptions = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.ADB_ENABLED, 0);

            android.util.Log.d("DeveloperModePlugin", "DevOptions: " + devOptions + ", ADB: " + adbOptions);

            boolean isEnabled = (devOptions == 1) || (adbOptions == 1);
            ret.put("enabled", isEnabled);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to check settings", e);
        }
    }
}
