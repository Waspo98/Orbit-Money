import { NavLink, useNavigate } from 'react-router-dom';
import { PRIMARY_TABS } from './BottomTabs.jsx';

export default function DesktopSidebar({ onMoreClick }) {
  const navigate = useNavigate();

  return (
    <aside className="desktop-sidebar">
      <button
        type="button"
        className="sidebar-brand brand-home"
        onClick={() => navigate('/dashboard')}
        aria-label="Go to dashboard"
      >
        <div className="brand-mark">$</div>
        <div className="brand-name">Budget Tracker</div>
      </button>
      <nav className="sidebar-nav">
        {PRIMARY_TABS.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) =>
              `sidebar-link ${isActive ? 'active' : ''}`
            }
          >
            <span className="sidebar-icon" aria-hidden>{t.icon}</span>
            <span>{t.label}</span>
          </NavLink>
        ))}
        <button
          type="button"
          className="sidebar-link"
          onClick={onMoreClick}
        >
          <span className="sidebar-icon" aria-hidden>⋯</span>
          <span>More</span>
        </button>
      </nav>
    </aside>
  );
}
