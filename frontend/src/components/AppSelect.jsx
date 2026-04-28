import { useEffect, useLayoutEffect, useRef, useState } from 'react';

export default function AppSelect({
  value,
  options,
  onChange,
  placeholder = 'Choose',
  className = '',
  disabled = false,
  ariaLabel,
  menuPlacement = 'default'
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [menuPosition, setMenuPosition] = useState(null);
  const rootRef = useRef(null);
  const menuRef = useRef(null);
  const selected = options.find((option) => String(option.value) === String(value));
  const usePageCenteredMenu = menuPlacement === 'page-center';

  function closeMenu() {
    if (!open || closing) return;
    setClosing(true);
    window.setTimeout(() => {
      setOpen(false);
      setClosing(false);
    }, 140);
  }

  useEffect(() => {
    if (!open) return undefined;

    function handlePointerDown(event) {
      if (
        !rootRef.current?.contains(event.target) &&
        !menuRef.current?.contains(event.target)
      ) {
        closeMenu();
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') closeMenu();
    }

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, closing]);

  useLayoutEffect(() => {
    if (!open || !usePageCenteredMenu) return undefined;

    function positionMenu() {
      const rect = rootRef.current?.getBoundingClientRect();
      if (!rect) return;

      const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
      const gutter = 20;
      const width = Math.min(320, viewportWidth - gutter * 2);
      const top = Math.min(rect.bottom + 8, viewportHeight - 96);

      setMenuPosition({
        top: Math.max(gutter, top),
        left: Math.max(gutter, (viewportWidth - width) / 2),
        width,
        maxHeight: Math.max(120, viewportHeight - top - gutter)
      });
    }

    positionMenu();
    window.addEventListener('resize', positionMenu);
    window.addEventListener('scroll', positionMenu, true);
    return () => {
      window.removeEventListener('resize', positionMenu);
      window.removeEventListener('scroll', positionMenu, true);
    };
  }, [open, usePageCenteredMenu]);

  const menuStyle = usePageCenteredMenu && menuPosition
    ? {
      position: 'fixed',
      top: `${menuPosition.top}px`,
      left: `${menuPosition.left}px`,
      right: 'auto',
      width: `${menuPosition.width}px`,
      maxHeight: `${menuPosition.maxHeight}px`
    }
    : undefined;

  return (
    <div ref={rootRef} className={`app-select ${className}`}>
      <button
        type="button"
        className="app-select-trigger"
        onClick={() => (open ? closeMenu() : setOpen(true))}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
      >
        <span>{selected?.label || placeholder}</span>
        <span className="app-select-caret" aria-hidden="true">v</span>
      </button>

      {open && (
        <div
          ref={menuRef}
          className={`app-select-menu ${usePageCenteredMenu ? 'app-select-menu-page-centered' : ''} ${closing ? 'closing' : ''}`.trim()}
          style={menuStyle}
          role="listbox"
        >
          {options.map((option) => (
            <button
              type="button"
              key={option.value}
              className={`app-select-option ${
                String(option.value) === String(value) ? 'selected' : ''
              }`}
              role="option"
              aria-selected={String(option.value) === String(value)}
              onClick={() => {
                onChange(option.value);
                closeMenu();
              }}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
