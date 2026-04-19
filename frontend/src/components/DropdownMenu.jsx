import { useEffect, useRef, useState } from 'react';

/**
 * Reusable dropdown menu anchored to a trigger button.
 *
 * Usage:
 *   <DropdownMenu
 *     items={[
 *       { label: 'Edit', onClick: () => {} },
 *       { label: 'Delete', onClick: () => {}, destructive: true }
 *     ]}
 *   />
 *
 * Each item can also include a `hidden: boolean` to conditionally suppress it.
 * Items support a `divider: true` flag to render a horizontal rule.
 *
 * Closes on:
 *   - outside click
 *   - Escape key
 *   - item click (item's onClick fires, then menu closes)
 */
export default function DropdownMenu({ items, ariaLabel = 'More actions' }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return;

    function onClick(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false);
    }

    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const visibleItems = items.filter((it) => !it.hidden);

  return (
    <div className="dropdown-wrap" ref={wrapRef}>
      <button
        type="button"
        className={`dropdown-trigger ${open ? 'open' : ''}`}
        aria-label={ariaLabel}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        ⋯
      </button>

      {open && (
        <div className="dropdown-menu" role="menu">
          {visibleItems.map((item, i) =>
            item.divider ? (
              <div key={`d-${i}`} className="dropdown-divider" />
            ) : (
              <button
                key={item.label}
                type="button"
                className={`dropdown-item ${item.destructive ? 'destructive' : ''}`}
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  item.onClick?.();
                }}
              >
                {item.icon && <span>{item.icon}</span>}
                <span>{item.label}</span>
              </button>
            )
          )}
        </div>
      )}
    </div>
  );
}
