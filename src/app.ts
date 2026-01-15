import { Capacitor } from '@capacitor/core';
import { StatusBar, Style } from '@capacitor/status-bar';

document.addEventListener('DOMContentLoaded', async () => {
  if (Capacitor.isNativePlatform()) {
    await StatusBar.setOverlaysWebView({ overlay: false });
    await StatusBar.setStyle({ style: Style.Light });
  }
});
