package com.spendvista.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import android.os.Handler;
import com.capacitorjs.plugins.pushnotifications.PushNotificationsPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private final Handler handler = new Handler();
    private boolean detectionComplete = false;
    public static boolean isRedirectingToSettings = false; // 🚦 Traffic controller flag

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(BankSmsRetrieverPlugin.class);
        Log.d(TAG, "✅ [NATIVE] Registered: BankSmsRetrieverPlugin");

        registerPlugin(PushNotificationsPlugin.class);
        Log.d(TAG, "✅ [NATIVE] Registered: PushNotificationsPlugin");

        registerPlugin(NotificationListenerPlugin.class);
        Log.d(TAG, "✅ [NATIVE] Registered: NotificationListenerPlugin");

        registerPlugin(NotificationAccessPlugin.class);
        Log.d(TAG, "✅ [NATIVE] Registered: NotificationAccessPlugin");

        registerPlugin(SmsSyncPlugin.class);
        Log.d(TAG, "✅ [NATIVE] Registered: SmsSyncPlugin");

        super.onCreate(savedInstanceState);

        // 🚀 Start URL Poller for login detection
        handler.post(urlPoller);
    }

    private final Runnable urlPoller = new Runnable() {
        @Override
        public void run() {
            try {
                WebView webView = getBridge().getWebView();
                if (webView != null) {
                    // 🚥 ALWAYS inject the guard on every poll to protect newly loaded pages (Skip
                    // flow)
                    injectPermissionGuard(webView);

                    // 🎯 ONLY run the login sequence once
                    if (!detectionComplete) {
                        String url = webView.getUrl();
                        if (url != null && url.contains("logged_in=1")) {
                            Log.d(TAG, "🎯 [NATIVE-POLL] Login Success detected!");
                            executePostLoginSequence();
                            detectionComplete = true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ WebView/Bridge not ready, skipping poll.");
            }

            handler.postDelayed(this, 300); // 🚀 Persistent poll (300ms) for constant protection
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(urlPoller);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(urlPoller);
    }

    private void executePostLoginSequence() {
        Log.d(TAG, "⚙️ [NATIVE] Executing post-login sequence...");

        runOnUiThread(() -> Toast.makeText(this, "Spend Vista: Login Success!", Toast.LENGTH_SHORT).show());

        // 🚀 Native POST_NOTIFICATIONS trigger (Android 13+)
        // We wait 5 seconds to ensure the dashboard has fully loaded and is stable.
        handler.postDelayed(() -> {
            Log.d(TAG, "🔔 [NATIVE] Triggering persistent notification permission request...");
            requestNotificationPermission();
        }, 5000);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ [NATIVE] Notification permission granted by user.");
            } else {
                Log.d(TAG, "❌ [NATIVE] Notification permission denied by user.");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reset the redirection flag when the app returns to the foreground
        if (isRedirectingToSettings) {
            Log.d(TAG, "🚦 [NATIVE] App resumed. Tapping the breaks on redirection flag...");
            isRedirectingToSettings = false;
        }

        // If detection wasn't completed (e.g. they minimized during login), re-check on
        // resume
        if (!detectionComplete) {
            try {
                WebView webView = getBridge().getWebView();
                if (webView != null && webView.getUrl() != null && webView.getUrl().contains("logged_in=1")) {
                    executePostLoginSequence();
                    detectionComplete = true;
                }
            } catch (Exception e) {
                // Silently skip if webView not ready
            }
        }

    }

    private void requestNotificationPermission() {
        // Only needed for Android 13 (Tiramisu) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🔔 [NATIVE] Requesting POST_NOTIFICATIONS permission...");
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        NOTIFICATION_PERMISSION_CODE);
            } else {
                Log.d(TAG, "✅ [NATIVE] Notification permission already granted.");
            }
        } else {
            Log.d(TAG, "ℹ️ [NATIVE] Below Android 13, permission is granted by default.");
        }
    }

    /**
     * Injects a Javascript "Guard" to monkey-patch Capacitor's PushNotifications
     * plugin.
     * This will automatically delay permission popups if they occur while the app
     * is
     * navigating to settings.
     */
    private void injectPermissionGuard(WebView webView) {
        String js = "(function() {" +
                "  if (window.__svPermissionGuard) return;" +
                "  window.__svPermissionGuard = true;" +
                "  var checkAndDelay = async (origFunc, args) => {" +
                "    console.log('🚥 [JS-GUARD] Checking native redirection status...');" +
                "    try {" +
                "      if (!window.Capacitor || !window.Capacitor.Plugins || !window.Capacitor.Plugins.NotificationListener) throw 'missing';"
                +
                "      const { isRedirecting } = await window.Capacitor.Plugins.NotificationListener.isRedirectStatus();"
                +
                "      if (isRedirecting) {" +
                "        console.warn('🚦 [JS-GUARD] Redirection detected! Delaying permission popup...');" +
                "        await new Promise(r => setTimeout(r, 4000));" +
                "      }" +
                "    } catch (e) {" +
                "      console.warn('🚥 [JS-GUARD] Fail-safe triggered (redirect check skipped):', e);" +
                "    }" +
                "    return origFunc.apply(window.Capacitor.Plugins.PushNotifications, args);" +
                "  };" +
                "  var interval = setInterval(() => {" +
                "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.PushNotifications) {"
                +
                "      clearInterval(interval);" +
                "      const orig = window.Capacitor.Plugins.PushNotifications.requestPermissions;" +
                "      if (orig && orig.__svPatched) return;" +
                "      window.Capacitor.Plugins.PushNotifications.requestPermissions = function() {" +
                "        return checkAndDelay(orig, arguments);" +
                "      };" +
                "      window.Capacitor.Plugins.PushNotifications.requestPermissions.__svPatched = true;" +
                "      console.log('✅ [JS-GUARD] Bridge patched. Push requests will now wait for redirection.');" +
                "    }" +
                "  }, 50);" + // ⏱️ Faster check (50ms)
                "})();";

        runOnUiThread(() -> {
            Log.d(TAG, "💉 [NATIVE] Injecting JS Permission Guard into WebView...");
            webView.evaluateJavascript(js, null);
        });
    }
}
