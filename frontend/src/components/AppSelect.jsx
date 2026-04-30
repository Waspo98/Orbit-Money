import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export default function AppSelect({
  value,
  options,
  onChange,
  placeholder = 'Choose',
  className = '',
  disabled = false,
  ariaLabel,
  menuPlacement = 'default',
  triggerLabel
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [menuPosition, setMenuPosition] = useState(null);
  const rootRef = useRef(null);
  const menuRef = useRef(null);
  const closeTimerRef = useRef(null);
  const selected = options.find((option) => String(option.value) === String(value));
  const usePageCenteredMenu = menuPlacement === 'page-center';
  const contextualMenuClasses = className
    .split(/\s+/)
    .filter(Boolean)
    .map((name) => `app-select-menu-${name}`)
    .join(' ');
  const menuClassNames = [
    'app-select-menu',
    usePageCenteredMenu ? 'app-select-menu-page-centered' : '',
    closing ? 'closing' : '',
    contextualMenuClasses
  ].filter(Boolean).join(' ');

  function openMenu() {
    setMenuPosition(null);
    setClosing(false);
    setOpen(true);
  }

  function closeMenu() {
    if (!open || closing) return;
    setClosing(true);
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    closeTimerRef.current = window.setTimeout(() => {
      setOpen(false);
      setClosing(false);
      closeTimerRef.current = null;
    }, 140);
  }

  useEffect(() => (
    () => {
      if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    }
  ), []);

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
    if (!open) return undefined;

    function positionMenu() {
      const rect = rootRef.current?.getBoundingClientRect();
      const menuRect = menuRef.current?.getBoundingClientRect();
      if (!rect || !menuRect) return;

      const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight;

      if (usePageCenteredMenu) {
        const gutter = 20;
        const width = Math.min(320, viewportWidth - gutter * 2);
        const top = Math.min(rect.bottom + 8, viewportHeight - 96);

        setMenuPosition({
          top: Math.max(gutter, top),
          left: Math.max(gutter, (viewportWidth - width) / 2),
          width,
          maxHeight: Math.max(120, viewportHeight - top - gutter),
          transformOrigin: 'top center'
        });
        return;
      }

      const gutter = 8;
      const gap = 6;
      const width = Math.min(
        viewportWidth - gutter * 2,
        Math.max(rect.width, menuRect.width || rect.width)
      );
      const menuHeight = menuRect.height || 40;
      const spaceBelow = viewportHeight - rect.bottom - gap - gutter;
      const spaceAbove = rect.top - gap - gutter;
      const openAbove = spaceBelow < Math.min(menuHeight, 180) && spaceAbove > spaceBelow;
      const maxHeight = Math.max(96, openAbove ? spaceAbove : spaceBelow);
      const top = openAbove
        ? Math.max(gutter, rect.top - gap - Math.min(menuHeight, maxHeight))
        : Math.min(rect.bottom + gap, viewportHeight - gutter - Math.min(menuHeight, maxHeight));
      const left = Math.max(gutter, Math.min(rect.left, viewportWidth - width - gutter));

      setMenuPosition({
        top,
        left,
        width,
        maxHeight,
        transformOrigin: openAbove ? 'bottom left' : 'top left'
      });
    }

    positionMenu();
    window.addEventListener('resize', positionMenu);
    window.addEventListener('scroll', positionMenu, true);
    return () => {
      window.removeEventListener('resize', positionMenu);
      window.removeEventListener('scroll', positionMenu, true);
    };
  }, [open, usePageCenteredMenu, options.length]);

  const menuStyle = menuPosition
    ? {
      position: 'fixed',
      top: `${menuPosition.top}px`,
      left: `${menuPosition.left}px`,
      right: 'auto',
      width: `${menuPosition.width}px`,
      maxHeight: `${menuPosition.maxHeight}px`,
      '--app-select-origin': menuPosition.transformOrigin
    }
    : open
      ? {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 'auto',
        visibility: 'hidden'
      }
    : undefined;

  const menu = open ? (
    <div
      ref={menuRef}
      className={menuClassNames}
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
  ) : null;

  return (
    <div ref={rootRef} className={`app-select ${className}`}>
      <button
        type="button"
        className="app-select-trigger"
        onClick={() => (open ? closeMenu() : openMenu())}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
      >
        <span>{triggerLabel || selected?.label || placeholder}</span>
        <span className="app-select-caret" aria-hidden="true">v</span>
      </button>

      {menu && typeof document !== 'undefined'
        ? createPortal(menu, document.body)
        : menu}
    </div>
  );
}
