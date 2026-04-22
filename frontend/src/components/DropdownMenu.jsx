import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { OVERLAY_ANIM_MS } from './overlayBehavior.js';

/**
 * Reusable dropdown menu anchored to a trigger button.
 *
 * The menu is portaled and viewport-clamped so page/card overflow rules cannot
 * clip it, and every close path uses the same zoom-out animation.
 */
export default function DropdownMenu({
  items,
  ariaLabel = 'More actions',
  triggerClassName = 'dropdown-trigger',
  menuClassName = '',
  renderTrigger
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [menuStyle, setMenuStyle] = useState(null);
  const wrapRef = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const timerRef = useRef(null);

  const visibleItems = items.filter((it) => !it.hidden);

  function close(options = {}) {
    if (!open || closing) return;
    if (!options.animate) {
      setOpen(false);
      setMenuStyle(null);
      return;
    }
    setClosing(true);
    timerRef.current = setTimeout(() => {
      setOpen(false);
      setClosing(false);
      setMenuStyle(null);
      options.afterClose?.();
    }, OVERLAY_ANIM_MS);
  }

  useEffect(() => {
    if (!open) return undefined;

    function onClick(e) {
      const insideTrigger = wrapRef.current && wrapRef.current.contains(e.target);
      const insideMenu = menuRef.current && menuRef.current.contains(e.target);
      if (!insideTrigger && !insideMenu) {
        close({ animate: true });
      }
    }

    function onKey(e) {
      if (e.key === 'Escape') close({ animate: true });
    }

    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, closing]);

  useLayoutEffect(() => {
    if (!open) return undefined;

    function updatePosition() {
      const trigger = triggerRef.current;
      const menu = menuRef.current;
      if (!trigger || !menu) return;

      const gap = 6;
      const margin = 8;
      const triggerRect = trigger.getBoundingClientRect();
      const menuRect = menu.getBoundingClientRect();
      const menuWidth = menuRect.width || 160;
      const menuHeight = menuRect.height || 40;
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;

      let left = triggerRect.right - menuWidth;
      left = Math.max(margin, Math.min(left, viewportWidth - menuWidth - margin));

      let top = triggerRect.bottom + gap;
      let originY = 'top';
      if (top + menuHeight > viewportHeight - margin && triggerRect.top - gap - menuHeight >= margin) {
        top = triggerRect.top - gap - menuHeight;
        originY = 'bottom';
      } else {
        top = Math.min(top, viewportHeight - menuHeight - margin);
      }

      setMenuStyle({
        position: 'fixed',
        top: `${Math.max(margin, top)}px`,
        left: `${left}px`,
        '--dropdown-origin': `${originY} right`
      });
    }

    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, visibleItems.length]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  return (
    <div className="dropdown-wrap" ref={wrapRef}>
      <button
        ref={triggerRef}
        type="button"
        className={`${triggerClassName} ${open ? 'open' : ''}`}
        aria-label={ariaLabel}
        aria-expanded={open}
        onClick={(e) => {
          e.stopPropagation();
          if (open) {
            close({ animate: true });
          } else {
            setOpen(true);
          }
        }}
      >
        {renderTrigger ? renderTrigger({ open }) : '⋯'}
      </button>

      {open && createPortal(
        <div
          ref={menuRef}
          className={`dropdown-menu ${menuClassName} ${closing ? 'closing' : ''}`}
          role="menu"
          style={menuStyle || undefined}
          onClick={(e) => e.stopPropagation()}
        >
          {visibleItems.map((item, i) =>
            item.divider ? (
              <div key={`d-${i}`} className="dropdown-divider" />
            ) : (
              <button
                key={item.label}
                type="button"
                className={`dropdown-item ${item.destructive ? 'destructive' : ''}`}
                role="menuitem"
                disabled={item.disabled}
                onClick={() => {
                  if (item.disabled) return;
                  close({
                    animate: true,
                    afterClose: () => item.onClick?.()
                  });
                }}
              >
                {item.icon && <span>{item.icon}</span>}
                <span>{item.label}</span>
              </button>
            )
          )}
        </div>,
        document.body
      )}
    </div>
  );
}
