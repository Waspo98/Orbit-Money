import { NativeBiometric } from '@capgo/capacitor-native-biometric';
import { isNativePlatform } from '../nativeApp.js';

const STORAGE_KEY = 'orbit-money-biometric-lock-enabled';

let cachedAvailability = null;

export async function isBiometricsAvailable() {
  if (!isNativePlatform()) return false;
  if (cachedAvailability !== null) return cachedAvailability;

  try {
    const result = await NativeBiometric.isAvailable({ useFallback: true });
    cachedAvailability = Boolean(result?.isAvailable);
    return cachedAvailability;
  } catch (err) {
    console.warn('[biometrics] Availability check error:', err);
    cachedAvailability = false;
    return false;
  }
}

export function isBiometricLockEnabled() {
  return localStorage.getItem(STORAGE_KEY) === 'true';
}

export function setBiometricLockEnabled(enabled) {
  localStorage.setItem(STORAGE_KEY, enabled ? 'true' : 'false');
}

export async function promptBiometricAuth(reason = 'Unlock Orbit Money') {
  if (!isNativePlatform() || !isBiometricLockEnabled()) {
    return true;
  }

  try {
    await NativeBiometric.verifyIdentity({
      title: 'Orbit Money',
      subtitle: reason,
      description: 'Authenticate with your fingerprint, face, or device PIN',
      useFallback: true,
      negativeButtonText: 'Cancel'
    });
    return true;
  } catch (err) {
    console.warn('[biometrics] Authentication failed or cancelled:', err);
    return false;
  }
}
