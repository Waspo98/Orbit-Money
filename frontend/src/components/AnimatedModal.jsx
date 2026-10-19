import { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { hapticDetachment } from '../lib/haptics.js';
import {
  OVERLAY_ANIM_MS,
  useBodyScrollLock,
  useOverlayBackDismiss,
  usePageBackdropBlur
} from './overlayBehavior.js';

/**
 * AnimatedModal — Adaptive dual-mode modal component.
 *
 * Viewport Adaptation:
 * - Desktop / Tablet (>= 768px): Centered floating dialog, no drag handle,
 *   preserves standard sizing and focus management.
 * - Mobile (< 768px): Docked Material 3 bottom sheet with 28px top rounded corners,
 *   a 32px × 4px centered drag handle pill, rubber-band upward resistance,
 *   and swipe-down dismissal with haptic detachment.
 *
 * Usage:
 *   <AnimatedModal onClose={handleClose} title="Edit Item">
 *     {({ close }) => (
 *       <>
 *         <p>Modal content...</p>
 *         <button onClick={close}>Cancel</button>
 *       </>
 *     )}
 *   </AnimatedModal>
 */

function isMobileViewport() {
  if (typeof window === 'undefined') return false;
  return window.innerWidth < 768 || (window.matchMedia && window.matchMedia('(max-width: 767px)').matches);
}

export default function AnimatedModal({
  isOpen = true,
  onClose,
  size = 'md',
  animation = 'default',
  initialInteractionDelayMs = 0,
  title,
  className = '',
  overlayClassName = '',
  showCloseButton = false,
  children,
  ...restProps
}) {
  const [closing, setClosing] = useState(false);
  const [closingAnimation, setClosingAnimation] = useState('default');
  const [interactionsBlocked, setInteractionsBlocked] = useState(
    () => Math.max(0, Number(initialInteractionDelayMs) || 0) > 0
  );
  const [isMobile, setIsMobile] = useState(() => isMobileViewport());

  const timerRef = useRef(null);
  const interactionTimerRef = useRef(null);
  const snapTimerRef = useRef(null);
  const modalRef = useRef(null);
  const backdropRef = useRef(null);
  const handleRef = useRef(null);
  const dragState = useRef(null);
  const draggedFarRef = useRef(false);

  const interactionDelayMs = Math.max(0, Number(initialInteractionDelayMs) || 0);

  // Monitor viewport breakpoint for mobile bottom sheet vs centered desktop dialog
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const mediaQuery = window.matchMedia('(max-width: 767px)');
    const updateViewport = (event) => setIsMobile(event.matches);

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', updateViewport);
    } else {
      mediaQuery.addListener(updateViewport);
    }

    return () => {
      if (mediaQuery.removeEventListener) {
        mediaQuery.removeEventListener('change', updateViewport);
      } else {
        mediaQuery.removeListener(updateViewport);
      }
    };
  }, []);

  function close(options = { animate: true }) {
    if (closing) return;
    if (modalRef.current) {
      modalRef.current.style.transition = '';
      modalRef.current.style.transform = '';
      modalRef.current.style.opacity = '';
    }
    if (backdropRef.current) {
      backdropRef.current.style.transition = '';
      backdropRef.current.style.opacity = '';
    }
    const shouldAnimate = options.animate !== false || !!options.animation;
    if (!shouldAnimate) {
      onClose();
      return;
    }
    setClosingAnimation(options.animation || 'default');
    setClosing(true);
    timerRef.current = setTimeout(onClose, OVERLAY_ANIM_MS);
  }

  useBodyScrollLock(true);
  usePageBackdropBlur(true);
  useOverlayBackDismiss(true, () => close({ animate: true }));

  // Initial interaction delay to prevent tap-through
  useEffect(() => {
    if (interactionTimerRef.current) {
      clearTimeout(interactionTimerRef.current);
      interactionTimerRef.current = null;
    }

    if (interactionDelayMs === 0) {
      setInteractionsBlocked(false);
      return undefined;
    }

    setInteractionsBlocked(true);
    interactionTimerRef.current = setTimeout(() => {
      setInteractionsBlocked(false);
      interactionTimerRef.current = null;
    }, interactionDelayMs);

    return () => {
      if (interactionTimerRef.current) clearTimeout(interactionTimerRef.current);
    };
  }, [interactionDelayMs]);

  // Accessibility focus management
  useEffect(() => {
    const modal = modalRef.current;
    if (!modal) return undefined;

    const frame = window.requestAnimationFrame(() => {
      modal.focus({ preventScroll: true });
    });

    return () => window.cancelAnimationFrame(frame);
  }, []);

  // Escape key closes modal
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Clean up all timers on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (interactionTimerRef.current) clearTimeout(interactionTimerRef.current);
      if (snapTimerRef.current) clearTimeout(snapTimerRef.current);
    };
  }, []);

  // Mobile Bottom Sheet Touch Drag Physics (< 768px)
  useEffect(() => {
    const modal = modalRef.current;
    if (!modal || !isMobile || closing) return undefined;

    function onTouchStart(e) {
      if (e.touches.length !== 1) return;
      const touch = e.touches[0];
      const target = e.target;
      const handle = handleRef.current;
      const isHandle = !!(handle && (handle === target || handle.contains(target)));

      // Detect inner scrollable container (e.g. FilterSheet body or nested list)
      let scrollContainer = modal;
      let el = target;
      while (el && el !== modal) {
        if (el.scrollHeight > el.clientHeight) {
          const style = window.getComputedStyle(el);
          if (style.overflowY === 'auto' || style.overflowY === 'scroll') {
            scrollContainer = el;
            break;
          }
        }
        el = el.parentElement;
      }

      dragState.current = {
        isHandle,
        active: false,
        startX: touch.clientX,
        startY: touch.clientY,
        lastY: touch.clientY,
        lastTime: Date.now(),
        velocity: 0,
        currentY: 0,
        scrollContainer
      };
    }

    function onTouchMove(e) {
      const state = dragState.current;
      if (!state || e.touches.length !== 1) return;

      const touch = e.touches[0];
      const dx = touch.clientX - state.startX;
      const dy = touch.clientY - state.startY;

      const now = Date.now();
      const dt = now - state.lastTime;
      if (dt > 0) {
        state.velocity = (touch.clientY - state.lastY) / dt;
      }
      state.lastY = touch.clientY;
      state.lastTime = now;

      // Check engagement conditions
      if (!state.active) {
        if (state.isHandle) {
          // Touching drag handle engages immediately on any vertical drag
          if (Math.abs(dy) > 2) {
            state.active = true;
          }
        } else {
          // Content touch engages only if scrolled to top and pulling downward
          const scrollTop = state.scrollContainer ? state.scrollContainer.scrollTop : modal.scrollTop;
          if (scrollTop <= 0 && dy > 4 && Math.abs(dy) > Math.abs(dx)) {
            state.active = true;
          }
        }
      }

      if (state.active) {
        if (e.cancelable) e.preventDefault();
        draggedFarRef.current = true;

        let translateY = 0;
        if (dy >= 0) {
          // Downward drag: translateY follows finger
          translateY = dy;
        } else {
          // Upward drag: rubber-band logarithmic resistance
          translateY = -Math.log10(1 + Math.abs(dy) * 0.05) * 16;
        }

        state.currentY = translateY;
        modal.style.transition = 'none';
        modal.style.transform = `translateY(${translateY}px)`;

        // Synchronous backdrop opacity dimming
        if (backdropRef.current && translateY > 0) {
          const maxDistance = window.innerHeight * 0.75;
          const progress = Math.min(1, Math.max(0, translateY / maxDistance));
          backdropRef.current.style.opacity = String(Math.max(0, 1 - progress * 0.6));
        }
      }
    }

    function onTouchEnd() {
      const state = dragState.current;
      if (!state) return;

      const wasActive = state.active;
      const dy = state.currentY;
      const timeSinceMove = Date.now() - state.lastTime;
      const vy = timeSinceMove > 80 ? 0 : state.velocity;
      dragState.current = null;

      if (!wasActive) return;

      // Threshold check: dy > 90px or downward velocity vy > 0.5px/ms
      const shouldDismiss = dy > 90 || vy > 0.5;

      if (shouldDismiss) {
        // Haptic detachment on dismissal
        try {
          hapticDetachment();
        } catch {
          // Fallback gracefully
        }

        modal.style.transition = 'transform 220ms cubic-bezier(0.3, 0, 0.8, 0.15), opacity 220ms ease-out';
        modal.style.transform = 'translateY(100%)';
        modal.style.opacity = '0';

        if (backdropRef.current) {
          backdropRef.current.style.transition = 'opacity 220ms ease-out';
          backdropRef.current.style.opacity = '0';
        }

        timerRef.current = setTimeout(() => {
          onClose();
        }, 220);
      } else {
        // Under threshold: smoothly snap back to translateY(0) using --spring-spatial-default
        modal.style.transition = 'transform var(--dur-spring-default, 420ms) var(--spring-spatial-default, cubic-bezier(0.25, 1, 0.5, 1))';
        modal.style.transform = 'translateY(0)';

        if (backdropRef.current) {
          backdropRef.current.style.transition = 'opacity var(--dur-spring-default, 420ms) ease';
          backdropRef.current.style.opacity = '';
        }

        if (snapTimerRef.current) clearTimeout(snapTimerRef.current);
        snapTimerRef.current = setTimeout(() => {
          if (modal && !closing) {
            modal.style.transition = '';
            modal.style.transform = '';
          }
          if (backdropRef.current && !closing) {
            backdropRef.current.style.transition = '';
            backdropRef.current.style.opacity = '';
          }
        }, 450);
      }
    }

    modal.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('touchend', onTouchEnd, { passive: true });
    window.addEventListener('touchcancel', onTouchEnd, { passive: true });

    return () => {
      modal.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('touchend', onTouchEnd);
      window.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [isMobile, closing, onClose]);

  function blockInitialInteraction(event) {
    if (!interactionsBlocked) return;
    event.preventDefault();
    event.stopPropagation();
  }

  function handleModalClickCapture(e) {
    blockInitialInteraction(e);
    if (draggedFarRef.current) {
      draggedFarRef.current = false;
      e.stopPropagation();
      e.preventDefault();
    }
  }

  if (!isOpen) return null;

  const modal = (
    <div
      ref={backdropRef}
      className={`modal-backdrop ${closing ? 'closing' : ''} ${overlayClassName}`.trim()}
      onPointerDownCapture={blockInitialInteraction}
      onPointerUpCapture={blockInitialInteraction}
      onMouseDownCapture={blockInitialInteraction}
      onMouseUpCapture={blockInitialInteraction}
      onTouchStartCapture={blockInitialInteraction}
      onTouchEndCapture={blockInitialInteraction}
      onClickCapture={blockInitialInteraction}
      onClick={close}
    >
      <div
        ref={modalRef}
        className={`modal ${size === 'lg' ? 'modal-lg' : ''} ${size === 'sm' ? 'modal-sm' : ''} ${animation === 'zoom' ? 'modal-zoom' : ''} ${closing ? `closing closing-${closingAnimation}` : ''} ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        onClickCapture={handleModalClickCapture}
        onClick={(e) => e.stopPropagation()}
        {...restProps}
      >
        <div
          ref={handleRef}
          className="m3-sheet-handle-bar"
          aria-hidden="true"
        >
          <div className="m3-sheet-handle" />
        </div>
        {showCloseButton && (
          <button
            type="button"
            className="modal-close"
            onClick={() => close()}
            aria-label="Close dialog"
          >
            ×
          </button>
        )}
        {title && <h3>{title}</h3>}
        {typeof children === 'function' ? children({ close }) : children}
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
