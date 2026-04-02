# Google SMS Retriever API Implementation - Play Store Friendly ✅

## Overview

This implementation uses **Google's official SMS Retriever API** for bank SMS reading - **completely Play Store compliant**.

### Why This Works

- ✅ **No READ_SMS permission** → No "App can request access to sensitive data" warning
- ✅ **No user permission popup** → Automatic SMS detection
- ✅ **Official Google API** → Officially blessed by Google Play
- ✅ **Bank-controlled filtering** → Banks decide which SMS reach your app
- ✅ **Foreground only** → Works only when app is open
- ✅ **No background listener** → Compliant with modern Android

---

## How It Works

```
1. User logs into Spend Vista
           ↓
2. App calls SmsRetriever.startSmsRetriever()
           ↓
3. Google Play Services initializes SMS retrieval
           ↓
4. When bank sends transaction SMS:
   - SMS includes your app's signature hash
   - Google verifies it's intended for your app
   - Your app receives SMS automatically
           ↓
5. App processes SMS and sends to Laravel backend
```

**Key:** Banks must include your app's **signature hash** in SMS for this to work.

---

## Getting Your App Signature Hash

### For Debug APK:
```bash
cd c:\xampp\htdocs\Projects\Spend-Vista---Android\android
./gradlew.bat signingReport
```

**Output looks like:**
```
Variant: debugAndroidTest
Config: debug
Store: C:\Users\..\.gradle\wrapper\...\debug.keystore
Alias: AndroidDebugKey
MD5: 12:34:56:78:...
SHA1: AB:CD:EF:...
SHA-256: 1a2b3c4d5e6f...
```

**Copy the SHA-256 value** → Share with your bank

### For Release APK:
```bash
./gradlew.bat signingReport
```
Look for variant: `release` and get its SHA-256

---

## Files Modified

### 1. **AndroidManifest.xml**
Changed permission from:
```xml
<uses-permission android:name="android.permission.READ_SMS" />
```
To:
```xml
<uses-permission android:name="com.google.android.gms.permission.RECEIVE_SMS" />
```

**Why:** `RECEIVE_SMS` is Google-managed, doesn't trigger Play Protect

### 2. **BankSmsRetrieverPlugin.java** (NEW)
Location: `android/app/src/main/java/com/spendvista/app/plugins/BankSmsRetrieverPlugin.java`

**Core methods:**
- `startListening()` - Initializes SMS Retriever
- `stopListening()` - Cleanup when app closes
- Automatically parses SMS and extracts:
  - Transaction type (debit/credit)
  - Amount
  - Balance

### 3. **MainActivity.java**
Updated to register new plugin:
```java
registerPlugin(BankSmsRetrieverPlugin.class);
```

### 4. **app.js**
New functions:
- `startBankSmsListener()` - Called after user logs in
- `stopBankSmsListener()` - Called on logout
- Auto-listens for `bankSmsReceived` events

---

## How to Use

### Automatically (After Login)

Once user logs in, app automatically starts listening:

```javascript
// In app.js - happens automatically
startBankSmsListener();
```

The app will receive bank SMS automatically.

### Receive Bank SMS Events

```javascript
BankSmsRetriever.addListener('bankSmsReceived', async (smsData) => {
  console.log('Bank SMS:', smsData);
  // {
  //   raw_message: "Your account debited Rs 100. Balance: Rs 5000",
  //   type: "debit",
  //   amount: "100",
  //   balance: "5000",
  //   received_at: 1712159400000
  // }
});
```

### Stop Listening

```javascript
await stopBankSmsListener();
```

---

## What Banks Need to Do

### Bank Integration Requirements

Your bank's SMS sending system must:

1. **Get your app signature hash** (SHA-256)
   - Debug: Get from `./gradlew signingReport`
   - Release: Get from your keystore

2. **Include hash in SMS**
   ```
   Your account debited INR 500.
   Balance: INR 5000
   #1a2b3c4d5e6f7g8h9i0j
   
   The #hash is your app's signature hash
   ```

3. **Send SMS with Google SMS Retriever**
   - Bank's SMS gateway must support Retriever API
   - Or use credentials from: https://developers.google.com/identity/sms-retriever

### Example Bank SMS Format (ICICI/HDFC Pattern)

```
Your A/c xxxxxx98 debited for Rs 1,000 on 02-Apr-2024 15:30:12 IST at UPI.
Bal: Rs 25,000.00. If unauthorized contact us at 1860-180-1111.
#AbCdEfGh1i2j3k4l5m6n7o8p
```

---

## Laravel Backend Integration

### New Endpoint

**Endpoint:** `POST /api/app-bank-sms`

