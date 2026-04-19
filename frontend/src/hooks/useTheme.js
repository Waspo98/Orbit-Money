import { useEffect, useState } from 'react';

const STORAGE_KEY = 'budget-tracker-theme';

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

  return { mode, setMode };
}
