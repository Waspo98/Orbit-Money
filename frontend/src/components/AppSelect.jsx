import { useEffect, useRef, useState } from 'react';

export default function AppSelect({
  value,
  options,
  onChange,
  placeholder = 'Choose',
  className = '',
  disabled = false,
  ariaLabel
}) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const rootRef = useRef(null);
  const selected = options.find((option) => String(option.value) === String(value));

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
      if (!rootRef.current?.contains(event.target)) {
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
        <div className={`app-select-menu ${closing ? 'closing' : ''}`} role="listbox">
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
