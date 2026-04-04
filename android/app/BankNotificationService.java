package com.spendvista.app;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

    private static final String TAG = "BankNotifService";

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
            "in.fincare.sfbl.digital"                    // Fincare
    ));

    // -------------------------------------------------------------------------
    // Regex patterns for common Indian bank SMS / notification formats
    // -------------------------------------------------------------------------

    // Debit patterns  e.g. "debited by Rs.1,200.00" / "INR 500 debited"
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
            "(?:debited(?:\\s+by)?|debit(?:ed)?(?:\\s+of)?)" +
            "\\s*(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    // Credit patterns  e.g. "credited with Rs.500" / "INR 2000 credited"
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "(?:credited(?:\\s+(?:with|by))?|credit(?:ed)?(?:\\s+of)?)" +
            "\\s*(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    // Amount-first patterns  e.g. "Rs.1200 debited" / "₹500 credited"
    private static final Pattern AMOUNT_FIRST_DEBIT = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:has been\\s+)?debited",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AMOUNT_FIRST_CREDIT = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:has been\\s+)?credited",
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

    private static final String BACKEND_URL =
            "https://stageapp.spendvista.com/api/app-save-bank-notification";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // -------------------------------------------------------------------------
    // NotificationListenerService callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // Only process known bank / payment apps
        if (!BANK_PACKAGES.contains(packageName)) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence bodyCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        String body = bodyCs != null ? bodyCs.toString() : "";

        // Combine title + body for matching
        String fullText = title + " " + body;

        Log.d(TAG, "Bank notification from " + packageName + ": " + fullText);

        // Parse transaction details
        TransactionInfo tx = parseTransaction(fullText, packageName);
        if (tx == null) {
            Log.d(TAG, "No transaction found in notification — skipping.");
            return;
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

        tx.timestamp = System.currentTimeMillis();

        return tx;
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    private void sendToBackend(TransactionInfo tx) {
        try {
            JSONObject json = new JSONObject();
            json.put("type",          tx.type          != null ? tx.type          : "unknown");
            json.put("amount",        tx.amount        != null ? tx.amount        : "0");
            json.put("upi_ref",       tx.upiRef        != null ? tx.upiRef        : "");
            json.put("account_last4", tx.accountLast4  != null ? tx.accountLast4  : "");
            json.put("merchant",      tx.merchant      != null ? tx.merchant      : "");
            json.put("app_package",   tx.packageName   != null ? tx.packageName   : "");
            json.put("app_name",      tx.appName       != null ? tx.appName       : "");
            json.put("raw_text",      tx.rawText       != null ? tx.rawText       : "");
            json.put("timestamp",     tx.timestamp);

            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URL(BACKEND_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
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
        String upiRef;        // UPI reference number
        String accountLast4;  // last 4 digits of bank account
        String merchant;      // merchant name / UPI VPA
        String packageName;   // source app package
        String appName;       // human-readable app name
        String rawText;       // full notification text (for debugging / ML)
        long   timestamp;     // epoch millis
    }
}
