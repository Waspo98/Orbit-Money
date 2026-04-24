import { NavLink, useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import { getNavigationRoutes } from '../navigation.js';

export default function DesktopSidebar({ mhaTrackerEnabled = false }) {
  const navigate = useNavigate();
  const links = getNavigationRoutes({ mhaTrackerEnabled });

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
            <AppIcon name={t.icon} className="sidebar-icon" />
            <span>{t.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
