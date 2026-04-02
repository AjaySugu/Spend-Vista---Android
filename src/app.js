import { PushNotifications } from '@capacitor/push-notifications';
import { registerPlugin, Capacitor } from '@capacitor/core';

// 🔌 Custom Plugins
const DeveloperMode = registerPlugin('DeveloperMode');
const BankSmsRetriever = registerPlugin('BankSmsRetriever');

// 🔔 Push Notification Setup  // Push notification api is not called here it is in login file laravel
let listenersInitialized = false;

// ✅ Setup Push Listeners
function setupPushListeners() {
  if (listenersInitialized) {
    console.log('🔔 [PUSH] Listeners already initialized');
    return;
  }

  listenersInitialized = true;
  console.log('🔔 [PUSH] Initializing listeners...');

  PushNotifications.addListener('registration', async token => {
    console.log('🔥 [PUSH] FCM TOKEN:', token.value);

    try {
      await fetch('https://stagev2.spendvista.com/api/app-save-fcm-token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fcm_token: token.value }),
        credentials: 'include'
      });
      console.log('✅ [PUSH] Token sent to server');
    } catch (err) {
      console.error('❌ [PUSH] Token API error:', err);
    }
  });

  PushNotifications.addListener('registrationError', err => {
    console.error('❌ [PUSH] Registration failed:', err);
  });

  PushNotifications.addListener('pushNotificationReceived', notification => {
    console.log('📢 [PUSH] Notification received:', notification);
  });

  PushNotifications.addListener('pushNotificationActionPerformed', action => {
    console.log('👆 [PUSH] Notification clicked:', action);
  });
}

// ✅ Request Permission (SAFE VERSION)
async function requestPushPermission() {
  try {
    console.log('🔔 [PUSH] Checking permission...');

    let permission = await PushNotifications.checkPermissions();
    console.log('🔔 [PUSH] Current:', permission);

    if (permission.receive !== 'granted') {
      console.log('🔔 [PUSH] Requesting permission...');
      permission = await PushNotifications.requestPermissions();
      console.log('🔔 [PUSH] After request:', permission);
    }

    if (permission.receive === 'granted') {
      console.log('✅ [PUSH] Permission granted');

      if (!window.__pushRegistered) {
        console.log('🔔 [PUSH] Registering device...');
        await PushNotifications.register();
        window.__pushRegistered = true;
      } else {
        console.log('🔁 [PUSH] Already registered');
      }

      return true;
    }

    console.warn('❌ [PUSH] Permission not granted');
    return false;

  } catch (err) {
    console.error('❌ [PUSH] Permission error:', err);
    return false;
  }
}

// 🔔 Trigger after login
async function requestPushPermissionAfterLogin() {
  console.log('🔔 [PUSH] Login detected → requesting permission');

  if (!Capacitor.isNativePlatform()) {
    console.warn('⚠️ Not native platform');
    return;
  }

  // Brief delay to ensure webview is stable
  await new Promise(res => setTimeout(res, 800));
  return await requestPushPermission();
}

// 👀 Watch URL changes (SPA safe)
function watchLoginAndRequestPermission() {
  console.log('👀 Watching login state...');

  // 1️⃣ Check immediately on load (in case they just redirected back)
  if (location.href.includes('logged_in=1')) {
    console.log('🎯 [PUSH] Already logged in on load!');
    requestPushPermissionAfterLogin();
    startBankSmsListener();
  }

  // 2️⃣ Listen for the native fallback event (from MainActivity)
  window.addEventListener('nativeAppResumeLogin', () => {
    console.log('📱 [PUSH] Login detected via native fallback!');
    requestPushPermissionAfterLogin();
    startBankSmsListener();
  });

  // 3️⃣ Polling fallback for SPA navigations
  let lastUrl = location.href;
  setInterval(() => {
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      console.log('🔄 URL changed:', lastUrl);

      if (lastUrl.includes('logged_in=1')) {
        requestPushPermissionAfterLogin();
        startBankSmsListener();
      }
    }
  }, 1500);
}

// 📩 SMS Retriever - Google's official SMS API (Play Store friendly)
// Automatically receives bank SMS when app is open - NO permission popup!

async function startBankSmsListener() {
  if (!Capacitor.isNativePlatform()) {
    console.warn('⚠️ SMS Retriever only available on Android');
    return;
  }

  try {
    console.log('📱 [SMS] Starting bank SMS listener...');
    
    // Start Google's SMS Retriever
    await BankSmsRetriever.startListening();
    
    console.log('✅ [SMS] Bank SMS listener started');
    console.log('📩 [SMS] App will now receive bank SMS automatically');
    console.log('💡 [SMS] Banks must know your app signature hash');

    // Listen for incoming bank SMS
    BankSmsRetriever.addListener('bankSmsReceived', async (smsData) => {
      console.log('📬 [SMS] Bank SMS received:', smsData);
      
      try {
        // Send to Laravel backend
        const response = await fetch('https://stagev2.spendvista.com/api/app-bank-sms', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            message: smsData.raw_message,
            type: smsData.type,
            amount: smsData.amount,
            balance: smsData.balance,
            received_at: new Date(smsData.received_at).toISOString()
          })
        });
        
        const result = await response.json();
        console.log('✅ [SMS] Bank SMS processed by server:', result);
        
      } catch (err) {
        console.error('❌ [SMS] Failed to send to server:', err);
      }
    });

  } catch (err) {
    console.error('❌ [SMS] Failed to start listener:', err);
  }
}

async function stopBankSmsListener() {
  try {
    console.log('🛑 [SMS] Stopping bank SMS listener...');
    await BankSmsRetriever.stopListening();
    console.log('✅ [SMS] Bank SMS listener stopped');
  } catch (err) {
    console.error('❌ [SMS] Error stopping listener:', err);
  }
}

// 🚀 App Init
async function initializeApp() {
  console.log('🚀 App starting...');

  try {
    // 🔒 Developer Mode
    try {
      const { enabled } = await DeveloperMode.check();
      if (enabled) {
        alert('Disable Developer Mode');
        return;
      }
    } catch (e) {
      console.warn('DeveloperMode plugin missing');
    }

    // 🔔 Setup listeners
    setupPushListeners();

    // 👀 Watch login
    watchLoginAndRequestPermission();

    // 📩 SMS Retriever - auto-receive bank SMS after login
    // Will start listening when user logs in
    if (location.href.includes('logged_in=1')) {
      startBankSmsListener();
    }

    console.log('✅ App ready');

  } catch (err) {
    console.error('❌ App init error:', err);
  }
}

// 🚀 Start
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initializeApp);
} else {
  initializeApp();
}

// 🔧 Debug access
window.requestPushPermission = requestPushPermission;
window.startBankSmsListener = startBankSmsListener;
window.stopBankSmsListener = stopBankSmsListener;