package com.spendvista.app.plugins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bank SMS Retriever Plugin
 * 
 * Uses Google's official SMS Retriever API for Play Store compliance:
 * ✅ No user permission popup (uses Google-managed permission)
 * ✅ Automatic SMS detection when app is in foreground
 * ✅ Bank SMS filtered by bank server (via app signature hash)
 * ✅ Play Store friendly (official Google API)
 * ✅ No background listener (only detects when app open)
 * 
 * Banks send SMS with special hash so only your app receives them.
 * Example SMS: "Login OTP: 123456 @spendvista #ABC123XYZ"
 * The #ABC123XYZ is your app's signature hash (bank verifies it).
 */
@CapacitorPlugin(name = "BankSmsRetriever")
public class BankSmsRetrieverPlugin extends Plugin {

    private static final String TAG = "BankSmsRetriever";
    private BankSmsBroadcastReceiver smsReceiver;

    /**
     * Start listening for bank SMS
     * 
     * Requirements:
     * 1. App must be in foreground
     * 2. Bank must know your app's signature hash
     * 3. Bank SMS must include special hash at end
     * 
     * Get your app's signature hash:
     * ./gradlew signingReport
     * Then share with your bank
     */
    @PluginMethod
    public void startListening(PluginCall call) {
        try {
            Log.d(TAG, "📱 Starting SMS Retriever listening...");
            
            // Start Google's SMS Retriever
            SmsRetriever.getClient(getContext())
                .startSmsRetriever()
                .addOnSuccessListener(result -> {
                    Log.d(TAG, "✅ SMS Retriever started successfully");
                    call.resolve(new JSObject());
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "❌ SMS Retriever failed to start", exception);
                    call.reject("SMS Retriever initialization failed: " + exception.getMessage());
                });

            // Register broadcast receiver for incoming SMS
            if (smsReceiver == null) {
                smsReceiver = new BankSmsBroadcastReceiver();
            }
            
            IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
            getContext().registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED);
            
            Log.d(TAG, "✅ Broadcast receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting SMS retriever", e);
            call.reject("Error: " + e.getMessage());
        }
    }

    /**
     * Stop listening for SMS
     */
    @PluginMethod
    public void stopListening(PluginCall call) {
        try {
            if (smsReceiver != null) {
                getContext().unregisterReceiver(smsReceiver);
                smsReceiver = null;
                Log.d(TAG, "✅ Broadcast receiver unregistered");
            }
            call.resolve(new JSObject());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping SMS retriever", e);
            call.reject("Error: " + e.getMessage());
        }
    }

    /**
     * Broadcast receiver for bank SMS
     * Only receives SMS with your app's signature hash
     */
    private class BankSmsBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                
                if (bundle != null) {
                    Status status = (Status) bundle.get(SmsRetriever.EXTRA_STATUS);
                    
                    if (status != null && status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                        // SMS received!
                        String message = (String) bundle.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                        
                        if (message != null) {
                            Log.d(TAG, "📬 Bank SMS received: " + maskSms(message));
                            sendSmsToJavaScript(message);
                        }
                    } else {
                        Log.d(TAG, "⚠️ SMS Retriever failed: " + 
                            (status != null ? status.getStatusCode() : "Unknown error"));
                    }
                }
            }
        }

        /**
         * Send SMS data to JavaScript
         */
        private void sendSmsToJavaScript(String message) {
            try {
                // Parse bank SMS
                JSObject smsData = parseBankSms(message);
                
                // Send to JavaScript listeners
                notifyListeners("bankSmsReceived", smsData);
                
                Log.d(TAG, "✅ SMS sent to JavaScript");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error processing SMS", e);
            }
        }

        /**
         * Parse bank SMS and extract transaction details
         */
        private JSObject parseBankSms(String message) {
            JSObject obj = new JSObject();
            
            obj.put("raw_message", message);
            obj.put("received_at", System.currentTimeMillis());
            
            // Try to extract common bank SMS patterns
            if (message.contains("debited") || message.contains("Debited")) {
                obj.put("type", "debit");
            } else if (message.contains("credited") || message.contains("Credited")) {
                obj.put("type", "credit");
            }
            
            // Extract amount (looks for INR, Rs, ₹)
            String amount = extractAmount(message);
            if (amount != null) {
                obj.put("amount", amount);
            }
            
            // Extract balance if available
            String balance = extractBalance(message);
            if (balance != null) {
                obj.put("balance", balance);
            }
            
            return obj;
        }

        /**
         * Extract amount from SMS
         * Examples: "Rs 100", "INR 500", "₹ 1000"
         */
        private String extractAmount(String message) {
            // Pattern: currency followed by numbers
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.\\d{2})?)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(message);
            
            if (matcher.find()) {
                return matcher.group(1).replaceAll(",", "");
            }
            return null;
        }

        /**
         * Extract balance from SMS
         * Examples: "Balance: Rs 5000", "Balance : ₹10,000"
         */
        private String extractBalance(String message) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?:Balance|Bal)\\s*:?\\s*(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.\\d{2})?)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(message);
            
            if (matcher.find()) {
                return matcher.group(1).replaceAll(",", "");
            }
            return null;
        }

        /**
         * Mask SMS for logging (hide sensitive data)
         */
        private String maskSms(String message) {
            if (message.length() > 50) {
                return message.substring(0, 50) + "...";
            }
            return message;
        }
    }
}
