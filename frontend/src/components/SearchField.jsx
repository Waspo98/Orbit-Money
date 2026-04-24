export default function SearchField({
  value,
  onChange,
  placeholder = 'Search',
  className = '',
  compact = false,
  ariaLabel
}) {
  const hasValue = Boolean(value);

  return (
    <div className={`search-field ${compact ? 'search-field-compact' : ''} ${className}`.trim()}>
      <span className="search-field-icon" aria-hidden="true">
        {'\u2315'}
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
          {'\u00d7'}
        </button>
      )}
    </div>
  );
}
