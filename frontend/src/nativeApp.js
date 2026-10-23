import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';
import { Keyboard } from '@capacitor/keyboard';
import { Browser } from '@capacitor/browser';

export function isNativePlatform() {
  return Capacitor.isNativePlatform();
}

if (typeof document !== 'undefined' && isNativePlatform()) {
  document.documentElement.classList.add('is-native-app');
}

const appUrlListeners = new Set();

export function onAppUrlOpen(callback) {
  appUrlListeners.add(callback);
  return () => appUrlListeners.delete(callback);
}

export async function openInAppBrowser(url) {
  if (isNativePlatform()) {
    try {
      await Browser.open({ url, presentationStyle: 'popover' });
      return;
    } catch (err) {
      console.warn('[nativeApp] Browser.open error, falling back to window.open:', err);
    }
  }
  window.open(url, '_blank');
}

export async function closeInAppBrowser() {
  if (isNativePlatform()) {
    try {
      await Browser.close();
    } catch {
      // Ignore if already closed
    }
  }
}

let backgroundedAt = 0;
const appStateListeners = new Set();
let nativeInitialized = false;
let backButtonHandler = null;
let navigateRootHandler = null;

export function onAppStateChange(callback) {
  appStateListeners.add(callback);
  return () => appStateListeners.delete(callback);
}

export async function initNativeApp({ onBackButton, onNavigateRoot } = {}) {
  backButtonHandler = onBackButton || null;
  navigateRootHandler = onNavigateRoot || null;
  if (typeof document !== 'undefined' && isNativePlatform()) {
    document.documentElement.classList.add('is-native-app');
  }
  if (!isNativePlatform() || nativeInitialized) return;
  nativeInitialized = true;

  try {
    StatusBar.setOverlaysWebView({ overlay: true }).catch(() => {});
    StatusBar.setStyle({ style: Style.Dark }).catch(() => {});

    CapApp.addListener('appUrlOpen', (data) => {
      console.log('[nativeApp] App opened with URL:', data?.url);
      for (const listener of appUrlListeners) {
        try {
          listener(data);
        } catch (e) {
          console.error('[nativeApp] appUrlOpen listener error:', e);
        }
      }
    });

    CapApp.addListener('appStateChange', ({ isActive }) => {
      if (!isActive) {
        backgroundedAt = Date.now();
      }
      const elapsedMs = isActive && backgroundedAt ? Date.now() - backgroundedAt : 0;
      if (isActive) {
        backgroundedAt = 0;
      }
      for (const listener of appStateListeners) {
        try {
          listener({ isActive, elapsedMs });
        } catch (e) {
          console.error('[nativeApp] appStateChange listener error:', e);
        }
      }
    });

    CapApp.addListener('backButton', ({ canGoBack }) => {
      if (backButtonHandler && backButtonHandler()) {
        return;
      }
      if (canGoBack) {
        window.history.back();
      } else if (navigateRootHandler && navigateRootHandler()) {
        return;
      } else {
        CapApp.minimizeApp();
      }
    });

    Keyboard.setResizeMode({ mode: 'body' }).catch(() => {});
  } catch (err) {
    console.warn('[nativeApp] Init warning:', err);
  }
}

export async function syncNativeStatusBar(theme, darkVariant) {
  if (!isNativePlatform()) return;

  try {
    await StatusBar.setOverlaysWebView({ overlay: true });
    await StatusBar.setStyle({ style: Style.Dark });
  } catch (err) {
    // Gracefully ignore on unsupported devices
  }
}
