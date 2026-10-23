import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import {
  OVERLAY_ANIM_MS,
  releaseCurrentOverlayHistoryEntry,
  useBodyScrollLock,
  useOverlayBackDismiss
} from './overlayBehavior.js';
import { getMoreRoutes } from '../navigation.js';
import { triggerHaptic } from '../lib/haptics.js';

const DRAG_START_PX = 10;
const DRAG_CLOSE_PX = 80;
const DRAG_CLOSE_VELOCITY = 0.45;

function createGestureState() {
  return {
    active: false,
    mode: 'idle',
    startX: 0,
    startY: 0,
    lastX: 0,
    lastMoveAt: 0,
    distanceX: 0,
    velocityX: 0
  };
}

export default function RightDrawer({
  open,
  onClose,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerState, setDrawerState] = useState('opening');
  const timerRef = useRef(null);
  const enterFrameRef = useRef(null);
  const drawerRef = useRef(null);
  const backdropRef = useRef(null);
  const scrollRef = useRef(null);
  const gestureRef = useRef(createGestureState());
  const crossedThresholdRef = useRef(false);
  const suppressNextClickRef = useRef(false);

  const visibleItems = getMoreRoutes({ mhaTrackerEnabled, navigationPreferences });

  useEffect(() => {
    if (!open) return undefined;

    clearCloseTimer();
    gestureRef.current = createGestureState();
    suppressNextClickRef.current = false;
    crossedThresholdRef.current = false;
    setDrawerState('opening');

    enterFrameRef.current = window.requestAnimationFrame(() => {
      enterFrameRef.current = window.requestAnimationFrame(() => {
        enterFrameRef.current = null;
        setDrawerState('open');
      });
    });

    return () => {
      if (enterFrameRef.current) {
        window.cancelAnimationFrame(enterFrameRef.current);
        enterFrameRef.current = null;
      }
      clearCloseTimer();
    };
  }, [open]);

  // Escape key listener for dismissal
  useEffect(() => {
    if (!open) return undefined;
    function onKeyDown(e) {
      if (e.key === 'Escape') close({ animate: true });
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);

  // Lock body scroll and register back-dismiss
  useBodyScrollLock(open);
  useOverlayBackDismiss(open, () => close({ animate: true }));

  function clearCloseTimer() {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }

  function close(options = { animate: true }) {
    if (drawerState === 'closing') return;
    if (options?.animate === false) {
      onClose();
      return;
    }
    setDrawerState('closing');
    timerRef.current = window.setTimeout(onClose, OVERLAY_ANIM_MS);
  }

  function handleItemClick(item) {
    if (suppressNextClickRef.current || item.comingSoon) return;
    const isCurrentRoute = item.path === location.pathname;

    const shouldReplaceOverlayEntry = !isCurrentRoute
      ? releaseCurrentOverlayHistoryEntry()
      : false;

    triggerHaptic('selection');
    close({ animate: true });
    if (isCurrentRoute) return;

    navigate(item.path, {
      replace: shouldReplaceOverlayEntry,
      state: { transition: 'forward' }
    });
  }

  // Touch gesture handlers for right-swipe
  function onTouchStart(e) {
    if (e.touches.length !== 1) return;
    const touch = e.touches[0];
    gestureRef.current = {
      active: true,
      mode: 'pending',
      startX: touch.clientX,
      startY: touch.clientY,
      lastX: touch.clientX,
      lastMoveAt: window.performance.now(),
      distanceX: 0,
      velocityX: 0
    };
    crossedThresholdRef.current = false;
  }

  function onTouchMove(e) {
    const gesture = gestureRef.current;
    if (!gesture.active || e.touches.length !== 1) return;

    const touch = e.touches[0];
    const deltaX = touch.clientX - gesture.startX;
    const deltaY = touch.clientY - gesture.startY;
    const now = window.performance.now();
    const elapsed = Math.max(1, now - gesture.lastMoveAt);
    gesture.velocityX = (touch.clientX - gesture.lastX) / elapsed;
    gesture.lastX = touch.clientX;
    gesture.lastMoveAt = now;

    if (gesture.mode === 'scroll') return;

    if (gesture.mode === 'pending') {
      if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > DRAG_START_PX) {
        gesture.mode = 'scroll';
        return;
      }
      if (deltaX > DRAG_START_PX) {
        gesture.mode = 'drawer';
      } else if (deltaX < -DRAG_START_PX) {
        gesture.mode = 'scroll';
        return;
      }
    }

    if (gesture.mode !== 'drawer') return;
    if (e.cancelable) e.preventDefault();

    const distanceX = Math.max(0, deltaX);
    gesture.distanceX = distanceX;

    const drawerNode = drawerRef.current;
    if (drawerNode) {
      drawerNode.classList.add('dragging');
      drawerNode.style.transform = `translate3d(${distanceX}px, 0, 0)`;
    }

    if (backdropRef.current && drawerNode) {
      const progress = Math.min(1, distanceX / (drawerNode.offsetWidth || 320));
      backdropRef.current.style.opacity = String(Math.max(0, 1 - progress * 0.9));
      backdropRef.current.style.transition = 'none';
    }

    if (distanceX > DRAG_CLOSE_PX && !crossedThresholdRef.current) {
      crossedThresholdRef.current = true;
      triggerHaptic('selection');
    } else if (distanceX <= DRAG_CLOSE_PX && crossedThresholdRef.current) {
      crossedThresholdRef.current = false;
    }
  }

  function onTouchEnd() {
    const gesture = gestureRef.current;
    if (!gesture.active) return;

    const drawerNode = drawerRef.current;
    if (drawerNode) {
      drawerNode.classList.remove('dragging');
      drawerNode.style.transform = '';
    }
    if (backdropRef.current) {
      backdropRef.current.style.opacity = '';
      backdropRef.current.style.transition = '';
    }

    if (gesture.mode === 'drawer') {
      const shouldClose =
        gesture.distanceX > DRAG_CLOSE_PX ||
        (gesture.distanceX > 20 && gesture.velocityX > DRAG_CLOSE_VELOCITY);

      if (shouldClose) {
        suppressNextClickRef.current = true;
        window.setTimeout(() => {
          suppressNextClickRef.current = false;
        }, OVERLAY_ANIM_MS);
        close({ animate: true });
      }
    }

    gestureRef.current = createGestureState();
  }

  if (!open) return null;
  const isClosing = drawerState === 'closing';

  return (
    <div
      ref={backdropRef}
      className={`right-drawer-backdrop ${drawerState === 'open' ? 'open' : ''} ${isClosing ? 'closing' : ''}`.trim()}
      onClick={() => close({ animate: true })}
      role="dialog"
      aria-modal="true"
      aria-label="Navigation & Settings"
    >
      <aside
        ref={drawerRef}
        className={`right-drawer ${isClosing ? 'closing' : ''}`.trim()}
        onClick={(e) => e.stopPropagation()}
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        onTouchCancel={onTouchEnd}
      >
        <div className="right-drawer-header">
          <span className="right-drawer-title">Settings & Navigation</span>
          <button
            type="button"
            className="right-drawer-close-btn"
            onClick={() => {
              triggerHaptic('tap');
              close({ animate: true });
            }}
            aria-label="Close drawer"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="right-drawer-scroll" ref={scrollRef}>
          <div className="right-drawer-list">
            {visibleItems.map((item) => {
              const isActive = location.pathname === item.path;
              const displayLabel = item.shortLabel || item.label;
              return (
                <button
                  key={item.path}
                  type="button"
                  className={`right-drawer-item m3-tactile ${isActive ? 'active' : ''}`}
                  onClick={() => handleItemClick(item)}
                  disabled={item.comingSoon}
                >
                  <div className="right-drawer-badge">
                    <AppIcon name={item.icon} className="right-drawer-icon" />
                  </div>
                  <div className="right-drawer-item-body">
                    <span className="right-drawer-item-title">{displayLabel}</span>
                    {item.description && (
                      <span className="right-drawer-item-desc">{item.description}</span>
                    )}
                  </div>
                  <svg className="right-drawer-chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                  </svg>
                </button>
              );
            })}
          </div>
        </div>
      </aside>
    </div>
  );
}
