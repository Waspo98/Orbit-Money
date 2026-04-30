import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon.jsx';
import {
  OVERLAY_ANIM_MS,
  useBodyScrollLock,
  useOverlayBackDismiss
} from './overlayBehavior.js';
import { getMoreRoutes } from '../navigation.js';

const DRAG_START_PX = 12;
const DRAG_CLOSE_PX = 92;
const DRAG_CLOSE_VELOCITY = 0.55;

function createGestureState() {
  return {
    active: false,
    mode: 'idle',
    pointerType: null,
    pointerId: null,
    startedAtTop: false,
    startY: 0,
    lastY: 0,
    lastMoveAt: 0,
    distance: 0,
    velocityY: 0
  };
}

export default function MoreSheet({
  open,
  onClose,
  mhaTrackerEnabled = false,
  navigationPreferences
}) {
  const navigate = useNavigate();
  const [drawerState, setDrawerState] = useState('opening');
  const timerRef = useRef(null);
  const enterFrameRef = useRef(null);
  const dragFrameRef = useRef(null);
  const sheetRef = useRef(null);
  const scrollRef = useRef(null);
  const scrollLockRef = useRef({ locked: false, overflowY: '' });
  const gestureRef = useRef(createGestureState());
  const pendingTransformRef = useRef(0);
  const pendingDraggingRef = useRef(false);
  const suppressNextClickRef = useRef(false);
  const visibleItems = getMoreRoutes({ mhaTrackerEnabled, navigationPreferences });

  useEffect(() => {
    if (!open) return undefined;

    clearCloseTimer();
    cancelDragFrame();
    gestureRef.current = createGestureState();
    suppressNextClickRef.current = false;
    unlockInternalScroll();
    sheetRef.current?.classList.remove('dragging');
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
      cancelDragFrame();
      unlockInternalScroll();
      gestureRef.current = createGestureState();
    };
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;

    function onKey(event) {
      if (event.key === 'Escape') close({ animate: true });
    }

    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useBodyScrollLock(open);
  useOverlayBackDismiss(open, () => close({ animate: true }));

  useEffect(() => (
    () => {
      clearCloseTimer();
      cancelDragFrame();
      unlockInternalScroll();
    }
  ), []);

  useEffect(() => {
    if (!open || !scrollRef.current) return undefined;
    const scrollNode = scrollRef.current;

    function onTouchStart(event) {
      if (event.touches.length !== 1) return;
      startTouchGesture(event.touches[0]);
    }

    function onTouchMove(event) {
      moveTouchGesture(event);
    }

    function onTouchEnd() {
      finishGesture();
    }

    scrollNode.addEventListener('touchstart', onTouchStart, { passive: true });
    scrollNode.addEventListener('touchmove', onTouchMove, { passive: false });
    scrollNode.addEventListener('touchend', onTouchEnd, { passive: true });
    scrollNode.addEventListener('touchcancel', onTouchEnd, { passive: true });

    return () => {
      scrollNode.removeEventListener('touchstart', onTouchStart);
      scrollNode.removeEventListener('touchmove', onTouchMove);
      scrollNode.removeEventListener('touchend', onTouchEnd);
      scrollNode.removeEventListener('touchcancel', onTouchEnd);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function clearCloseTimer() {
    if (!timerRef.current) return;
    window.clearTimeout(timerRef.current);
    timerRef.current = null;
  }

  function cancelDragFrame() {
    if (!dragFrameRef.current) return;
    window.cancelAnimationFrame(dragFrameRef.current);
    dragFrameRef.current = null;
  }

  function lockInternalScroll() {
    const scrollNode = scrollRef.current;
    if (!scrollNode || scrollLockRef.current.locked) return;

    scrollLockRef.current = {
      locked: true,
      overflowY: scrollNode.style.overflowY
    };
    scrollNode.style.overflowY = 'hidden';
  }

  function unlockInternalScroll() {
    if (!scrollLockRef.current.locked) return;

    const scrollNode = scrollRef.current;
    if (scrollNode) {
      scrollNode.style.overflowY = scrollLockRef.current.overflowY;
    }

    scrollLockRef.current = { locked: false, overflowY: '' };
  }

  function setSheetTransform(value, { dragging }) {
    const sheet = sheetRef.current;
    if (!sheet) return;
    sheet.classList.toggle('dragging', Boolean(dragging && value > 0));
    sheet.style.transform = `translateY(${value}px)`;
  }

  function scheduleSheetTransform(value, options) {
    pendingTransformRef.current = value;
    pendingDraggingRef.current = Boolean(options.dragging);
    if (dragFrameRef.current) return;

    dragFrameRef.current = window.requestAnimationFrame(() => {
      dragFrameRef.current = null;
      setSheetTransform(pendingTransformRef.current, {
        dragging: pendingDraggingRef.current
      });
    });
  }

  function close(options = { animate: true }) {
    if (drawerState === 'closing') return;

    if (options?.animate === false) {
      unlockInternalScroll();
      onClose();
      return;
    }

    gestureRef.current = createGestureState();
    cancelDragFrame();
    unlockInternalScroll();
    setSheetTransform(0, { dragging: false });
    setDrawerState('closing');
    timerRef.current = window.setTimeout(onClose, OVERLAY_ANIM_MS);
  }

  function closeFromGesture() {
    maybeSuppressClick();
    gestureRef.current = createGestureState();
    cancelDragFrame();
    unlockInternalScroll();
    setDrawerState('closing');
    setSheetTransform(window.innerHeight, { dragging: false });
    timerRef.current = window.setTimeout(onClose, OVERLAY_ANIM_MS);
  }

  function snapBackFromGesture() {
    maybeSuppressClick();
    gestureRef.current = createGestureState();
    cancelDragFrame();
    unlockInternalScroll();
    setSheetTransform(0, { dragging: false });
  }

  function maybeSuppressClick() {
    suppressNextClickRef.current = true;
    window.setTimeout(() => {
      suppressNextClickRef.current = false;
    }, OVERLAY_ANIM_MS);
  }

  function shouldCloseDrawer(distance, velocityY) {
    return distance > DRAG_CLOSE_PX || velocityY > DRAG_CLOSE_VELOCITY;
  }

  function handleItemClick(item) {
    if (suppressNextClickRef.current || item.comingSoon) return;
    close({ animate: true });
    window.setTimeout(() => {
      navigate(item.path, { state: { transition: 'from-more' } });
    }, OVERLAY_ANIM_MS + 40);
  }

  function startTouchGesture(touch) {
    const scrollTop = scrollRef.current?.scrollTop || 0;
    gestureRef.current = {
      active: true,
      mode: 'pending',
      pointerType: 'touch',
      pointerId: touch.identifier,
      startedAtTop: scrollTop <= 0,
      startY: touch.clientY,
      lastY: touch.clientY,
      lastMoveAt: window.performance.now(),
      distance: 0,
      velocityY: 0
    };
  }

  function moveTouchGesture(event) {
    const gesture = gestureRef.current;
    if (!gesture.active || gesture.pointerType !== 'touch' || event.touches.length !== 1) {
      return;
    }

    const touch = event.touches[0];
    const nextY = touch.clientY;
    const totalY = nextY - gesture.startY;
    const now = window.performance.now();
    const elapsed = Math.max(1, now - gesture.lastMoveAt);
    gesture.velocityY = (nextY - gesture.lastY) / elapsed;
    gesture.lastY = nextY;
    gesture.lastMoveAt = now;

    if (gesture.mode === 'scroll') return;

    if (gesture.mode === 'pending') {
      if (!gesture.startedAtTop || totalY < 0) {
        gesture.mode = 'scroll';
        return;
      }

      if (totalY < DRAG_START_PX) return;
      gesture.mode = 'sheet';
      lockInternalScroll();
    }

    if (gesture.mode !== 'sheet') return;
    if (event.cancelable) event.preventDefault();

    const distance = Math.max(0, totalY);
    gesture.distance = distance;
    scheduleSheetTransform(distance, { dragging: true });
  }

  function startPointerGesture(event) {
    if (event.pointerType === 'touch') return;
    if (event.button != null && event.button !== 0) return;

    gestureRef.current = {
      active: true,
      mode: 'sheet',
      pointerType: event.pointerType || 'mouse',
      pointerId: event.pointerId,
      startedAtTop: true,
      startY: event.clientY,
      lastY: event.clientY,
      lastMoveAt: window.performance.now(),
      distance: 0,
      velocityY: 0
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
    lockInternalScroll();
  }

  function movePointerGesture(event) {
    const gesture = gestureRef.current;
    if (!gesture.active || gesture.pointerType === 'touch' || gesture.mode !== 'sheet') return;

    const nextY = event.clientY;
    const distance = Math.max(0, nextY - gesture.startY);
    const now = window.performance.now();
    const elapsed = Math.max(1, now - gesture.lastMoveAt);
    gesture.velocityY = (nextY - gesture.lastY) / elapsed;
    gesture.lastY = nextY;
    gesture.lastMoveAt = now;
    gesture.distance = distance;

    if (distance > 6) event.preventDefault();
    scheduleSheetTransform(distance, { dragging: true });
  }

  function finishPointerGesture(event) {
    const gesture = gestureRef.current;
    if (!gesture.active || gesture.pointerType === 'touch') return;

    event.currentTarget.releasePointerCapture?.(event.pointerId);
    finishGesture();
  }

  function finishGesture() {
    const gesture = gestureRef.current;
    if (!gesture.active) return;

    if (
      gesture.mode === 'sheet' &&
      shouldCloseDrawer(gesture.distance, gesture.velocityY)
    ) {
      closeFromGesture();
      return;
    }

    if (gesture.mode === 'sheet' && gesture.distance > 0) {
      snapBackFromGesture();
      return;
    }

    gestureRef.current = createGestureState();
    unlockInternalScroll();
  }

  if (!open) return null;

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
        className={`more-sheet ${isClosing ? 'closing' : ''}`.trim()}
        style={{ transform: `translateY(${sheetOffset})` }}
        onClick={(event) => event.stopPropagation()}
        onClickCapture={(event) => {
          if (!suppressNextClickRef.current) return;
          event.preventDefault();
          event.stopPropagation();
        }}
      >
        <div
          className="more-sheet-handle"
          aria-hidden
          onPointerDown={startPointerGesture}
          onPointerMove={movePointerGesture}
          onPointerUp={finishPointerGesture}
          onPointerCancel={finishGesture}
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
