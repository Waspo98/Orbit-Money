import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import { OVERLAY_ANIM_MS, useBodyScrollLock } from './overlayBehavior.js';
import { getMoreRoutes } from '../navigation.js';

const TOUCH_PULL_START_PX = 8;

export default function MoreSheet({
  open,
  onClose,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const navigate = useNavigate();
  const [drawerState, setDrawerState] = useState('opening');
  const [settling, setSettling] = useState(false);
  const timerRef = useRef(null);
  const frameRef = useRef(null);
  const dragFrameRef = useRef(null);
  const dragYRef = useRef(0);
  const pendingDragYRef = useRef(0);
  const sheetRef = useRef(null);
  const scrollRef = useRef(null);
  const dragRef = useRef({
    active: false,
    pointerId: null,
    pointerType: null,
    pulling: false,
    startedAtTop: false,
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
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      setDrawerState('opening');
      setSheetOffsetImmediate(0, { dragging: false });
      setSettling(false);
      suppressNextClickRef.current = false;
      frameRef.current = window.requestAnimationFrame(() => {
        setDrawerState('open');
      });
    }

    return () => {
      if (frameRef.current) {
        window.cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, [open]);

  function close(options = { animate: true }) {
    if (drawerState === 'closing') return;
    const shouldAnimate = options?.animate !== false;
    if (!shouldAnimate) {
      onClose();
      return;
    }
    setSettling(false);
    setSheetOffsetImmediate(0, { dragging: false });
    setDrawerState('closing');
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
      if (dragFrameRef.current) {
        window.cancelAnimationFrame(dragFrameRef.current);
        dragFrameRef.current = null;
      }
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
      startedAtTop: false,
      startY: 0,
      lastY: 0,
      startScrollTop: 0,
      distance: 0,
      lastMoveAt: 0,
      velocityY: 0,
      dragged: false
    };
    setSettling(false);
    setSheetOffsetImmediate(0, { dragging: false });
  }

  function applySheetOffset(value, options = { dragging: false }) {
    const sheet = sheetRef.current;
    if (!sheet) return;
    sheet.style.transform = `translateY(${value}px)`;
    sheet.classList.toggle('dragging', Boolean(options.dragging && value > 0));
  }

  function setSheetOffsetImmediate(value, options = { dragging: false }) {
    if (dragFrameRef.current) {
      window.cancelAnimationFrame(dragFrameRef.current);
      dragFrameRef.current = null;
    }
    dragYRef.current = value;
    pendingDragYRef.current = value;
    applySheetOffset(value, options);
  }

  function setSheetOffsetOnFrame(value, options = { dragging: false }) {
    if (Math.abs(value - dragYRef.current) < 0.5) return;

    dragYRef.current = value;
    pendingDragYRef.current = value;
    if (dragFrameRef.current) return;

    dragFrameRef.current = window.requestAnimationFrame(() => {
      dragFrameRef.current = null;
      applySheetOffset(pendingDragYRef.current, options);
    });
  }

  function resetPullDistance() {
    dragRef.current.distance = 0;
    if (dragYRef.current !== 0) {
      setSheetOffsetImmediate(0, { dragging: false });
    }
  }

  function setPullDistance(value) {
    const distance = Math.min(Math.max(value, 0), window.innerHeight);
    dragRef.current.distance = distance;
    if (distance > 6) dragRef.current.dragged = true;
    setSheetOffsetOnFrame(distance, { dragging: true });
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
      setSheetOffsetImmediate(window.innerHeight, { dragging: false });
      setDrawerState('closing');
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
      startedAtTop: true,
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
      startedAtTop: (scrollRef.current?.scrollTop || 0) <= 0,
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

    if (!drag.startedAtTop) {
      drag.startY = nextY;
      resetPullDistance();
      return;
    }

    if (!drag.pulling) {
      if (!atTop || !movingDown) {
        drag.startY = nextY;
        resetPullDistance();
        return;
      }

      const initialPull = Math.max(0, nextY - drag.startY);
      if (initialPull < TOUCH_PULL_START_PX) return;
      if (event.cancelable) event.preventDefault();
      drag.pulling = true;
    }

    const nextDistance = nextY - drag.startY;
    if (nextDistance <= 0) {
      drag.pulling = false;
      drag.startY = nextY;
      resetPullDistance();
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

  const isClosing = drawerState === 'closing';
  const sheetOffset = drawerState === 'open' ? '0px' : '100%';

  return (
    <div
      className={`more-sheet-backdrop ${drawerState === 'open' ? 'open' : ''} ${isClosing ? 'closing' : ''}`.trim()}
      onClick={() => close({ animate: true })}
      role="dialog"
      aria-modal="true"
      aria-label="More navigation"
    >
      <div
        ref={sheetRef}
        className={`more-sheet ${isClosing ? 'closing' : ''} ${settling ? 'settling' : ''}`.trim()}
        style={{ transform: `translateY(${sheetOffset})` }}
        onClick={(e) => e.stopPropagation()}
        onClickCapture={(event) => {
          if (!suppressNextClickRef.current) return;
          event.preventDefault();
          event.stopPropagation();
        }}
      >
        <div
          className="more-sheet-handle"
          aria-hidden
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={finishPointerDrag}
          onPointerCancel={cancelPointerDrag}
        />

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
