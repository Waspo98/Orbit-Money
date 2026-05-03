export default function CollapseIndicator({
  expanded,
  className = '',
  label,
  visible = true
}) {
  if (!visible) return null;

  return (
    <span
      className={`collapse-indicator ${expanded ? 'expanded' : ''} ${className}`.trim()}
      aria-hidden={label ? undefined : 'true'}
      aria-label={label}
    >
      <span className="collapse-indicator-chevron" />
    </span>
  );
}