**Request:**
```json
{
  "message": "Your account debited Rs 100. Balance: Rs 5000",
  "type": "debit",
  "amount": "100",
  "balance": "5000",
  "received_at": "2024-04-02T15:30:00Z"
}
```

**Controller:**
```php
public function processBankSms(Request $request) {
    $user = Auth::user();
    
    BankSms::create([
        'user_id' => $user->id,
        'raw_message' => $request->input('message'),
        'type' => $request->input('type'),
        'amount' => $request->input('amount'),
        'balance' => $request->input('balance'),
        'received_at' => $request->input('received_at'),
    ]);
    
    // Auto-create transaction if amount detected
    if ($request->input('amount')) {
        Transaction::create([
            'user_id' => $user->id,
            'amount' => $request->input('amount'),
            'type' => $request->input('type'),
            'source' => 'sms',
            'sms_text' => $request->input('message'),
        ]);
    }
    
    return response()->json(['success' => true]);
}
```

---

## Build & Ship

### 1. Build Debug APK
```powershell
cd c:\xampp\htdocs\Projects\Spend-Vista---Android
npm run build
npx cap sync android
cd android
./gradlew.bat clean build
```

### 2. Get Signature Hash
```powershell
./gradlew.bat signingReport
# Copy the SHA-256 value
```

### 3. Share with Bank
Send them the SHA-256 hash so they can configure SMS sending.

### 4. Test on Device
```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

- Login to app
- SMS listener starts automatically
- Ask bank to send test SMS
- Should appear in console and app

### 5. Check Logs
```powershell
adb logcat | grep -E "BankSmsRetriever|SMS"
```

---

## Why Play Store Approves This

✅ **Official Google API** - Google Play trusts it  
✅ **No dangerous permissions** - Uses managed permission  
✅ **Transparent to user** - No hidden background sync  
✅ **Bank-controlled** - Bank decides what SMS reaches app  
✅ **Foreground only** - Won't work in background  
✅ **Common pattern** - Banking apps use this everywhere  

---

## Comparison: Old vs New Approach

| Feature | Old (READ_SMS) | New (SMS Retriever) |
|---------|---|---|
| Permission popup | ❌ Yes, scary | ✅ No |
| Play Protect warning | ❌ Yes | ✅ No |
| Requires permission file access | ❌ Yes | ✅ No |
| Bank must know your app | ⚠️ No | ✅ Yes |
| Automatic SMS detection | ✅ Yes | ✅ Yes |
| Play Store friendly | ❌ No | ✅ Yes |
| Background listening | ⚠️ Possible | ✅ Foreground only |

---

## Troubleshooting

### SMS Not Received

1. **Check if bank knows your SHA-256**
   ```bash
   ./gradlew.bat signingReport
   ```
   - Share SHA-256 with bank
   - Bank must configure their SMS gateway with it

2. **Check if app is in foreground**
   - SMS Retriever only works when app is open
   - Press home button = SMS won't be received

3. **Check logcat for errors**
   ```bash
   adb logcat | grep BankSmsRetriever
   ```

4. **Make sure app config matches bank**
   - Your app package: `com.spendvista.app`
   - Share this with bank too

### "SMS Retriever failed to start"

- Google Play Services not installed on device
- Update Play Services: Play Store → Google Play Services → Update
- Or use different device/emulator with Play Services

### App Crashes

```bash
adb logcat | head -100
```
Look for stack traces and share with support.

---

## Security Notes

✅ **Safe because:**
- Only receives SMS intended for your app (bank controls via hash)
- Only works when app is open
- No background access to all SMS
- User hasn't shared sensitive permissions

⚠️ **Important:**
- Never let banks send actual OTPs via SMS Retriever
- OTPs should use standard READ_SMS (with user permission)
- This is for transaction notifications, not authentication

---

## Going Live on Play Store

1. **Build release APK**
   ```bash
   ./gradlew.bat signingReport  # Get release SHA-256
   ```

2. **Get release signature hash**
   - From your production keystore
   - Share with bank

3. **Upload to Play Store**
   - Google reviews app
   - Approves because SMS Retriever is official Google API

4. **Bank updates SMS configuration**
   - To send SMS with release app hash
   - Not debug hash

---

## Resources

- [Google SMS Retriever Docs](https://developers.google.com/identity/sms-retriever)
- [Android BroadcastReceiver Guide](https://developer.android.com/guide/components/broadcasts)
- [Capacitor Plugin Development](https://capacitorjs.com/docs/plugins)

---

## Next Steps

1. ✅ Build APK with new code
2. ✅ Get App Signature Hash
3. ⏳ Contact your bank's integration team
4. ⏳ Share app signature hash + package name
5. ⏳ Bank configures SMS gateway
6. ⏳ Test with real SMS
7. ⏳ Submit to Google Play

---

