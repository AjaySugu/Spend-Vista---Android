package com.spendvista.app;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BankNotificationService
 *
 * Listens to incoming notifications from known Indian bank / payment apps,
 * extracts transaction details with regex, and POSTs them to the SpendVista
 * Laravel backend.
 *
 * Play Store compliance notes
 * ───────────────────────────
 * • NotificationListenerService is permitted for finance / expense-tracking
 *   apps under the "Prominent Disclosure" requirement — you MUST show a clear
 *   in-app disclosure screen before directing users to grant access.
 * • Data is sent only to YOUR OWN backend (stageapp.spendvista.com).
 * • No SMS permission is requested; notifications are used instead.
 */
public class BankNotificationService extends NotificationListenerService {

    private static final String TAG = "[SPENDVISTA][Native]";

    // -------------------------------------------------------------------------
    // Known Indian bank / payment app package names
    // -------------------------------------------------------------------------
    private static final Set<String> BANK_PACKAGES = new HashSet<>(Arrays.asList(
            // UPI / Wallets
            "com.google.android.apps.nbu.paisa.user",   // Google Pay
            "net.one97.paytm",                           // Paytm
            "com.phonepe.app",                           // PhonePe
            "in.amazon.mShop.android.shopping",          // Amazon Pay
            "com.whatsapp",                              // WhatsApp Pay (shared pkg)
            "com.mobikwik_new",                          // MobiKwik
            "com.freecharge.android",                    // Freecharge
            // Public sector banks
            "com.sbi.lotusintouch",                      // SBI YONO
            "com.sbi.SBIFreedomPlus",
            "com.pnb.mbanking",                          // PNB
            "com.bankofbaroda.mpassbook",                // Bank of Baroda
            "in.co.bankofbaroda.mconnect",
            "com.canarabank.mobility",                   // Canara Bank
            "com.infrasofttech.centralbank",             // Central Bank
            "com.unisongt.unionbank",                    // Union Bank
            "com.IndianBank_MobileBanking",              // Indian Bank
            "com.syntelinfotech.iob",                    // Indian Overseas Bank
            "com.idbi.mpassbook",                        // IDBI
            // Private sector banks
            "com.hdfc.mobilebanking",                    // HDFC
            "com.icici.mobile",                          // ICICI iMobile
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",                           // Axis Mobile
            "com.kotak.mobile.android",                  // Kotak
            "com.yesbank",                               // Yes Bank
            "com.indusind.mobilebanking",                // IndusInd
            "com.rbl.rblmobilebanking",                  // RBL
            "com.idfcfirstbank.mobileapp",               // IDFC First
            "com.federalbank.FedMobile",                 // Federal Bank
            "com.southindianbank.SIBMirror",             // South Indian Bank
            "com.kvbankltd.kvbmobile",                   // Karur Vysya
            "com.dcbbank.mobilebanking",                 // DCB Bank
            // Small finance / Neo banks
            "in.fampay.app",                             // FamPay
            "com.jupiter.money",                         // Jupiter
            "com.niyo.global",                           // Niyo
            "in.fambre.sfbl.digital",                   // Fincare
            "com.unionbankofindia.vyom",                 // Union Bank (Vyom)
            // Common SMS Apps (for bank alerts via SMS)
            "com.google.android.apps.messaging",         // Google Messages
            "com.android.mms",                           // Default Samsung/Android SMS
            "com.truecaller",                            // Truecaller SMS
            "com.microsoft.launcher",                    // Microsoft Launcher (SMS)
            "com.android.shell"                          // Allow testing via ADB
    ));

    // -------------------------------------------------------------------------
    // Regex patterns for common Indian bank SMS / notification formats
    // -------------------------------------------------------------------------

