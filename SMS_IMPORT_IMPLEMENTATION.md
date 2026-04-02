# Bank SMS Import Feature - Implementation Guide

## Overview
This feature allows users to **manually import bank-related SMS messages** from their inbox. It's **Play Store compliant** because:
- ✅ User-triggered only (no background listeners)
- ✅ Explicit permission request at runtime
- ✅ No BroadcastReceiver
- ✅ Filters only relevant messages

---

## Architecture

### 1. Android Plugin (SmsSyncPlugin.java)
**Location:** `android/app/src/main/java/com/spendvista/app/plugins/SmsSyncPlugin.java`

```java
@CapacitorPlugin(
    name = "SmsSync",
    permissions = {
        @Permission(
            alias = "sms",
            strings = { Manifest.permission.READ_SMS }
        )
    }
)
public class SmsSyncPlugin extends Plugin {
    
    @PluginMethod
    public void importSms(PluginCall call) {
        // Requests permission at runtime if not granted
        // Reads last 200 SMS from inbox
        // Filters messages containing bank keywords
        // Returns JSON array to JavaScript
    }
}
```

**Features:**
- Requests `READ_SMS` permission at runtime
- Reads **last 200 SMS** from device inbox only
- Filters keywords: `debited`, `credited`, `withdrawn`, `spent`, `INR`, `Rs` (case-insensitive)
- Returns data in JSON format with: `sender`, `body`, `date`

---

## Android Manifest Configuration

**File:** `android/app/src/main/AndroidManifest.xml`

```xml
<!-- Bank SMS Import (User-triggered, Play Store friendly) -->
<!-- Permission only requested at runtime when user calls importBankSms() -->
<uses-permission android:name="android.permission.READ_SMS" />
```

**Why this is safe for Play Store:**
- Permission is declared in manifest (required)
- Permission is only **requested at runtime** when user triggers import
- Android 6.0+ will show explicitly where permission is needed
- Google Play recognizes this as user-initiated, not malicious

---

## MainActivity Registration

**File:** `android/app/src/main/java/com/spendvista/app/MainActivity.java`

```java
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(SmsSyncPlugin.class);  // ✅ Already registered
        // ... other code
    }
}
```

---

## JavaScript Implementation

**File:** `src/app.js`

### Function: `importBankSms()`

```javascript
async function importBankSms() {
  if (!Capacitor.isNativePlatform()) {
    console.warn('⚠️ SMS import only available on native platforms');
    return;
  }

  try {
    console.log('📩 [SMS] Requesting SMS import...');
    
    // Shows Android permission dialog (if not already granted)
    const result = await SmsSync.importSms();
    
    console.log('✅ [SMS] Imported messages:', result.messages.length);
    
    if (result.messages.length === 0) {
      console.warn('⚠️ [SMS] No bank-related SMS found');
      return result;
    }

    // Send to Laravel backend
    const response = await fetch('https://stagev2.spendvista.com/api/app-import-sms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        messages: result.messages,
        count: result.messages.length,
        imported_at: new Date().toISOString()
      })
    });
    
    const data = await response.json();
    console.log('✅ [SMS] Successfully sent bank SMS to server:', data);
    return result;
    
  } catch (err) {
    console.error('❌ [SMS] Import failed:', err);
    throw err;
  }
}
```

**Exposed globally:**
```javascript
window.importBankSms = importBankSms;
```

---

## Usage in HTML

### Button to Trigger Import

```html
<button onclick="importBankSms()">Import Bank SMS</button>
```

### Using Promises

```javascript
importBankSms()
  .then(result => {
    console.log('Imported:', result.messages.length, 'messages');
    // Update UI with imported data
  })
  .catch(error => {
    console.error('Failed:', error);
    // Show error toast/alert
  });
```

### Using Async/Await

