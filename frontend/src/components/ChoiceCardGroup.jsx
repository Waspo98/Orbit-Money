function defaultValue(option) {
  return option?.value;
}

function defaultLabel(option) {
  return option?.label;
}

function defaultDescription(option) {
  return option?.description;
}

function defaultIcon(option) {
  return option?.icon ?? option?.emoji ?? null;
}

export default function ChoiceCardGroup({
  options = [],
  value,
  onChange,
  ariaLabel,
  type = 'radio',
  columns,
  className = '',
  optionClassName = '',
  getOptionValue = defaultValue,
  getOptionLabel = defaultLabel,
  getOptionDescription = defaultDescription,
  getOptionIcon = defaultIcon,
  getOptionClassName,
  getOptionDisabled,
  isOptionActive,
  renderIcon
}) {
  const isRadio = type === 'radio';
  const groupClasses = [
    'choice-card-group',
    className,
    columns === 2 ? 'choice-card-group-two' : ''
  ].filter(Boolean).join(' ');

  return (
    <div className={groupClasses} role={isRadio ? 'radiogroup' : 'group'} aria-label={ariaLabel}>
      {options.map((option) => {
        const optionValue = getOptionValue(option);
        const active = isOptionActive
          ? isOptionActive(option)
          : String(optionValue) === String(value);
        const disabled = Boolean(getOptionDisabled?.(option));
        const icon = getOptionIcon(option);

        return (
          <button
            key={optionValue}
            type="button"
            className={[
              'choice-card',
              optionClassName,
              getOptionClassName?.(option),
              active ? 'active' : '',
              disabled ? 'locked' : ''
            ].filter(Boolean).join(' ')}
            onClick={() => onChange?.(optionValue, option)}
            disabled={disabled}
            role={isRadio ? 'radio' : undefined}
            aria-checked={isRadio ? active : undefined}
            aria-pressed={isRadio ? undefined : active}
          >
            {renderIcon ? renderIcon(option) : icon != null && (
              <span className="choice-card-icon settings-theme-emoji" aria-hidden="true">
                {icon}
              </span>
            )}
            <span className="choice-card-text settings-theme-text">
              <span className="choice-card-label settings-theme-label">{getOptionLabel(option)}</span>
              {getOptionDescription(option) && (
                <span className="choice-card-copy settings-theme-copy">
                  {getOptionDescription(option)}
                </span>
              )}
            </span>
          </button>
        );
      })}
    </div>
  );
}
