import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.spendvista.app',
  appName: 'Spend Vista',
  webDir: 'dist',
  // bundledWebRuntime: false,
  server: {
    url: 'https://stageapp.spendvista.com/',
    cleartext: true,
    allowNavigation: ['stageapp.spendvista.com'],
  },
  plugins: {
    StatusBar: {
      overlaysWebView: false,
      style: 'light',
      backgroundColor: '#ffffff00',
    },
  },
};

export default config;
