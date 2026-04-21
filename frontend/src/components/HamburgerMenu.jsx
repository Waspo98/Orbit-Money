import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const ANIM_MS = 180;

export default function HamburgerMenu({
  open,
  onClose,
  themeMode,
  onThemeChange,
  onLogout
}) {
  const navigate = useNavigate();
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);

  useEffect(() => {
    if (open) setClosing(false);
  }, [open]);

  // Lock body scroll while menu is open.
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [open]);

  function close() {
    if (closing) return;
    setClosing(true);
    timerRef.current = setTimeout(onClose, ANIM_MS);
  }

  useEffect(() => {
    if (!open) return;
    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  if (!open) return null;

  function go(path) {
    close();
    setTimeout(() => navigate(path), ANIM_MS);
  }

  const themeOptions = [
    { value: 'light',  emoji: '☀️', label: 'Light' },
    { value: 'system', emoji: '💻', label: 'System' },
    { value: 'dark',   emoji: '🌙', label: 'Dark' }
  ];

  return (
    <>
      <div className={`menu-backdrop ${closing ? 'closing' : ''}`} onClick={close} />
      <div className={`menu-panel ${closing ? 'closing' : ''}`} role="menu">
        <button
          type="button"
          className="menu-close-button"
          onClick={close}
          aria-label="Close menu"
        >
          ☰
        </button>
        <button type="button" className="menu-item" onClick={() => go('/settings')}>
          <span className="menu-item-icon">↑</span>
          <span>Settings</span>
        </button>
        <button type="button" className="menu-item" onClick={() => go('/settings')} hidden>
          <span className="menu-item-icon">⚙</span>
          <span>Settings</span>
        </button>

        <div className="menu-divider" />

        <div className="menu-section-label">Theme</div>
        <div className="theme-selector">
          {themeOptions.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={`theme-option ${themeMode === opt.value ? 'active' : ''}`}
              onClick={() => onThemeChange(opt.value)}
            >
              <span className="theme-option-emoji">{opt.emoji}</span>
              <span>{opt.label}</span>
            </button>
          ))}
        </div>

        <div className="menu-divider" />

        <button
          type="button"
          className="menu-item destructive"
          onClick={() => {
            close();
            setTimeout(onLogout, ANIM_MS);
          }}
        >
          <span className="menu-item-icon">→</span>
          <span>Sign out</span>
        </button>
      </div>
    </>
  );
}
