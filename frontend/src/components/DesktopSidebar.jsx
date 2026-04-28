import { NavLink, useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import BrandLogo from './BrandLogo.jsx';
import { getNavigationRoutes } from '../navigation.js';
import { APP_ICON_192 } from '../brandAssets.js';

export default function DesktopSidebar({
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const navigate = useNavigate();
  const links = getNavigationRoutes({ mhaTrackerEnabled, navigationPreferences });

  return (
    <aside className="desktop-sidebar">
      <button
        type="button"
        className="sidebar-brand brand-home"
        onClick={() => navigate('/dashboard')}
        aria-label="Go to dashboard"
      >
        <img src={APP_ICON_192} alt="" className="brand-mark brand-mark-image" />
        <BrandLogo />
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
