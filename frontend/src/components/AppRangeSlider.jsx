import { useRef } from 'react';
import { triggerHaptic } from '../lib/haptics.js';

export default function AppRangeSlider({
  value,
  onChange,
  hapticStep,
  step = 1,
  ...props
}) {
  const lastTickRef = useRef(null);

  function tick(nextValue) {
    const numericValue = Number(nextValue);
    const tickSize = Number(hapticStep) || Number(step) || 1;
    const tickValue = Math.round(numericValue / tickSize);
    if (lastTickRef.current !== tickValue) {
      lastTickRef.current = tickValue;
      triggerHaptic('selection');
    }
  }

  return (
    <input
      {...props}
      type="range"
      step={step}
      value={value}
      onPointerDown={() => tick(value)}
      onChange={(event) => {
        tick(event.target.value);
        onChange?.(event);
      }}
    />
  );
}
