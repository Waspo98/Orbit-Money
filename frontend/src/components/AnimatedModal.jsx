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
    const scrollY = window.scrollY;
    const prev = {
      overflow: document.body.style.overflow,
      position: document.body.style.position,
      top: document.body.style.top,
      left: document.body.style.left,
      right: document.body.style.right,
      width: document.body.style.width
    };
    document.body.style.overflow = 'hidden';
    document.body.style.position = 'fixed';
    document.body.style.top = `-${scrollY}px`;
    document.body.style.left = '0';
    document.body.style.right = '0';
    document.body.style.width = '100%';
    return () => {
      document.body.style.overflow = prev.overflow;
      document.body.style.position = prev.position;
      document.body.style.top = prev.top;
      document.body.style.left = prev.left;
      document.body.style.right = prev.right;
      document.body.style.width = prev.width;
      window.scrollTo(0, scrollY);
    };
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
