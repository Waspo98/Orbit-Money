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
  const timerRef = useRef(null);
  const dragRef = useRef({ active: false, startY: 0, startScrollTop: 0, dragged: false });
  const suppressNextClickRef = useRef(false);
  const visibleItems = getMoreRoutes({ mhaTrackerEnabled, navigationPreferences });

  useEffect(() => {
    if (open) {
      setClosing(false);
      setDragY(0);
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

  if (!open) return null;

  function handleItemClick(item) {
    if (suppressNextClickRef.current) return;
    if (item.comingSoon) return;
    close({ animate: true });
    setTimeout(() => {
      navigate(item.path, { state: { transition: 'from-more' } });
    }, OVERLAY_ANIM_MS);
  }

  function handlePointerDown(event) {
    if (event.button != null && event.button !== 0) return;
    dragRef.current = {
      active: true,
      startY: event.clientY,
      startScrollTop: event.currentTarget.scrollTop,
      dragged: false
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
  }

  function handlePointerMove(event) {
    const drag = dragRef.current;
    if (!drag.active || drag.startScrollTop > 0) return;

    const nextY = Math.max(0, event.clientY - drag.startY);
    if (nextY > 6) {
      drag.dragged = true;
      event.preventDefault();
    }
    setDragY(Math.min(nextY, 180));
  }

  function finishPointerDrag(event) {
    const drag = dragRef.current;
    if (!drag.active) return;

    event.currentTarget.releasePointerCapture?.(event.pointerId);
    dragRef.current = { active: false, startY: 0, startScrollTop: 0, dragged: false };

    const finalY = Math.max(0, event.clientY - drag.startY);
    setDragY(0);

    if (drag.dragged && finalY > 96) {
      suppressNextClickRef.current = true;
      close({ animate: true });
      window.setTimeout(() => {
        suppressNextClickRef.current = false;
      }, OVERLAY_ANIM_MS);
    }
  }

  function cancelPointerDrag() {
    dragRef.current = { active: false, startY: 0, startScrollTop: 0, dragged: false };
    setDragY(0);
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
        className={`more-sheet ${closing ? 'closing' : ''} ${dragY > 0 ? 'dragging' : ''}`.trim()}
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
  );
}
