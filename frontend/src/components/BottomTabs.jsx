import { NavLink } from 'react-router-dom';

const PRIMARY_TABS = [
  { to: '/dashboard',    label: 'Dashboard',    icon: '◐' },
  { to: '/transactions', label: 'Transactions', icon: '≡' },
  { to: '/budgets',      label: 'Budgets',      icon: '◉' },
  { to: '/accounts',     label: 'Accounts',     icon: '⬢' }
];

export default function BottomTabs({ onMoreClick }) {
  return (
    <nav className="bottom-tabs" role="tablist">
      {PRIMARY_TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          role="tab"
          className={({ isActive }) =>
            `bottom-tab ${isActive ? 'active' : ''}`
          }
        >
          <span className="bottom-tab-icon" aria-hidden>{t.icon}</span>
          <span>{t.label}</span>
        </NavLink>
      ))}
      <button
        type="button"
        className="bottom-tab"
        onClick={onMoreClick}
        aria-label="Open more menu"
      >
        <span className="bottom-tab-icon" aria-hidden>⋯</span>
        <span>More</span>
      </button>
    </nav>
  );
}

export { PRIMARY_TABS };
