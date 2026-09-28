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

export async function triggerHaptic(type = 'light') {
  if (!isNativePlatform() || !isHapticsEnabled()) return;

  try {
    switch (type) {
      case 'selection':
        await Haptics.selectionChanged();
        break;
      case 'medium':
        await Haptics.impact({ style: ImpactStyle.Medium });
        break;
      case 'heavy':
        await Haptics.impact({ style: ImpactStyle.Heavy });
        break;
      case 'success':
        await Haptics.notification({ type: NotificationType.Success });
        break;
      case 'warning':
      case 'error':
        await Haptics.notification({ type: NotificationType.Warning });
        break;
      case 'light':
      default:
        await Haptics.impact({ style: ImpactStyle.Light });
        break;
    }
  } catch {
    // Non-blocking fallback for unsupported devices
  }
}