    // Debit patterns  e.g. "debited by Rs.1,200.00" / "spent Rs.500" / "paid INR 2000"
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
            "(?:debited[\\s_]*(?:by|for)?|debit(?:ed)?[\\s_]*(?:of)?|spent|paid|sent)" +
            "[\\s_]*(?:Rs\\.?|INR|₹|Rs:|INR:|₹:)[\\s_]*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    // Credit patterns  e.g. "credited for Rs:500" / "received INR 2000"
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "(?:credited[\\s_]*(?:with|by|for)?|credit(?:ed)?[\\s_]*(?:of)?|received|added|deposited)" +
            "[\\s_]*(?:Rs\\.?|INR|₹|Rs:|INR:|₹:)[\\s_]*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    // Amount-first patterns  e.g. "Rs.1200 debited" / "₹500 spent"
    private static final Pattern AMOUNT_FIRST_DEBIT = Pattern.compile(
            "(?:Rs\\.?|INR|₹|Rs:|INR:|₹:)[\\s_]*([\\d,]+(?:\\.\\d{1,2})?)[\\s_]+(?:has been[\\s_]+)?(?:debited|spent|paid|sent)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AMOUNT_FIRST_CREDIT = Pattern.compile(
            "(?:Rs\\.?|INR|₹|Rs:|INR:|₹:)[\\s_]*([\\d,]+(?:\\.\\d{1,2})?)[\\s_]+(?:has been[\\s_]+)?(?:credited|received|added|deposited)",
            Pattern.CASE_INSENSITIVE
    );

    // UPI reference  e.g. "UPI Ref: 123456789012"
    private static final Pattern UPI_REF_PATTERN = Pattern.compile(
            "(?:UPI\\s*Ref(?:erence)?(?:\\s*No\\.?)?|Ref\\s*No\\.?|Txn\\s*(?:ID|Ref))[:\\s]*([A-Z0-9]{10,20})",
            Pattern.CASE_INSENSITIVE
    );

