import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.spendvista.app',
  appName: 'Spend Vista',
  webDir: 'dist',

  // ✅ Load from remote staging URL
  server: {
    url: 'https://stagev2.spendvista.com/?preview_disclosure=1',
    cleartext: true,
    allowNavigation: ["*"]
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