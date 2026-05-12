import { Children } from 'react';

function inferPattern(children, pattern) {
  if (pattern) return pattern;
  const count = Children.count(children);
  if (count === 4) return 'four';
  if (count === 6) return 'six';
  return 'auto';
}

export default function SubcardGrid({
  as: Component = 'div',
  children,
  className = '',
  innerClassName = '',
  pattern,
  wrap = true,
  ...props
}) {
  const resolvedPattern = inferPattern(children, pattern);
  const frameClassName = ['subcard-grid-frame', className].filter(Boolean).join(' ');
  const gridClassName = [
    'subcard-grid',
    resolvedPattern ? `subcard-grid-${resolvedPattern}` : '',
    innerClassName
  ].filter(Boolean).join(' ');

  if (!wrap) {
    return (
      <Component className={`${frameClassName} ${gridClassName}`} {...props}>
        {children}
      </Component>
    );
  }

  return (
    <Component className={frameClassName} {...props}>
      <div className={gridClassName}>
        {children}
      </div>
    </Component>
  );
}
