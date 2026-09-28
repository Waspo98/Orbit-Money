import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';
import { Keyboard } from '@capacitor/keyboard';
import { Browser } from '@capacitor/browser';

export function isNativePlatform() {
  return Capacitor.isNativePlatform();
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

export function onAppStateChange(callback) {
  appStateListeners.add(callback);
  return () => appStateListeners.delete(callback);
}

export async function initNativeApp({ onBackButton, onNavigateRoot } = {}) {
  if (!isNativePlatform()) return;

  try {
    StatusBar.setOverlaysWebView({ overlay: false }).catch(() => {});

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
      if (onBackButton && onBackButton()) {
        return;
      }
      if (canGoBack) {
        window.history.back();
      } else if (onNavigateRoot && onNavigateRoot()) {
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
    const isDark =
      theme === 'dark' ||
      (theme === 'system' &&
        window.matchMedia &&
        window.matchMedia('(prefers-color-scheme: dark)').matches);
    const isAmoled = isDark && darkVariant === 'amoled';

    let backgroundColor = '#fafaf7';
    if (isAmoled) {
      backgroundColor = '#000000';
    } else if (isDark) {
      backgroundColor = '#0a1410';
    }

    await StatusBar.setStyle({ style: isDark ? Style.Dark : Style.Light });
    await StatusBar.setBackgroundColor({ color: backgroundColor });
    await StatusBar.setOverlaysWebView({ overlay: false });
  } catch (err) {
    // Gracefully ignore on unsupported devices
  }
}

