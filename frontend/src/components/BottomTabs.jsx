import { useEffect, useLayoutEffect, useRef, useState } from 'react';
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

  const containerRef = useRef(null);
  const isFirstMeasurement = useRef(true);
  const prevIndexRef = useRef(currentIndex);
  const [isStretching, setIsStretching] = useState(false);
  const [direction, setDirection] = useState('forward');
  const [isMeasured, setIsMeasured] = useState(false);
  const [sliderRect, setSliderRect] = useState({
    left: 0,
    width: 0,
    visible: false
  });

  const prefersReducedMotion = typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;

  const updateSlider = () => {
    const container = containerRef.current;
    if (!container) return;

    if (currentIndex < 0) {
      setSliderRect((prev) => ({ ...prev, visible: false }));
      return;
    }

    const activeTab = container.querySelector('.bottom-tab.active');
    if (!activeTab) {
      setSliderRect((prev) => ({ ...prev, visible: false }));
      return;
    }

    const containerRect = container.getBoundingClientRect();
    const tabRect = activeTab.getBoundingClientRect();

    setSliderRect({
      left: tabRect.left - containerRect.left,
      width: tabRect.width,
      visible: true
    });
    setIsMeasured(true);
  };

  useLayoutEffect(() => {
    updateSlider();
  }, [currentIndex, primaryTabs]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return;

    const observer = new ResizeObserver(() => {
      updateSlider();
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, [currentIndex, primaryTabs]);

  useEffect(() => {
    if (isFirstMeasurement.current) {
      isFirstMeasurement.current = false;
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
  }, [currentIndex]);

  const scale = isStretching && !prefersReducedMotion ? 1.12 : 1.0;

  const sliderStyle = {
    width: sliderRect.width ? `${sliderRect.width}px` : undefined,
    transform: sliderRect.visible
      ? `translate3d(${sliderRect.left}px, 0, 0) scaleX(${scale})`
      : 'translate3d(0, 0, 0) scaleX(1)',
    transformOrigin: direction === 'forward' ? 'left center' : 'right center',
    opacity: sliderRect.visible ? 1 : 0,
    transition: isFirstMeasurement.current || prefersReducedMotion
      ? 'none'
      : undefined
  };

  return (
    <nav
      ref={containerRef}
      className={`bottom-tabs ${isMeasured ? 'has-slider' : ''}`}
      aria-label="Primary navigation"
    >
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
