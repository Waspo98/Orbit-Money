import { useEffect } from 'react';

let lockCount = 0;
let lockSnapshot = null;

export const OVERLAY_ANIM_MS = 180;

export function useBodyScrollLock(active) {
  useEffect(() => {
    if (!active) return undefined;

    if (lockCount === 0) {
      const scrollY = window.scrollY;
      lockSnapshot = {
        scrollY,
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
    }

    lockCount += 1;

    return () => {
      lockCount = Math.max(0, lockCount - 1);
      if (lockCount > 0 || !lockSnapshot) return;

      const snapshot = lockSnapshot;
      lockSnapshot = null;
      document.body.style.overflow = snapshot.overflow;
      document.body.style.position = snapshot.position;
      document.body.style.top = snapshot.top;
      document.body.style.left = snapshot.left;
      document.body.style.right = snapshot.right;
      document.body.style.width = snapshot.width;
      window.scrollTo(0, snapshot.scrollY);
    };
  }, [active]);
}
