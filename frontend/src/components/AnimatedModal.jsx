import { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  OVERLAY_ANIM_MS,
  useBodyScrollLock,
  useOverlayBackDismiss,
  usePageBackdropBlur
} from './overlayBehavior.js';

/**
 * AnimatedModal — plays a slide-out animation before calling the parent's
 * onClose callback.
 *
 * Usage (render-prop):
 *
 *   <AnimatedModal onClose={handleClose}>
 *     {({ close }) => (
 *       <>
 *         <h3>Title</h3>
 *         <button onClick={close}>Cancel</button>
 *       </>
 *     )}
 *   </AnimatedModal>
 *
 * Children receive a `close` function that triggers the exit animation.
 * Backdrop click and Escape also use that same function.
 *
 * After ANIM_MS the parent's onClose fires and the modal unmounts.
 */

export default function AnimatedModal({
  onClose,
  size = 'md',
  animation = 'default',
  initialInteractionDelayMs = 0,
  children
}) {
  const [closing, setClosing] = useState(false);
  const [closingAnimation, setClosingAnimation] = useState('default');
  const [interactionsBlocked, setInteractionsBlocked] = useState(
    () => Math.max(0, Number(initialInteractionDelayMs) || 0) > 0
  );
  const timerRef = useRef(null);
  const interactionTimerRef = useRef(null);
  const modalRef = useRef(null);
  const interactionDelayMs = Math.max(0, Number(initialInteractionDelayMs) || 0);

  function close(options = { animate: true }) {
    if (closing) return;
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

  useEffect(() => {
    const modal = modalRef.current;
    if (!modal) return undefined;

    const frame = window.requestAnimationFrame(() => {
      modal.focus({ preventScroll: true });
    });

    return () => window.cancelAnimationFrame(frame);
  }, []);

  // Escape closes.
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (interactionTimerRef.current) clearTimeout(interactionTimerRef.current);
    };
  }, []);

  function blockInitialInteraction(event) {
    if (!interactionsBlocked) return;
    event.preventDefault();
    event.stopPropagation();
  }

  const modal = (
    <div
      className={`modal-backdrop ${closing ? 'closing' : ''}`}
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
        className={`modal ${size === 'lg' ? 'modal-lg' : ''} ${size === 'sm' ? 'modal-sm' : ''} ${animation === 'zoom' ? 'modal-zoom' : ''} ${closing ? `closing closing-${closingAnimation}` : ''}`}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        {typeof children === 'function' ? children({ close }) : children}
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