    // Account  e.g. "A/c **1234" / "account ending 5678"
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "(?:A/?[Cc]\\.?|account(?:\\s+ending)?)[\\s*X]*([0-9]{4})",
            Pattern.CASE_INSENSITIVE
    );

    // Merchant / VPA  e.g. "to merchant@upi" or "to AMAZON"
    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
            "(?:to|at|from)\\s+([A-Za-z0-9@._-]{3,40})",
            Pattern.CASE_INSENSITIVE
    );

    // Balance pattern e.g. "avl bal Rs:5656" / "Balance: INR 1200"
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?:avl|available|bal|balance)(?:[\\s_]*bal(?:ance)?)?[\\s_]*(?:Rs\\.?|INR|₹|Rs:|INR:|₹:)[\\s_]*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final String BACKEND_URL =
            "https://stagev2.spendvista.com/api/app-save-bank-notification";

    private static final String TOGGLE_SYNC_URL =
            "https://stagev2.spendvista.com/api/sms/toggle-sync";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // -------------------------------------------------------------------------
    // NotificationListenerService callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Notification Listener Connected! ✅ Service is now active.");
        syncPermissionStatus(true);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Notification Listener Disconnected! ❌");
        syncPermissionStatus(false);
    }

    /**
     * POSTs the current notification listener enabled/disabled status
     * to /api/sms/toggle-sync with the stored Bearer token.
     */
    private void syncPermissionStatus(boolean enabled) {
        executor.submit(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("SmsSyncPrefs", Context.MODE_PRIVATE);
                String token = prefs.getString("auth_token", null);

                if (token == null) {
                    Log.w(TAG, "⚠️ No auth token found. Skipping permission sync.");
                    return;
                }

                JSONObject json = new JSONObject();
                json.put("enabled", enabled);

                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URI(TOGGLE_SYNC_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "✅ Permission sync (" + (enabled ? "CONNECTED" : "DISCONNECTED") + ") response: " + code);
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to sync permission status", e);
            }
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // Only process known bank / payment apps
        if (!BANK_PACKAGES.contains(packageName)) {
            // Uncomment for ultra-verbose debugging of all notifications:
            // Log.v(TAG, "Ignored notification from: " + packageName);
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence bodyCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        String body = bodyCs != null ? bodyCs.toString() : "";

        // Combine title + body for matching
        String fullText = (title + " " + body).trim();

        Log.d(TAG, "Bank notification from " + packageName + ": " + fullText);

        Log.d(TAG, "Bank notification from " + packageName + ": " + fullText);

        // Parse transaction details
        TransactionInfo tx = parseTransaction(fullText, packageName);
        if (tx == null) {
            // Fallback: If regex didn't match, still send the message with "unknown" values
            tx = new TransactionInfo();
            tx.packageName = packageName;
            tx.type = "unknown";
            tx.amount = "0";
            tx.balance = "0";
            tx.timestamp = System.currentTimeMillis();
        }

        // Get human-readable app name
        tx.appName = getAppName(packageName);
        tx.rawText = fullText.trim();

        // Send to backend on a background thread
        final TransactionInfo finalTx = tx;
        executor.submit(() -> sendToBackend(finalTx));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed
    }

    // -------------------------------------------------------------------------
    // Parsing logic
    // -------------------------------------------------------------------------

    private TransactionInfo parseTransaction(String text, String packageName) {
        TransactionInfo tx = new TransactionInfo();
        tx.packageName = packageName;

        // Determine type and amount
        String amount = null;

        Matcher m = DEBIT_PATTERN.matcher(text);
        if (m.find()) { tx.type = "debit"; amount = m.group(1); }

        if (amount == null) {
            m = AMOUNT_FIRST_DEBIT.matcher(text);
            if (m.find()) { tx.type = "debit"; amount = m.group(1); }
        }

        if (amount == null) {
            m = CREDIT_PATTERN.matcher(text);
            if (m.find()) { tx.type = "credit"; amount = m.group(1); }
        }

        if (amount == null) {
            m = AMOUNT_FIRST_CREDIT.matcher(text);
            if (m.find()) { tx.type = "credit"; amount = m.group(1); }
        }

        // If we can't determine amount this is probably not a transaction notification
        if (amount == null) return null;

        // Normalise amount string → remove commas
        tx.amount = amount.replace(",", "");

        // UPI reference
        m = UPI_REF_PATTERN.matcher(text);
        if (m.find()) tx.upiRef = m.group(1);

        // Last 4 digits of account
        m = ACCOUNT_PATTERN.matcher(text);
        if (m.find()) tx.accountLast4 = m.group(1);

        // Merchant / counterparty
        m = MERCHANT_PATTERN.matcher(text);
        if (m.find()) tx.merchant = m.group(1).trim();

        // Available balance
        m = BALANCE_PATTERN.matcher(text);
        if (m.find()) tx.balance = m.group(1).replace(",", "");

        tx.timestamp = System.currentTimeMillis();

        return tx;
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    private void sendToBackend(TransactionInfo tx) {
        try {
            // Format timestamp to ISO 8601 (matching the JS .toISOString() format)
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            String isoDate = df.format(new Date(tx.timestamp));

            JSONObject json = new JSONObject();
            json.put("message",       tx.rawText       != null ? tx.rawText       : "");
            json.put("type",          tx.type          != null ? tx.type          : "unknown");
            json.put("amount",        tx.amount        != null ? tx.amount        : "0");
            json.put("balance",       tx.balance       != null ? tx.balance       : "0");
            json.put("received_at",   isoDate);

            // Optional/Extra fields that might be useful
            json.put("account",       tx.accountLast4  != null ? tx.accountLast4  : "");
            json.put("upi_ref",       tx.upiRef        != null ? tx.upiRef        : "");
            json.put("merchant",      tx.merchant      != null ? tx.merchant      : "");
            json.put("android_id",    Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URI(BACKEND_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            // --- Add Authorization Header ---
            SharedPreferences prefs = getSharedPreferences("SmsSyncPrefs", Context.MODE_PRIVATE);
            String token = prefs.getString("auth_token", null);
            Log.d(TAG, "DEBUG: Using Auth Token: " + (token != null ? token : "NULL"));
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            // --------------------------------

            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Backend response: " + code);
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "Failed to send notification to backend", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    // -------------------------------------------------------------------------
    // Inner data class
    // -------------------------------------------------------------------------

    private static class TransactionInfo {
        String type;          // "debit" | "credit"
        String amount;        // e.g. "1200.00"
        String balance;       // available balance if present
        String upiRef;        // UPI reference number
        String accountLast4;  // last 4 digits of bank account
        String merchant;      // merchant name / UPI VPA
        String packageName;   // source app package
        String appName;       // human-readable app name
        String rawText;       // full notification text (for debugging / ML)
        long   timestamp;     // epoch millis
    }
}
