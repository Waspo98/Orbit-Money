import { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  OVERLAY_ANIM_MS,
  useBodyScrollLock,
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

export default function AnimatedModal({ onClose, size = 'md', animation = 'default', children }) {
  const [closing, setClosing] = useState(false);
  const [closingAnimation, setClosingAnimation] = useState('default');
  const timerRef = useRef(null);

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
    };
  }, []);

  const modal = (
    <div
      className={`modal-backdrop ${closing ? 'closing' : ''}`}
      onClick={close}
    >
      <div
        className={`modal ${size === 'lg' ? 'modal-lg' : ''} ${size === 'sm' ? 'modal-sm' : ''} ${animation === 'zoom' ? 'modal-zoom' : ''} ${closing ? `closing closing-${closingAnimation}` : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {typeof children === 'function' ? children({ close }) : children}
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
