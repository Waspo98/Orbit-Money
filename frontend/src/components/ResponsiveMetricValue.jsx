import { useLayoutEffect, useRef, useState } from 'react';

const SIZE_PRESETS = {
  sm: { minRem: 0.92, maxRem: 1.08 },
  md: { minRem: 0.98, maxRem: 1.4 },
  lg: { minRem: 1.02, maxRem: 2.05 },
  xl: { minRem: 1.08, maxRem: 2.45 },
  display: { minRem: 1.12, maxRem: 3 }
};

function resolveSize(size, minRem, maxRem) {
  const preset = SIZE_PRESETS[size] || SIZE_PRESETS.lg;
  return {
    minRem: Number.isFinite(minRem) ? minRem : preset.minRem,
    maxRem: Number.isFinite(maxRem) ? maxRem : preset.maxRem
  };
}

function getParentContentWidth(parent) {
  if (!parent || typeof window === 'undefined') return 0;
  const style = window.getComputedStyle(parent);
  const paddingX = (parseFloat(style.paddingLeft) || 0) + (parseFloat(style.paddingRight) || 0);
  return Math.max(0, parent.clientWidth - paddingX);
}

export default function ResponsiveMetricValue({
  as: Component = 'strong',
  children,
  className = '',
  tone = 'accent',
  size = 'md',
  fitGutter = 4,
  minRem,
  maxRem,
  style,
  title,
  ...props
}) {
  const frameRef = useRef(null);
  const valueRef = useRef(null);
  const [fontSize, setFontSize] = useState(null);
  const resolvedSize = resolveSize(size, minRem, maxRem);
  const frameStyle = {
    '--responsive-metric-min': `${resolvedSize.minRem}rem`,
    '--responsive-metric-max': `${resolvedSize.maxRem}rem`,
    ...style
  };
  const valueStyle = fontSize ? { fontSize: `${fontSize}px` } : undefined;
  const autoTitle =
    title ?? (typeof children === 'string' || typeof children === 'number' ? String(children) : undefined);
  const classes = [
    'responsive-metric-value',
    `responsive-metric-value-size-${size}`,
    tone ? `responsive-metric-value-tone-${tone}` : '',
    className
  ].filter(Boolean).join(' ');

  useLayoutEffect(() => {
    const frame = frameRef.current;
    const value = valueRef.current;
    if (!frame || !value || typeof window === 'undefined') return undefined;

    let frameId = null;
    let cancelled = false;

    function measure() {
      if (cancelled) return;
      const frameWidth = frame.getBoundingClientRect().width;
      const parentContentWidth = getParentContentWidth(frame.parentElement);
      const availableWidth = frameWidth || parentContentWidth;
      if (!availableWidth) return;

      const rootFontSize = parseFloat(window.getComputedStyle(document.documentElement).fontSize) || 16;
      const maxPx = resolvedSize.maxRem * rootFontSize;
      const minPx = resolvedSize.minRem * rootFontSize;
      const targetWidth = Math.max(1, availableWidth - fitGutter * 2);
      const previousFontSize = value.style.fontSize;
      const previousWidth = value.style.width;
      const previousMaxWidth = value.style.maxWidth;

      value.style.fontSize = `${maxPx}px`;
      value.style.width = 'max-content';
      value.style.maxWidth = 'none';
      const fullWidth = value.getBoundingClientRect().width;
      value.style.fontSize = previousFontSize;
      value.style.width = previousWidth;
      value.style.maxWidth = previousMaxWidth;

      if (!fullWidth) return;
      const fittedPx = Math.max(minPx, Math.min(maxPx, Math.floor((maxPx * targetWidth) / fullWidth)));
      setFontSize((current) => (Math.abs((current || 0) - fittedPx) > 0.5 ? fittedPx : current));
    }

    function scheduleMeasure() {
      if (frameId) window.cancelAnimationFrame(frameId);
      frameId = window.requestAnimationFrame(() => {
        frameId = null;
        measure();
      });
    }

    scheduleMeasure();
    const observer =
      'ResizeObserver' in window
        ? new ResizeObserver(scheduleMeasure)
        : null;
    if (observer) {
      observer.observe(frame);
      if (frame.parentElement) observer.observe(frame.parentElement);
    }
    window.addEventListener('resize', scheduleMeasure);
    document.fonts?.ready?.then(scheduleMeasure).catch(() => {});

    return () => {
      cancelled = true;
      if (observer) observer.disconnect();
      window.removeEventListener('resize', scheduleMeasure);
      if (frameId) window.cancelAnimationFrame(frameId);
    };
  }, [children, fitGutter, resolvedSize.maxRem, resolvedSize.minRem]);

  return (
    <span className="responsive-metric-value-frame" ref={frameRef} style={frameStyle}>
      <Component ref={valueRef} className={classes} style={valueStyle} title={autoTitle} {...props}>
        {children}
      </Component>
    </span>
  );
}
