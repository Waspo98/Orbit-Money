export default function SelectableListItem({
  active = false,
  disabled = false,
  leading,
  title,
  subtitle,
  sidePrimary,
  sideSecondary,
  className = '',
  onClick,
  ariaLabel
}) {
  return (
    <button
      type="button"
      className={`selectable-list-item ${active ? 'active' : ''} ${className}`}
      onClick={onClick}
      aria-pressed={active}
      aria-label={ariaLabel}
      disabled={disabled}
    >
      {leading && <span className="selectable-list-leading">{leading}</span>}
      <span className="selectable-list-main">
        <strong>{title}</strong>
        {subtitle && <em>{subtitle}</em>}
      </span>
      {(sidePrimary || sideSecondary) && (
        <span className="selectable-list-side">
          {sidePrimary && <strong>{sidePrimary}</strong>}
          {sideSecondary && <em>{sideSecondary}</em>}
        </span>
      )}
    </button>
  );
}
