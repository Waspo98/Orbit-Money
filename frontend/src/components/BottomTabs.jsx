import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import MoreDotsIcon from './MoreDotsIcon.jsx';
import { getPrimaryRoutes } from '../navigation.js';
import { triggerHaptic } from '../lib/haptics.js';

export default function BottomTabs({
  onMoreClick,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const location = useLocation();
  const primaryTabs = getPrimaryRoutes({ mhaTrackerEnabled, navigationPreferences });
  const currentIndex = primaryTabs.findIndex((tab) => tab.path === location.pathname);
  const totalTabs = primaryTabs.length + 1;

  const [mounted, setMounted] = useState(false);
  const prevIndexRef = useRef(currentIndex);
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
      prevIndexRef.current = currentIndex;
      return;
    }

    if (currentIndex >= 0 && prevIndexRef.current >= 0 && currentIndex !== prevIndexRef.current) {
      const dir = currentIndex > prevIndexRef.current ? 'forward' : 'backward';
      setDirection(dir);
      setIsStretching(true);

      const timer = setTimeout(() => {
        setIsStretching(false);
      }, 180);

      prevIndexRef.current = currentIndex;
      return () => clearTimeout(timer);
    }
    prevIndexRef.current = currentIndex;
  }, [currentIndex, mounted]);

  const prefersReducedMotion = typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
  const scale = isStretching && !prefersReducedMotion ? 1.12 : 1.0;

  const sliderStyle = {
    width: `calc(100% / ${totalTabs})`,
    transform: currentIndex >= 0
      ? `translate3d(calc(${currentIndex} * 100%), 0, 0) scaleX(${scale})`
      : 'translate3d(0, 0, 0) scaleX(1)',
    transformOrigin: direction === 'forward' ? 'left center' : 'right center',
    opacity: currentIndex >= 0 ? 1 : 0,
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
        className="bottom-tab"
        onClick={() => {
          triggerHaptic('selection');
          onMoreClick?.();
        }}
        aria-label="Open more menu"
      >
        <span className="bottom-tab-pill">
          <MoreDotsIcon className="bottom-tab-icon" />
        </span>
        <span className="bottom-tab-label">More</span>
      </button>
    </nav>
  );
}
