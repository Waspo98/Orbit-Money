import { useEffect, useState } from 'react';
import { formatCurrency } from '../lib/formatters.js';

const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

function checkReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(checkReducedMotion);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = (event) => setReduced(event.matches);
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', onChange);
      return () => mediaQuery.removeEventListener('change', onChange);
    } else if (mediaQuery.addListener) {
      mediaQuery.addListener(onChange);
      return () => mediaQuery.removeListener(onChange);
    }
    return undefined;
  }, []);

  return reduced;
}

/**
 * RollingTicker - Tabular spring-animated numeric ticker.
 * Displays numeric values with tabular numerals (font-variant-numeric: tabular-nums),
 * vertical spring reels for digits (0-9) via translateY(-${d * 10}%), static preservation
 * of non-digit characters ($, ,, ., -, +), and static text rendering under prefers-reduced-motion.
 */
export default function RollingTicker({
  value,
  formatter,
  prefix = '',
  suffix = '',
  className = '',
  as: Component = 'span',
  animateOnMount = true,
  ...rest
}) {
  const reducedMotion = usePrefersReducedMotion();
  const [isMounted, setIsMounted] = useState(!animateOnMount || reducedMotion);

  useEffect(() => {
    if (reducedMotion || !animateOnMount) {
      setIsMounted(true);
      return undefined;
    }

    if (typeof window !== 'undefined' && window.requestAnimationFrame) {
      const raf = window.requestAnimationFrame(() => {
        setIsMounted(true);
      });
      return () => window.cancelAnimationFrame(raf);
    }

    const timer = setTimeout(() => setIsMounted(true), 16);
    return () => clearTimeout(timer);
  }, [animateOnMount, reducedMotion]);

  // Compute formatted text string
  let formatted = '';
  if (typeof formatter === 'function') {
    formatted = formatter(value);
  } else if (typeof value === 'number') {
    formatted = formatCurrency(value);
  } else {
    formatted = String(value ?? '');
  }

  if (prefix && !formatted.startsWith(prefix)) {
    formatted = `${prefix}${formatted}`;
  }
  if (suffix && !formatted.endsWith(suffix)) {
    formatted = `${formatted}${suffix}`;
  }

  const combinedClassName = `rolling-ticker ${className}`.trim();

  // Reduced motion: render static text directly without vertical reel animations
  if (reducedMotion) {
    return (
      <Component
        className={combinedClassName}
        role="text"
        aria-label={formatted}
        {...rest}
      >
        {formatted}
      </Component>
    );
  }

  const chars = Array.from(formatted);

  return (
    <Component
      className={combinedClassName}
      role="text"
      aria-label={formatted}
      {...rest}
    >
      {chars.map((char, index) => {
        const isDigit = /\d/.test(char);
        if (isDigit) {
          const targetDigit = parseInt(char, 10);
          const activeDigit = isMounted ? targetDigit : 0;
          return (
            <span
              key={`d-${index}`}
              className="rolling-ticker-column"
              aria-hidden="true"
            >
              <span
                className="rolling-ticker-reel"
                style={{ transform: `translateY(-${activeDigit * 10}%)` }}
              >
                {DIGITS.map((n) => (
                  <span key={n} className="rolling-ticker-digit">
                    {n}
                  </span>
                ))}
              </span>
            </span>
          );
        }

        return (
          <span
            key={`c-${index}`}
            className="rolling-ticker-char"
            aria-hidden="true"
          >
            {char === ' ' ? '\u00A0' : char}
          </span>
        );
      })}
    </Component>
  );
}