```javascript
async function handleImportClick() {
  try {
    const result = await importBankSms();
    alert(`Imported ${result.messages.length} bank SMS messages`);
  } catch (error) {
    alert('Failed to import SMS: ' + error.message);
  }
}
```

---

## Response Format

### Success Response
```json
{
  "messages": [
    {
      "sender": "+919876543210",
      "body": "Your account has been debited with Rs 500. Balance: Rs 5000",
      "date": "2024-04-02"
    },
    {
      "sender": "+919876543211",
      "body": "Amount of INR 1000 credited to your account",
      "date": "2024-04-01"
    }
  ]
}
```

### Error Response
```json
{
  "error": "READ_SMS permission denied"
}
```

---

## Permission Flow

1. **User clicks "Import Bank SMS" button**
   ↓
2. **importBankSms() function called**
   ↓
3. **Plugin checks if READ_SMS permission granted**
   ↓
4. **If NOT granted:**
   - Android runtime permission dialog shown
   - User sees: "Spend Vista needs access to your SMS"
   - User can Accept or Deny
   ↓
5. **If permission granted:**
   - Plugin reads last 200 SMS from inbox
   - Filters messages with bank keywords
   - Returns filtered array to JavaScript
   ↓
6. **JavaScript sends to Laravel API**
   ↓
7. **Backend processes and stores SMS data**

---

## Laravel Backend API

**Endpoint:** `POST /api/app-import-sms`

**Request Body:**
```json
{
  "messages": [
    {
      "sender": "+919876543210",
      "body": "Debited Rs 500. Balance: Rs 5000",
      "date": "2024-04-02"
    }
  ],
  "count": 1,
  "imported_at": "2024-04-02T10:30:00Z"
}
```

**Example Laravel Controller:**
```php
public function importSms(Request $request) {
    $messages = $request->input('messages', []);
    
    foreach ($messages as $sms) {
        BankSms::create([
            'user_id' => Auth::id(),
            'sender' => $sms['sender'],
            'body' => $sms['body'],
            'date' => $sms['date'],
        ]);
    }
    
    return response()->json([
        'success' => true,
        'imported_count' => count($messages),
    ]);
}
```

---

## Build & Test

### 1. Clean Build
```powershell
cd c:\xampp\htdocs\Projects\Spend-Vista---Android
npm run build
npx cap sync android
cd android
./gradlew.bat clean build
```

### 2. Install Debug APK
```powershell
adb uninstall com.spendvista.app
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Test on Device
- Open Spend Vista app
- Click "Import Bank SMS" button
- Grant `READ_SMS` permission when prompted
- App should display imported bank SMS

### 4. Check Logs
```powershell
adb logcat | grep -E "SMS|SmsSyncPlugin"
```

---

## Security Notes

✅ **Play Store Compliant Because:**
- No background SMS reading
- No automatic sync
- No broadcast receiver
- Explicit user permission
- Runs only when user triggers it
- Filters sensitive data
- Transparent about what it reads

⚠️ **Do NOT:**
- Add BroadcastReceiver for SMS
- Auto-trigger SMS import
- Store SMS indefinitely on device
- Share SMS with third parties
- Use for marketing/spam

---

## Troubleshooting

### "Permission denied"
- User denied READ_SMS permission
- Solution: Go to Settings → Apps → Spend Vista → Permissions → Allow SMS

### "No messages imported"
- No bank-related SMS in inbox
- Keywords not matched
- Solution: Check if SMS contains: debited, credited, withdrawn, spent, INR, or Rs

### "Plugin not found"
- SmsSyncPlugin not registered in MainActivity
- Solution: Ensure `registerPlugin(SmsSyncPlugin.class);` in MainActivity.onCreate()

### "Gradle build error"
- Cache issue
- Solution: Run `./gradlew.bat clean` before building

---

## Plugin Source Code

See [SmsSyncPlugin.java](./android/app/src/main/java/com/spendvista/app/plugins/SmsSyncPlugin.java)

