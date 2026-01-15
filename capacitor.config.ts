import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.spendvista.app',
  appName: 'Spend Vista',
  webDir: 'dist',
  // bundledWebRuntime: false,
  server: {
    url: 'https://xspend.tinyjobse.in',
    cleartext: true,
    allowNavigation: ['xspend.tinyjobse.in'],
  },    
};

export default config;
