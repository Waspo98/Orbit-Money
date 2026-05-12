export default function DashboardCard({
  title,
  subtitle,
  action,
  header,
  children,
  className = '',
  headerClassName = '',
  headerAs: HeaderComponent = 'header',
  headerProps = {},
  bodyClassName = '',
  body = true,
  as: Component = 'section',
  ...props
}) {
  const cardClassName = ['dashboard-card', className].filter(Boolean).join(' ');
  const {
    className: headerPropsClassName = '',
    ...restHeaderProps
  } = headerProps;
  const headerClasses = [
    'dashboard-card-header',
    headerClassName,
    headerPropsClassName
  ].filter(Boolean).join(' ');
  const bodyClasses = ['dashboard-card-body', bodyClassName].filter(Boolean).join(' ');
  const titleNode = subtitle ? (
    <div className="dashboard-card-title-block">
      <h3>{title}</h3>
      {subtitle}
    </div>
  ) : (
    <h3>{title}</h3>
  );

  return (
    <Component className={cardClassName} {...props}>
      <HeaderComponent className={headerClasses} {...restHeaderProps}>
        {header || titleNode}
        {action}
      </HeaderComponent>
      {body ? <div className={bodyClasses}>{children}</div> : children}
    </Component>
  );
}

export function DashProgressBar({ percent, overBudget = false }) {
  const clamped = Math.max(0, Math.min(100, percent || 0));
  const label = `${Math.round(percent || 0)}%${overBudget ? ' over target' : ' complete'}`;
  return (
    <div
      className="budget-progress"
      role="progressbar"
      aria-valuenow={Math.round(percent || 0)}
      aria-valuemin="0"
      aria-valuemax="100"
      title={label}
    >
      <div
        className={`budget-progress-fill ${
          overBudget ? 'over' : percent >= 85 ? 'warning' : ''
        }`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}

export function CardSkeleton() {
  return (
    <div className="dash-skeleton">
      <div className="dash-skeleton-line wide" />
      <div className="dash-skeleton-line" />
      <div className="dash-skeleton-line short" />
    </div>
  );
}
