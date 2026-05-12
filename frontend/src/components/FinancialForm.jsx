export function FinancialFormGrid({
  children,
  className = '',
  single = false
}) {
  const classes = [
    'goal-form-grid',
    single ? 'goal-form-grid-single' : '',
    className
  ].filter(Boolean).join(' ');

  return <div className={classes}>{children}</div>;
}

export function FinancialField({
  label,
  children,
  className = '',
  description
}) {
  return (
    <label className={['field', className].filter(Boolean).join(' ')}>
      <span>{label}</span>
      {description && <small>{description}</small>}
      {children}
    </label>
  );
}
