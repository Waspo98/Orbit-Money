import { useLayoutEffect, useRef, useState, useEffect } from 'react';
import { triggerHaptic } from '../lib/haptics.js';

export default function SegmentedControl({
  options,
  value,
  onChange,
  ariaLabel,
  className = '',
  role = 'tablist',
  buttonRole,
  getOptionLabel = (option) => option.label,
  getOptionSubtitle = () => null,
  getOptionValue = (option) => option.value,
  isOptionActive,
  getOptionDisabled = (option) => Boolean(option.disabled)
}) {
  const containerRef = useRef(null);
  const isFirstMeasurement = useRef(true);
  const [thumbStyle, setThumbStyle] = useState({
    transform: 'translate3d(0, 0, 0)',
    width: 0,
    height: 0,
    opacity: 0
  });
  const [isMeasured, setIsMeasured] = useState(false);

  const resolvedButtonRole = buttonRole === undefined
    ? role === 'tablist' ? 'tab' : undefined
    : buttonRole;
  const usesTabs = resolvedButtonRole === 'tab';
  const usesRadio = resolvedButtonRole === 'radio';

  const updateThumb = () => {
    const container = containerRef.current;
    if (!container) return;

    const activeButton = container.querySelector('button.active');
    if (!activeButton) {
      setThumbStyle((prev) => ({ ...prev, opacity: 0 }));
      setIsMeasured(false);
      return;
    }

    const { offsetLeft, offsetTop, offsetWidth, offsetHeight } = activeButton;
    const isInitial = isFirstMeasurement.current;
    if (isInitial) {
      isFirstMeasurement.current = false;
    }

    setThumbStyle({
      transform: `translate3d(${offsetLeft}px, ${offsetTop}px, 0)`,
      width: `${offsetWidth}px`,
      height: `${offsetHeight}px`,
      opacity: 1,
      transition: isInitial ? 'none' : undefined
    });
    setIsMeasured(true);
  };

  useLayoutEffect(() => {
    updateThumb();
  }, [value, options]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return;

    const observer = new ResizeObserver(() => {
      updateThumb();
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, [value, options]);

  const classes = [
    'segmented-control',
    isMeasured ? 'has-thumb' : '',
    className
  ].filter(Boolean).join(' ');

  return (
    <div
      ref={containerRef}
      className={classes}
      role={role || undefined}
      aria-label={ariaLabel}
    >
      <span
        className="segmented-control-thumb"
        aria-hidden="true"
        style={thumbStyle}
      />
      {options.map((option) => {
        const optionValue = getOptionValue(option);
        const label = getOptionLabel(option);
        const subtitle = getOptionSubtitle(option);
        const active = isOptionActive
          ? isOptionActive(option, value)
          : Object.is(optionValue, value);
        const disabled = getOptionDisabled(option);

        return (
          <button
            key={optionValue}
            type="button"
            role={resolvedButtonRole}
            className={active ? 'active' : ''}
            aria-selected={usesTabs ? active : undefined}
            aria-checked={usesRadio ? active : undefined}
            aria-pressed={!usesTabs && !usesRadio ? active : undefined}
            disabled={disabled}
            onClick={() => {
              if (!disabled) {
                triggerHaptic('selection');
                onChange?.(optionValue, option);
              }
            }}
          >
            {subtitle ? (
              <span className="segmented-option-content">
                <span className="segmented-option-label">{label}</span>
                <span className="segmented-option-subtitle">{subtitle}</span>
              </span>
            ) : label}
          </button>
        );
      })}
    </div>
  );
}
