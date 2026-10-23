import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import { getPrimaryRoutes } from '../navigation.js';
import { triggerHaptic } from '../lib/haptics.js';

export default function BottomTabs({
  onSettingsClick,
  onMoreClick,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const handleOpenDrawer = onSettingsClick || onMoreClick;
  const location = useLocation();
  const primaryTabs = getPrimaryRoutes({ mhaTrackerEnabled, navigationPreferences });
  const currentIndex = primaryTabs.findIndex((tab) => tab.path === location.pathname);
  const totalTabs = primaryTabs.length + 1;

  const isPrimaryTab = currentIndex >= 0;
  const isMoreActive = !isPrimaryTab;
  const activeIndex = isPrimaryTab ? currentIndex : primaryTabs.length;

  const [mounted, setMounted] = useState(false);
  const prevIndexRef = useRef(activeIndex);
  const [isStretching, setIsStretching] = useState(false);
  const [direction, setDirection] = useState('forward');

  // Suppress animation on initial page mount (mount directly at target tab index without sliding from 0)
  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      setMounted(true);
    });
    return () => cancelAnimationFrame(frame);
  }, []);

  useEffect(() => {
    if (!mounted) {
      prevIndexRef.current = activeIndex;
      return;
    }

    if (activeIndex >= 0 && prevIndexRef.current >= 0 && activeIndex !== prevIndexRef.current) {
      const dir = activeIndex > prevIndexRef.current ? 'forward' : 'backward';
      setDirection(dir);
      setIsStretching(true);

      const timer = setTimeout(() => {
        setIsStretching(false);
      }, 180);

      prevIndexRef.current = activeIndex;
      return () => clearTimeout(timer);
    }
    prevIndexRef.current = activeIndex;
  }, [activeIndex, mounted]);

  const prefersReducedMotion = typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
  const scale = isStretching && !prefersReducedMotion ? 1.12 : 1.0;

  const sliderStyle = {
    width: `calc(100% / ${totalTabs})`,
    transform: `translate3d(calc(${activeIndex} * 100%), 0, 0) scaleX(${scale})`,
    transformOrigin: direction === 'forward' ? 'left center' : 'right center',
    opacity: 1,
    transition: !mounted || prefersReducedMotion
      ? 'none'
      : undefined
  };

  return (
    <nav className="bottom-tabs" aria-label="Primary navigation">
      <span
        className="bottom-tabs-slider"
        aria-hidden="true"
        style={sliderStyle}
      />
      {primaryTabs.map((t, index) => (
        <NavLink
          key={t.path}
          to={t.path}
          onClick={() => triggerHaptic('selection')}
          state={
            currentIndex >= 0 && index !== currentIndex
              ? { transition: index > currentIndex ? 'forward' : 'back' }
              : undefined
          }
          className={({ isActive }) =>
            `bottom-tab ${isActive ? 'active' : ''}`
          }
        >
          <span className="bottom-tab-pill">
            <AppIcon name={t.icon} className="bottom-tab-icon" />
          </span>
          <span className="bottom-tab-label">{t.label}</span>
        </NavLink>
      ))}
      <button
        type="button"
        className={`bottom-tab ${isMoreActive ? 'active' : ''}`}
        onClick={() => {
          triggerHaptic('selection');
          handleOpenDrawer?.();
        }}
        aria-label="Open settings and navigation drawer"
      >
        <span className="bottom-tab-pill">
          <AppIcon name="settings" className="bottom-tab-icon" />
        </span>
        <span className="bottom-tab-label">Settings</span>
      </button>
    </nav>
  );
}
