import { useRef } from 'react';

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
      if (navigator.vibrate) navigator.vibrate(8);
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
