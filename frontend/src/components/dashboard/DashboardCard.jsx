export default function DashboardCard({ title, action, children }) {
  return (
    <section className="dashboard-card">
      <header className="dashboard-card-header">
        <h3>{title}</h3>
        {action}
      </header>
      <div className="dashboard-card-body">{children}</div>
    </section>
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
