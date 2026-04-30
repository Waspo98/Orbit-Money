import { NavLink, useLocation } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import MoreDotsIcon from './MoreDotsIcon.jsx';
import { getPrimaryRoutes } from '../navigation.js';

export default function BottomTabs({ onMoreClick }) {
  const location = useLocation();
  const primaryTabs = getPrimaryRoutes();
  const currentIndex = primaryTabs.findIndex((tab) => tab.path === location.pathname);

  return (
    <nav className="bottom-tabs" aria-label="Primary navigation">
      {primaryTabs.map((t, index) => (
        <NavLink
          key={t.path}
          to={t.path}
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
        <MoreDotsIcon className="bottom-tab-icon" />
        <span>More</span>
      </button>
    </nav>
  );
}
