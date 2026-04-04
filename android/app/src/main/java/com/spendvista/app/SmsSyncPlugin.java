package com.spendvista.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PermissionState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@CapacitorPlugin(name = "SmsSync")
public class SmsSyncPlugin extends Plugin {
    private static final String TAG = "SmsSyncPlugin";

    // Regex matches any of the keywords case-insensitively
    private static final String KEYWORD_REGEX = "(?i).*\\b(debited|credited|withdrawn|spent|inr|rs)\\b.*";

    @PluginMethod
    public void saveToken(PluginCall call) {
        String token = call.getString("token");
        Log.d(TAG, "📡 [NATIVE-BRIDGE] Received saveToken request. Token length: " + (token != null ? token.length() : "NULL"));
        if (token != null) {
            SharedPreferences prefs = getContext().getSharedPreferences("SmsSyncPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("auth_token", token).apply();
            Log.d(TAG, "Auth token saved successfully");
            call.resolve();
        } else {
            call.reject("Token is null");
        }
    }

    @PluginMethod
    public void importSms(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "smsPermsCallback");
            return;
        }
        performSmsImport(call);
    }

    @PermissionCallback
    private void smsPermsCallback(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) {
            performSmsImport(call);
        } else {
            call.reject("READ_SMS permission denied");
        }
    }

    private void performSmsImport(PluginCall call) {
        JSArray results = new JSArray();
        Uri inboxUri = Uri.parse("content://sms/inbox");
        String sortOrder = "date DESC LIMIT 200";

        try (Cursor cursor = getContext().getContentResolver().query(inboxUri, null, null, null, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int bodyIndex = cursor.getColumnIndexOrThrow("body");
                    int addressIndex = cursor.getColumnIndexOrThrow("address");
                    int dateIndex = cursor.getColumnIndexOrThrow("date");

                    String body = cursor.getString(bodyIndex);
                    String sender = cursor.getString(addressIndex);
                    long dateMillis = cursor.getLong(dateIndex);

                    if (body != null && body.matches(KEYWORD_REGEX)) {
                        JSObject smsObj = new JSObject();
                        smsObj.put("sender", sender);
                        smsObj.put("body", body);
                        
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        smsObj.put("date", sdf.format(new Date(dateMillis)));
                        results.put(smsObj);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SMS", e);
            call.reject("Failed to read SMS: " + e.getMessage());
            return;
        }

        JSObject response = new JSObject();
        response.put("messages", results);
        call.resolve(response);
    }
}
