# Spend Vista - Android App

![Build Status](https://img.shields.io/badge/status-active-brightgreen)
![Firebase](https://img.shields.io/badge/Firebase-Integrated-orange)
![Capacitor](https://img.shields.io/badge/Capacitor-7.4.4-blue)

A Capacitor-based Android application for expense tracking with bank SMS notification detection and Firebase Cloud Messaging integration.

## 📋 Prerequisites

- **Node.js** 16+ and npm
- **Android SDK** (API 24+)
- **Java JDK** 11+
- **Android Studio** (recommended)
- **Firebase Project** (configured with `google-services.json`)

## 🚀 Quick Start

### 1. Install Dependencies
```bash
npm install
```

### 2. Build for Android
```bash
# Development build
npm run build
npx cap sync android

# Or use the batch script
build_and_run.bat

# Or for a production release
build-debug.bat
```

### 3. Run on Emulator/Device
```bash
# After the build, open Android Studio
android/

# Or via command line:
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

## 📁 Project Structure

```
src/
  ├── app.js (Main notification logic, backend integration)
  ├── app.ts (StatusBar configuration)
  ├── index.html (App entry point)
  ├── manifest.json (PWA manifest)
  └── assets/ (Images, icons, styles)

android/
  ├── app/ (Android app source)
  │   ├── src/main/ (Java/Kotlin code, AndroidManifest.xml)
  │   ├── build.gradle (App-level Gradle config)
  │   └── google-services.json (Firebase config)
  ├── build.gradle (Project-level Gradle config)
  └── local.properties (SDK/NDK paths)

Documentation/
  ├── TESTING_GUIDE.md (Complete testing procedures)
  ├── FIREBASE_APP_DISTRIBUTION_SETUP.md (Distribution guide)
  ├── LOGCAT_MONITORING_GUIDE.md (Debugging and monitoring)
  └── SMS_RETRIEVER_PLAY_STORE_GUIDE.md (SMS retriever setup)
```

## 🔔 Notification Features

### Bank SMS Detection
The app automatically detects bank and payment notifications:
- **HDFC, ICICI, Axis, SBI, IDBI, Kotak**
- **Google Pay, PhonePe, Paytm, Amazon Pay**
- **UPI transactions and general payment alerts**

All detected notifications are:
1. Logged locally in browser storage
2. Sent to backend API: `POST /api/app-notification-log`
3. Tagged in Android logcat: `[SPENDVISTA]`

### Firebase Cloud Messaging
- **Automatic token registration** on app launch
- **Token saved to backend**: `POST /api/app-save-fcm-token`
- **Push notification handling** with event logging

## 🔧 Configuration

### Backend API Endpoints
```javascript
const API_BASE = "https://stagev2.spendvista.com/api";

// Endpoints used:
POST /app-save-fcm-token          // Register device token
POST /app-notification-log        // Log received notifications
```

### Firebase Configuration
- **Project ID**: `spend-vista`
- **App ID**: `com.spendvista.app`
- **Google Services JSON**: `android/app/google-services.json`

## 🧪 Testing

### Test Bank SMS (Emulator)
```powershell
# Windows PowerShell
.\send_test_sms.ps1
# Then select option 1 for HDFC test SMS
```

### Monitor Notifications
```powershell
# Terminal 1: Watch logcat
adb logcat | Select-String SPENDVISTA

# Or use the helper script
.\monitor_logcat.ps1
```

### View Notification Logs
```
# Browser-based log viewer
src/debug-logs.html

# Keyboard shortcut: Ctrl+Shift+L in app
```

## 📚 Complete Documentation

- **[TESTING_GUIDE.md](TESTING_GUIDE.md)** - Detailed testing procedures for SMS, notifications, and real devices
- **[FIREBASE_APP_DISTRIBUTION_SETUP.md](FIREBASE_APP_DISTRIBUTION_SETUP.md)** - App distribution and release management
- **[LOGCAT_MONITORING_GUIDE.md](LOGCAT_MONITORING_GUIDE.md)** - Debugging, logcat filtering, and advanced monitoring
- **[SMS_RETRIEVER_PLAY_STORE_GUIDE.md](SMS_RETRIEVER_PLAY_STORE_GUIDE.md)** - SMS retriever API integration (Play Store)

## 🛠️ Build Scripts

| Script | Purpose |
|--------|---------|
| `build_and_run.bat` | Development: build + install + run |
| `build-debug.bat` | Debug build for testing |
| `send_test_sms.ps1` | Test SMS notifications on emulator |
| `monitor_logcat.ps1` | Real-time app log monitoring |
| `distribute_release.bat` | Release APK to Firebase App Distribution |

## 📦 Dependencies

### Core
- **@capacitor/core** v7.4.4
- **@capacitor/push-notifications** v7.4.4
- **Vite** (build tool)
- **TypeScript**

### Android/Firebase
- **Firebase Cloud Messaging** (gradle plugin 4.0.1)
- **Google Play Services** (notifications)
- **Capacitor plugins** (runtime integration)

## 🔐 Permissions

### Requested Permissions
```xml
<!-- AndroidManifest.xml -->
android.permission.POST_NOTIFICATIONS     (Push notifications)
android.permission.INTERNET                (API calls)
android.permission.READ_PHONE_STATE        (Device info for backend)
```

### User-Granted Permissions
- **Notification Access** (READ_LOGS for system notifications)
- **Notification Post** (Android 13+ runtime permission)

## 🚨 Troubleshooting

### FCM Token Not Registering
1. Check: `adb logcat | grep "FCM"`
2. Verify `google-services.json` is in `android/app/`
3. Ensure `google-services` Gradle plugin is applied

### Bank Notifications Not Detected
1. Check keyword list in `src/app.js` → `isBankNotification()`
2. Monitor logcat: `adb logcat | Select-String SPENDVISTA`
3. Verify app has Notification Access: Settings → Notifications → Access

### Backend Not Receiving Logs
1. Check Laravel queue/jobs: `php artisan queue:work`
2. Monitor network: Chrome DevTools → Network tab
3. Verify endpoint: `POST /api/app-notification-log` responds with 200

## 📊 Debug Pages

### Local Development
```html
<!-- View notification logs -->
file:///src/debug-logs.html

<!-- Keyboard shortcut -->
Ctrl+Shift+L (in app)
```

## 🔐 Release Build & Distribution

```bash
# Build release APK (signed with debug keystore for testing)
cd android
./gradlew.bat clean assembleRelease

# Distribute to Firebase App Distribution
firebase appdistribution:distribute \
  --app="1:208192444461:android:fa6290b072e2c8e178c0a4" \
  --testers="email@example.com" \
  --release-notes="Version 1.0" \
  "app/build/outputs/apk/release/app-release.apk"

# Or use the batch script
distribute_release.bat
```

## 📱 Real Device Testing

1. **Install APK from Firebase App Distribution** (link sent to testers)
2. **Enable Notification Access**: Settings → Apps → Spend Vista → Permissions → Notification Access
3. **Receive bank SMS** on the device
4. **Monitor logs**: `adb logcat | grep SPENDVISTA`
5. **Verify backend logs** for POST requests to `/api/app-notification-log`

## 📞 Support & Issues

For issues with:
- **Firebase setup** → See [FIREBASE_APP_DISTRIBUTION_SETUP.md](FIREBASE_APP_DISTRIBUTION_SETUP.md)
- **Testing methodology** → See [TESTING_GUIDE.md](TESTING_GUIDE.md)
- **Monitoring/debugging** → See [LOGCAT_MONITORING_GUIDE.md](LOGCAT_MONITORING_GUIDE.md)
- **SMS retriever** → See [SMS_RETRIEVER_PLAY_STORE_GUIDE.md](SMS_RETRIEVER_PLAY_STORE_GUIDE.md)

## 📄 License

This project is based on Capacitor Create App.
