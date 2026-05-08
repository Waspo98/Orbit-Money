import SearchField from './SearchField.jsx';

export default function PageToolbar({
  children,
  label,
  className = ''
}) {
  return (
    <div
      className={`page-toolbar ${className}`.trim()}
      role={label ? 'group' : undefined}
      aria-label={label}
    >
      {children}
    </div>
  );
}

export function PageSearchToolbar({
  value,
  onChange,
  placeholder = 'Search',
  ariaLabel,
  className = '',
  searchClassName = ''
}) {
  return (
    <PageToolbar className={className} label={ariaLabel || placeholder}>
      <SearchField
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        ariaLabel={ariaLabel}
        className={searchClassName}
      />
    </PageToolbar>
  );
}
