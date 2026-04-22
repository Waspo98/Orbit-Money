import { NavLink, useLocation } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';

const PRIMARY_TABS = [
  { to: '/dashboard',    label: 'Dashboard',    icon: 'dashboard' },
  { to: '/transactions', label: 'Transactions', icon: 'transactions' },
  { to: '/budgets',      label: 'Budgets',      icon: 'budgets' },
  { to: '/accounts',     label: 'Accounts',     icon: 'accounts' }
];

export default function BottomTabs({ onMoreClick }) {
  const location = useLocation();
  const currentIndex = PRIMARY_TABS.findIndex((tab) => tab.to === location.pathname);

  return (
    <nav className="bottom-tabs" aria-label="Primary navigation">
      {PRIMARY_TABS.map((t, index) => (
        <NavLink
          key={t.to}
          to={t.to}
          state={
            currentIndex >= 0 && index !== currentIndex
              ? { transition: index > currentIndex ? 'forward' : 'back' }
              : undefined
          }
          className={({ isActive }) =>
            `bottom-tab ${isActive ? 'active' : ''}`
          }
        >
          <AppIcon name={t.icon} className="bottom-tab-icon" />
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
