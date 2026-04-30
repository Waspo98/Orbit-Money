export default function ExpandingSection({
  expanded,
  className = '',
  innerClassName = '',
  children,
  id
}) {
  return (
    <div
      id={id}
      className={`expanding-section ${expanded ? 'expanded' : ''} ${className}`.trim()}
      aria-hidden={!expanded}
      inert={expanded ? undefined : ''}
    >
      <div className={`expanding-section-inner ${innerClassName}`.trim()}>
        {children}
      </div>
    </div>
  );
}
