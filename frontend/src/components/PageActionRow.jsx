export default function PageActionRow({
  children,
  label,
  className = ''
}) {
  return (
    <div
      className={`page-action-row ${className}`.trim()}
      role="group"
      aria-label={label}
    >
      {children}
    </div>
  );
}
