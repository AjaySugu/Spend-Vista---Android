import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.spendvista.app',
  appName: 'Spend Vista',
  webDir: 'dist',

  // ✅ Load from local dev server (for testing)
  server: {
    url: 'https://stagev2.spendvista.com',
    cleartext: true,
  },

  plugins: {
    StatusBar: {
      overlaysWebView: false,
      style: 'light',
      backgroundColor: '#ffffff00',
    },
    PushNotifications: {
      presentationOptions: ["badge", "sound", "alert"],
    },
  },
};

export default config;