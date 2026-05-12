import { isValidDateOnly } from '../lib/localDate.js';

export default function DateInput({
  value = '',
  onChange,
  ...props
}) {
  const safeValue = value && isValidDateOnly(value) ? value : '';

  return (
    <input
      type="date"
      value={safeValue}
      onChange={(event) => onChange?.(event.target.value)}
      {...props}
    />
  );
}
