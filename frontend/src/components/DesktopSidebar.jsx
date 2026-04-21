import { NavLink, useNavigate } from 'react-router-dom';
import { PRIMARY_TABS } from './BottomTabs.jsx';
import { MORE_ITEMS } from './MoreSheet.jsx';

export default function DesktopSidebar({ mhaTrackerEnabled = false }) {
  const navigate = useNavigate();
  const links = [
    ...PRIMARY_TABS.map((item) => ({
      ...item,
      path: item.to
    })),
    ...MORE_ITEMS.filter((item) => item.feature !== 'mha' || mhaTrackerEnabled)
  ];

  return (
    <aside className="desktop-sidebar">
      <button
        type="button"
        className="sidebar-brand brand-home"
        onClick={() => navigate('/dashboard')}
        aria-label="Go to dashboard"
      >
        <div className="brand-mark">$</div>
        <div className="brand-name">Orbit Money</div>
      </button>
      <nav className="sidebar-nav">
        {links.map((t) => (
          <NavLink
            key={t.path}
            to={t.path}
            className={({ isActive }) =>
              `sidebar-link ${isActive ? 'active' : ''}`
            }
          >
            <span className="sidebar-icon" aria-hidden>{t.icon}</span>
            <span>{t.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
