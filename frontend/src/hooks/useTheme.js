import { useEffect, useState } from 'react';

const STORAGE_KEY = 'orbit-money-theme';
const DARK_VARIANT_STORAGE_KEY = 'orbit-money-dark-variant';
const DARK_VARIANTS = new Set(['classic', 'amoled']);

/**
 * Three-way theme state: 'light' | 'dark' | 'system'.
 * - Persists to localStorage (per browser, no server round-trip)
 * - 'system' mode listens to prefers-color-scheme and updates live
 * - Sets data-theme attribute on <html>; CSS handles everything else
 *
 * Intentionally matches the pre-paint script in index.html so first frame is
 * correct and subsequent changes don't flash.
 */
export function useTheme() {
  const [mode, setMode] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) || 'system';
    } catch {
      return 'system';
    }
  });
  const [darkVariant, setDarkVariantState] = useState(() => {
    try {
      const saved = localStorage.getItem(DARK_VARIANT_STORAGE_KEY);
      return DARK_VARIANTS.has(saved) ? saved : 'classic';
    } catch {
      return 'classic';
    }
  });

  function setDarkVariant(value) {
    setDarkVariantState(DARK_VARIANTS.has(value) ? value : 'classic');
  }

  // Apply the mode to <html>.
  useEffect(() => {
    const root = document.documentElement;
    if (mode === 'dark') {
      root.setAttribute('data-theme', 'dark');
    } else if (mode === 'light') {
      root.setAttribute('data-theme', 'light');
    } else {
      root.removeAttribute('data-theme');
    }
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      /* ignore */
    }
  }, [mode]);

  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-dark-variant', darkVariant);
    try {
      localStorage.setItem(DARK_VARIANT_STORAGE_KEY, darkVariant);
    } catch {
      /* ignore */
    }
  }, [darkVariant]);

  return { mode, setMode, darkVariant, setDarkVariant };
}
