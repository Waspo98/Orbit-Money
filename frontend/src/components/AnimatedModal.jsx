import { useEffect, useState, useRef } from 'react';

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

const ANIM_MS = 180;

export default function AnimatedModal({ onClose, size = 'md', children }) {
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);

  function close() {
    if (closing) return;
    setClosing(true);
    timerRef.current = setTimeout(onClose, ANIM_MS);
  }

  // Lock body scroll while modal is open.
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
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
    };
  }, []);

  return (
    <div
      className={`modal-backdrop ${closing ? 'closing' : ''}`}
      onClick={close}
    >
      <div
        className={`modal ${size === 'lg' ? 'modal-lg' : ''} ${size === 'sm' ? 'modal-sm' : ''} ${closing ? 'closing' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {typeof children === 'function' ? children({ close }) : children}
      </div>
    </div>
  );
}
