import { useEffect, useRef, useState } from 'react';
import { OVERLAY_ANIM_MS, useBodyScrollLock } from './overlayBehavior.js';

export default function InlinePopover({ open, className = '', role, children }) {
  const [rendered, setRendered] = useState(open);
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);

  useBodyScrollLock(rendered);

  useEffect(() => {
    if (open) {
      if (timerRef.current) clearTimeout(timerRef.current);
      setRendered(true);
      setClosing(false);
      return undefined;
    }

    if (!rendered) return undefined;
    setClosing(true);
    timerRef.current = setTimeout(() => {
      setRendered(false);
      setClosing(false);
    }, OVERLAY_ANIM_MS);

    return undefined;
  }, [open, rendered]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  if (!rendered) return null;

  return (
    <div className={`inline-popover ${className} ${closing ? 'closing' : ''}`} role={role}>
      {children}
    </div>
  );
}
