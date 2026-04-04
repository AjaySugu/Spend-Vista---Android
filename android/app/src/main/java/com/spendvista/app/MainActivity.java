package com.spendvista.app;

import android.Manifest;
import android.content.Intent;
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
import com.getcapacitor.PluginHandle;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private final Handler handler = new Handler();
    private boolean detectionComplete = false;

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
            if (detectionComplete || isFinishing() || isDestroyed()) return;

            try {
                WebView webView = getBridge().getWebView();
                if (webView != null) {
                    String url = webView.getUrl();
                    if (url != null && url.contains("logged_in=1")) {
                        Log.d(TAG, "🎯 [NATIVE-POLL] Login Success detected!");
                        executePostLoginSequence();
                        detectionComplete = true; 
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ WebView not ready yet, skipping poll.");
            }
            
            handler.postDelayed(this, 1500); // 1.5 seconds to reduce UI load
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
        
        runOnUiThread(() -> 
            Toast.makeText(this, "Spend Vista: Login Success!", Toast.LENGTH_SHORT).show()
        );

        // 1. Request Notification Permission
        requestNotificationPermission();


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
        // If detection wasn't completed (e.g. they minimized during login), re-check on resume
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🔔 [NATIVE] Requesting POST_NOTIFICATIONS permission...");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            } else {
                Log.d(TAG, "✅ [NATIVE] Notification permission already granted.");
            }
        } else {
            Log.d(TAG, "ℹ️ [NATIVE] Below Android 13, permission is granted by default.");
        }
    }
}
