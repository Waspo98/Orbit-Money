export default function SegmentedControl({
  options,
  value,
  onChange,
  ariaLabel,
  className = '',
  role = 'tablist',
  buttonRole,
  getOptionLabel = (option) => option.label,
  getOptionValue = (option) => option.value,
  isOptionActive,
  getOptionDisabled = (option) => Boolean(option.disabled)
}) {
  const classes = ['segmented-control', className].filter(Boolean).join(' ');
  const resolvedButtonRole = buttonRole === undefined
    ? role === 'tablist' ? 'tab' : undefined
    : buttonRole;
  const usesTabs = resolvedButtonRole === 'tab';
  const usesRadio = resolvedButtonRole === 'radio';

  return (
    <div className={classes} role={role || undefined} aria-label={ariaLabel}>
      {options.map((option) => {
        const optionValue = getOptionValue(option);
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
              if (!disabled) onChange?.(optionValue, option);
            }}
          >
            {getOptionLabel(option)}
          </button>
        );
      })}
    </div>
  );
}
