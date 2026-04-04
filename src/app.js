import { PushNotifications } from '@capacitor/push-notifications';
import { registerPlugin, Capacitor } from '@capacitor/core';

// 🔌 Custom Plugins
const DeveloperMode = registerPlugin('DeveloperMode');
const BankSmsRetriever = registerPlugin('BankSmsRetriever');

// � Local Notification Log Storage
let notificationLog = [];
const MAX_LOG_ENTRIES = 500; // Keep last 500 entries

// 🔔 Push Notification Setup
let listenersInitialized = false;

// 📝 Log Helper Function with Android Logcat Integration
function logNotificationEvent(level, message, data = null) {
  const timestamp = new Date().toISOString();
  const logEntry = {
    timestamp,
    level,
    message,
    data,
    url: window.location.href
  };

  notificationLog.push(logEntry);
  
  // Keep only last entries
  if (notificationLog.length > MAX_LOG_ENTRIES) {
    notificationLog = notificationLog.slice(-MAX_LOG_ENTRIES);
  }

  // Save to localStorage for persistence
  try {
    localStorage.setItem('spendvista_notification_log', JSON.stringify(notificationLog));
  } catch (e) {
    console.warn('Could not save to localStorage:', e);
  }

  // Format for console with consistent tag for easy filtering in logcat
  const tag = 'SPENDVISTA';
  const consoleMessage = `[${tag}] [${level.toUpperCase()}] ${message}`;
  const dataStr = data ? `\n📊 Data: ${JSON.stringify(data, null, 2)}` : '';

  // Log to console (will appear in adb logcat)
  switch (level) {
    case 'error':
      console.error(`❌ ${consoleMessage}${dataStr}`);
      break;
    case 'warning':
      console.warn(`⚠️ ${consoleMessage}${dataStr}`);
      break;
    case 'sms':
      console.log(`📲 ${consoleMessage}${dataStr}`);
      break;
    case 'success':
      console.log(`✅ ${consoleMessage}${dataStr}`);
      break;
    default:
      console.log(`ℹ️ ${consoleMessage}${dataStr}`);
  }
}

// 🏦 Check if notification is from a bank app
function isBankNotification(notification) {
  const bankKeywords = [
    'hdfc', 'icici', 'axis', 'sbi', 'bob', 'boi', // Banks
    'gpay', 'googlepay', 'paytm', 'phonepe', 'whatsapp', // Payment apps
    'upi', 'transfer', 'transaction', 'debit', 'credit', 'amount',
    'balance', 'payment', 'transaction', 'deposit', 'withdrawal',
    'bank', 'card', 'account', 'alert'
  ];

  const text = JSON.stringify(notification).toLowerCase();
  return bankKeywords.some(keyword => text.includes(keyword));
}

// ✅ Setup Push Listeners with Comprehensive Logging
function setupPushListeners() {
  if (listenersInitialized) {
    logNotificationEvent('info', '🔔 Listeners already initialized');
    return;
  }

  listenersInitialized = true;
  logNotificationEvent('info', '🔔 Initializing push notification listeners...');
  logNotificationEvent('info', '📡 Listening for: Firebase Push Notifications (FCM)');
  logNotificationEvent('info', '💡 For bank SMS: Go to Settings → Apps → Notifications → Notification Access → Enable Spend Vista');

  // Listen to FCM Token Registration
  PushNotifications.addListener('registration', async token => {
    logNotificationEvent('info', '🔥 FCM TOKEN REGISTERED', {
      token: token.value,
      length: token.value.length
    });

    try {
      const response = await fetch('https://stagev2.spendvista.com/api/app-save-fcm-token', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({ fcm_token: token.value }),
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        logNotificationEvent('success', '✅ FCM Token sent to server successfully', data);
      } else {
        logNotificationEvent('warning', '⚠️ Server returned error for token', { 
          status: response.status,
          statusText: response.statusText
        });
      }
    } catch (err) {
      logNotificationEvent('error', '❌ Failed to send FCM token to server', {
        error: err.message
      });
    }
  });

  // Handle Registration Errors
  PushNotifications.addListener('registrationError', err => {
    logNotificationEvent('error', '❌ Push registration failed', {
      error: err.message || String(err)
    });
  });

  // 📲 Listen to Notifications Received (App in Background or Foreground)
  PushNotifications.addListener('pushNotificationReceived', async notification => {
    const isBankNotif = isBankNotification(notification);
    const logLevel = isBankNotif ? 'sms' : 'info';
    
    logNotificationEvent(logLevel, `📢 NOTIFICATION RECEIVED ${isBankNotif ? '(BANK/PAYMENT)' : ''}`, {
      title: notification.title,
      body: notification.body,
      data: notification.data,
      raw: JSON.stringify(notification, null, 2),
      isBank: isBankNotif
    });

    // Send notification data to Laravel backend
    await sendNotificationToBackend('received', notification, isBankNotif);
  });

  // 👆 Listen to Notification Actions (User Interaction)
  PushNotifications.addListener('pushNotificationActionPerformed', async action => {
    const isBankNotif = isBankNotification(action.notification);
    
    logNotificationEvent(logLevel, `👆 NOTIFICATION ACTION ${isBankNotif ? '(BANK/PAYMENT)' : ''}`, {
      actionId: action.actionId,
      inputValue: action.inputValue,
      notification: {
        title: action.notification?.title,
        body: action.notification?.body
      },
      isBank: isBankNotif
    });

    // Send notification action to Laravel backend
    await sendNotificationToBackend('action', action, isBankNotif);
  });
}

