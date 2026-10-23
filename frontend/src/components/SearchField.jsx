export default function SearchField({
  value,
  onChange,
  placeholder = 'Search',
  className = '',
  compact = false,
  variant = 'default',
  ariaLabel
}) {
  const hasValue = Boolean(value);
  const isCapsule = variant === 'capsule';

  return (
    <div
      className={`search-field ${compact ? 'search-field-compact' : ''} ${
        isCapsule ? 'search-field-capsule' : ''
      } ${className}`.trim()}
    >
      <span className="material-symbols-rounded search-field-icon search-field-symbol" aria-hidden="true">
        search
      </span>
      <input
        type="search"
        className="search-field-input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={ariaLabel || placeholder}
      />
      {hasValue && (
        <button
          type="button"
          className="search-field-clear"
          onClick={() => onChange('')}
          aria-label="Clear search"
        >
          <span className="material-symbols-rounded" aria-hidden="true">close</span>
        </button>
      )}
    </div>
  );
}
