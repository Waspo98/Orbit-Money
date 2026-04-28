import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import { OVERLAY_ANIM_MS, useBodyScrollLock } from './overlayBehavior.js';
import { getMoreRoutes } from '../navigation.js';

export default function MoreSheet({
  open,
  onClose,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const navigate = useNavigate();
  const [closing, setClosing] = useState(false);
  const [dragY, setDragY] = useState(0);
  const [settling, setSettling] = useState(false);
  const timerRef = useRef(null);
  const scrollRef = useRef(null);
  const dragRef = useRef({
    active: false,
    pointerId: null,
    pointerType: null,
    pulling: false,
    startY: 0,
    lastY: 0,
    startScrollTop: 0,
    distance: 0,
    lastMoveAt: 0,
    velocityY: 0,
    dragged: false
  });
  const suppressNextClickRef = useRef(false);
  const visibleItems = getMoreRoutes({ mhaTrackerEnabled, navigationPreferences });

  useEffect(() => {
    if (open) {
      setClosing(false);
      setDragY(0);
      setSettling(false);
      suppressNextClickRef.current = false;
    }
  }, [open]);

  function close(options = { animate: true }) {
    if (closing) return;
    const shouldAnimate = options?.animate !== false;
    if (!shouldAnimate) {
      onClose();
      return;
    }
    setClosing(true);
    timerRef.current = setTimeout(onClose, OVERLAY_ANIM_MS);
  }

  useEffect(() => {
    if (!open) return;
    function onKey(e) {
      if (e.key === 'Escape') close({ animate: true });
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useBodyScrollLock(open);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!open || !scrollRef.current) return undefined;
    const scrollNode = scrollRef.current;

    function onNativeTouchStart(event) {
      if (event.touches.length !== 1) return;
      handleTouchStart(event);
    }

    function onNativeTouchMove(event) {
      handleTouchMove(event);
    }

    function onNativeTouchEnd() {
      finishTouchDrag();
    }

    scrollNode.addEventListener('touchstart', onNativeTouchStart, { passive: true });
    scrollNode.addEventListener('touchmove', onNativeTouchMove, { passive: false });
    scrollNode.addEventListener('touchend', onNativeTouchEnd, { passive: true });
    scrollNode.addEventListener('touchcancel', onNativeTouchEnd, { passive: true });

    return () => {
      scrollNode.removeEventListener('touchstart', onNativeTouchStart);
      scrollNode.removeEventListener('touchmove', onNativeTouchMove);
      scrollNode.removeEventListener('touchend', onNativeTouchEnd);
      scrollNode.removeEventListener('touchcancel', onNativeTouchEnd);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  if (!open) return null;

  function handleItemClick(item) {
    if (suppressNextClickRef.current) return;
    if (item.comingSoon) return;
    close({ animate: true });
    setTimeout(() => {
      navigate(item.path, { state: { transition: 'from-more' } });
    }, OVERLAY_ANIM_MS);
  }

  function resetDrag() {
    dragRef.current = {
      active: false,
      pointerId: null,
      pointerType: null,
      pulling: false,
      startY: 0,
      lastY: 0,
      startScrollTop: 0,
      distance: 0,
      lastMoveAt: 0,
      velocityY: 0,
      dragged: false
    };
    setSettling(false);
    setDragY(0);
  }

  function setPullDistance(value) {
    const distance = Math.min(Math.max(value, 0), window.innerHeight);
    dragRef.current.distance = distance;
    if (distance > 6) dragRef.current.dragged = true;
    setDragY(distance);
  }

  function maybeSuppressClick() {
    suppressNextClickRef.current = true;
    window.setTimeout(() => {
      suppressNextClickRef.current = false;
    }, OVERLAY_ANIM_MS);
  }

  function shouldCloseDrawer(distance, velocityY) {
    const distanceThreshold = Math.min(72, window.innerHeight * 0.08);
    return distance > distanceThreshold || velocityY > 0.35;
  }

  function finishDrawerDrag() {
    const drag = dragRef.current;
    const finalY = drag.distance;
    const velocityY = drag.velocityY;
    const dragged = drag.dragged;

    if (dragged && shouldCloseDrawer(finalY, velocityY)) {
      maybeSuppressClick();
      setSettling(true);
      setDragY(window.innerHeight);
      setClosing(true);
      timerRef.current = window.setTimeout(onClose, OVERLAY_ANIM_MS);
    } else if (dragged) {
      maybeSuppressClick();
      resetDrag();
    } else {
      resetDrag();
    }
  }

  function handlePointerDown(event) {
    if (event.pointerType === 'touch') return;
    if (event.button != null && event.button !== 0) return;
    dragRef.current = {
      active: true,
      pointerId: event.pointerId,
      pointerType: event.pointerType || 'mouse',
      pulling: true,
      startY: event.clientY,
      lastY: event.clientY,
      startScrollTop: scrollRef.current?.scrollTop || 0,
      distance: 0,
      lastMoveAt: window.performance.now(),
      velocityY: 0,
      dragged: false
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
  }

  function handlePointerMove(event) {
    const drag = dragRef.current;
    if (!drag.active || drag.pointerType === 'touch' || drag.startScrollTop > 0) return;

    const nextY = Math.max(0, event.clientY - drag.startY);
    const now = window.performance.now();
    const elapsed = Math.max(1, now - drag.lastMoveAt);
    drag.velocityY = (event.clientY - drag.lastY) / elapsed;
    drag.lastY = event.clientY;
    drag.lastMoveAt = now;
    if (nextY > 6) {
      event.preventDefault();
    }
    setPullDistance(nextY);
  }

  function finishPointerDrag(event) {
    const drag = dragRef.current;
    if (!drag.active || drag.pointerType === 'touch') return;

    event.currentTarget.releasePointerCapture?.(event.pointerId);
    finishDrawerDrag();
  }

  function cancelPointerDrag() {
    resetDrag();
  }

  function handleTouchStart(event) {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    dragRef.current = {
      active: true,
      pointerId: touch.identifier,
      pointerType: 'touch',
      pulling: false,
      startY: touch.clientY,
      lastY: touch.clientY,
      startScrollTop: scrollRef.current?.scrollTop || 0,
      distance: 0,
      lastMoveAt: window.performance.now(),
      velocityY: 0,
      dragged: false
    };
  }

  function handleTouchMove(event) {
    const drag = dragRef.current;
    if (!drag.active || drag.pointerType !== 'touch' || event.touches.length !== 1) return;

    const sheet = scrollRef.current;
    if (!sheet) return;
    const touch = event.touches[0];
    const nextY = touch.clientY;
    const movingDown = nextY > drag.lastY;
    const atTop = sheet.scrollTop <= 0;
    const now = window.performance.now();
    const elapsed = Math.max(1, now - drag.lastMoveAt);
    drag.velocityY = (nextY - drag.lastY) / elapsed;

    drag.lastY = nextY;
    drag.lastMoveAt = now;

    if (!drag.pulling) {
      if (!atTop || !movingDown) {
        drag.startY = nextY;
        drag.distance = 0;
        setDragY(0);
        return;
      }

      if (event.cancelable) event.preventDefault();
      const initialPull = Math.max(0, nextY - drag.startY);
      if (initialPull < 2) return;
      drag.pulling = true;
    }

    const nextDistance = nextY - drag.startY;
    if (nextDistance <= 0) {
      drag.pulling = false;
      drag.startY = nextY;
      drag.distance = 0;
      setDragY(0);
      return;
    }

    if (event.cancelable) event.preventDefault();
    setPullDistance(nextDistance);
  }

  function finishTouchDrag() {
    const drag = dragRef.current;
    if (!drag.active || drag.pointerType !== 'touch') return;

    finishDrawerDrag();
  }

  return (
    <div
      className={`more-sheet-backdrop ${closing ? 'closing' : ''}`}
      onClick={() => close({ animate: true })}
      role="dialog"
      aria-modal="true"
      aria-label="More navigation"
    >
      <div
        className={`more-sheet ${closing ? 'closing' : ''} ${dragY > 0 && !settling ? 'dragging' : ''} ${settling ? 'settling' : ''}`.trim()}
        style={dragY > 0 ? { transform: `translateY(${dragY}px)` } : undefined}
        onClick={(e) => e.stopPropagation()}
        onClickCapture={(event) => {
          if (!suppressNextClickRef.current) return;
          event.preventDefault();
          event.stopPropagation();
        }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={finishPointerDrag}
        onPointerCancel={cancelPointerDrag}
      >
        <div className="more-sheet-handle" aria-hidden />

        <div className="more-sheet-scroll" ref={scrollRef}>
          <div className="more-grid">
            {visibleItems.map((item) => (
              <button
                key={item.path}
                type="button"
                className={`more-card ${item.comingSoon ? 'coming-soon' : ''}`}
                onClick={() => handleItemClick(item)}
                disabled={item.comingSoon}
              >
                <AppIcon name={item.icon} className="more-card-icon" />
                <div className="more-card-label">{item.label}</div>
                <div className="more-card-description">{item.description}</div>
                {item.comingSoon && <span className="more-card-badge">Soon</span>}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
