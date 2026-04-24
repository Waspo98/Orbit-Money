import { formatPercentInput } from '../lib/formatters.js';

export default function PercentInput({ value, onChange, ...props }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="decimal"
      value={value}
      onChange={(event) => onChange(formatPercentInput(event.target.value))}
    />
  );
}
