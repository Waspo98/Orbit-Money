import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { isNativePlatform } from '../nativeApp.js';

const STORAGE_KEY = 'orbit-money-haptics-enabled';

export function isHapticsEnabled() {
  const saved = localStorage.getItem(STORAGE_KEY);
  return saved === null ? true : saved === 'true';
}

export function setHapticsEnabled(enabled) {
  localStorage.setItem(STORAGE_KEY, enabled ? 'true' : 'false');
}

/**
 * Universal micro-haptic dispatcher
 * Supports native Capacitor Haptics and calibrated Web Vibration patterns.
 */
export async function triggerHaptic(type = 'light') {
  if (!isHapticsEnabled()) return;

  // Web / PWA vibration fallback
  if (!isNativePlatform()) {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      try {
        switch (type) {
          case 'selection':
            navigator.vibrate(4);
            break;
          case 'tap':
          case 'light':
            navigator.vibrate(8);
            break;
          case 'medium':
            navigator.vibrate(15);
            break;
          case 'heavy':
            navigator.vibrate(25);
            break;
          case 'detachment':
            navigator.vibrate([12, 30, 8]); // Double-tick detachment feel
            break;
          case 'threshold':
            navigator.vibrate(10);
            break;
          case 'refresh-snap':
            navigator.vibrate([8, 40, 14]);
            break;
          case 'success':
            navigator.vibrate([10, 40, 15]);
            break;
          case 'warning':
            navigator.vibrate([20, 50, 20]);
            break;
          case 'error':
            navigator.vibrate([30, 40, 30, 40, 30]);
            break;
          default:
            navigator.vibrate(10);
            break;
        }
      } catch {
        // Non-blocking fallback
      }
    }
    return;
  }

  // Native Android Capacitor Haptics
  try {
    switch (type) {
      case 'selection':
        await Haptics.selectionChanged();
        break;
      case 'tap':
      case 'light':
        await Haptics.impact({ style: ImpactStyle.Light });
        break;
      case 'medium':
        await Haptics.impact({ style: ImpactStyle.Medium });
        break;
      case 'heavy':
        await Haptics.impact({ style: ImpactStyle.Heavy });
        break;
      case 'detachment':
        // Crisp mechanical detachment on swipe release
        await Haptics.impact({ style: ImpactStyle.Medium });
        break;
      case 'threshold':
        // Light tick when crossing pull-to-refresh or swipe threshold
        await Haptics.selectionChanged();
        break;
      case 'refresh-snap':
        await Haptics.impact({ style: ImpactStyle.Medium });
        break;
      case 'success':
        await Haptics.notification({ type: NotificationType.Success });
        break;
      case 'warning':
        await Haptics.notification({ type: NotificationType.Warning });
        break;
      case 'error':
        await Haptics.notification({ type: NotificationType.Error });
        break;
      default:
        await Haptics.impact({ style: ImpactStyle.Light });
        break;
    }
  } catch {
    // Non-blocking fallback for unsupported devices
  }
}

// Convenient named helper exports for gesture primitives
export const hapticSelection = () => triggerHaptic('selection');
export const hapticTap = () => triggerHaptic('tap');
export const hapticDetachment = () => triggerHaptic('detachment');
export const hapticThreshold = () => triggerHaptic('threshold');
export const hapticRefreshSnap = () => triggerHaptic('refresh-snap');
export const hapticSuccess = () => triggerHaptic('success');
export const hapticWarning = () => triggerHaptic('warning');
export const hapticError = () => triggerHaptic('error');

export async function hapticSelectionStart() {
  if (!isHapticsEnabled() || !isNativePlatform()) return;
  try {
    await Haptics.selectionStart();
  } catch {}
}

export async function hapticSelectionEnd() {
  if (!isHapticsEnabled() || !isNativePlatform()) return;
  try {
    await Haptics.selectionEnd();
  } catch {}
}