// 🔄 Send Notification Data to Laravel Backend with Logging
async function sendNotificationToBackend(eventType, data, isBankNotif = false) {
  try {
    const payload = {
      event_type: eventType,
      timestamp: new Date().toISOString(),
      is_bank_notification: isBankNotif,
      device_info: {
        platform: Capacitor.getPlatform(),
        app_version: '1.0.0'
      },
      notification_data: {
        title: data.notification?.title || data.title || null,
        body: data.notification?.body || data.body || null,
        data: data.data || {},
        raw_notification: JSON.stringify(data)
      }
    };

    logNotificationEvent('info', `📤 Sending ${isBankNotif ? 'BANK SMS' : 'notification'} to backend`, {
      eventType,
      isBank: isBankNotif,
      title: payload.notification_data.title
    });

    const response = await fetch('https://stagev2.spendvista.com/api/app-notification-log', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(payload),
      credentials: 'include'
    });

    if (response.ok) {
      const result = await response.json();
      logNotificationEvent('success', `✅ ${isBankNotif ? 'Bank SMS' : 'Notification'} logged on backend`, {
        responseStatus: 'success',
        messageId: result.id || result.message_id
      });
    } else {
      logNotificationEvent('warning', '⚠️ Backend returned error', { 
        status: response.status,
        isBank: isBankNotif
      });
    }
  } catch (err) {
    logNotificationEvent('error', `❌ Failed to send ${isBankNotif ? 'bank SMS' : 'notification'} to backend`, {
      error: err.message
    });
  }
}

// ✅ Request Permission (SAFE VERSION)
async function requestPushPermission() {
  try {
    logNotificationEvent('info', '🔔 Checking notification permission...');

    let permission = await PushNotifications.checkPermissions();
    logNotificationEvent('info', '🔔 Current permission status', permission);

    if (permission.receive !== 'granted') {
      logNotificationEvent('info', '🔔 Requesting notification permission...');
      permission = await PushNotifications.requestPermissions();
      logNotificationEvent('info', '🔔 Permission request response', permission);
    }

    if (permission.receive === 'granted') {
      logNotificationEvent('success', '✅ Notification permission GRANTED');

      if (!window.__pushRegistered) {
        logNotificationEvent('info', '🔔 Registering device for push notifications...');
        await PushNotifications.register();
        window.__pushRegistered = true;
        logNotificationEvent('success', '✅ Device registered for push notifications');
      } else {
        logNotificationEvent('info', '🔁 Device already registered for push');
      }

      // Initialize listeners after successful registration
      setupPushListeners();
      return true;
    }

    logNotificationEvent('warning', '⚠️ Notification permission NOT granted');
    return false;

  } catch (err) {
    logNotificationEvent('error', '❌ Permission request failed', {
      error: err.message
    });
    return false;
  }
}

// 🔔 Trigger after login
async function requestPushPermissionAfterLogin() {
  logNotificationEvent('info', '🔔 Login detected → requesting push permission');

  if (!Capacitor.isNativePlatform()) {
    logNotificationEvent('warning', '⚠️ Not on native platform - push notifications may not work');
    return;
  }

  // Brief delay to ensure webview is stable
  await new Promise(res => setTimeout(res, 800));
  return await requestPushPermission();
}

// 📋 Get Notification Log (for debugging)
function getNotificationLog() {
  return notificationLog;
}

// 📋 Clear Notification Log
function clearNotificationLog() {
  notificationLog = [];
  localStorage.removeItem('spendvista_notification_log');
  logNotificationEvent('info', '🗑️ Notification log cleared');
  return true;
}

// 📋 Export Notification Log as File
function exportNotificationLog() {
  const dataStr = JSON.stringify(notificationLog, null, 2);
  const dataBlob = new Blob([dataStr], { type: 'application/json' });
  const url = URL.createObjectURL(dataBlob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `notification_log_${new Date().toISOString().slice(0, 19)}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  logNotificationEvent('info', '📥 Notification log exported as JSON');
}

// 🔌 Export for external use
export {
  setupPushListeners,
  requestPushPermission,
  requestPushPermissionAfterLogin,
  sendNotificationToBackend,
  getNotificationLog,
  clearNotificationLog,
  exportNotificationLog,
  isBankNotification,
  logNotificationEvent,
  DeveloperMode,
  BankSmsRetriever
};

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