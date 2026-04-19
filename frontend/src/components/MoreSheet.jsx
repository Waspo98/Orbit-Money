import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const ANIM_MS = 180;

const ITEMS = [
  { id: 'rules',    label: 'Rules',              description: 'Automate merchant names and categories', icon: '⊙', path: '/rules' },
  { id: 'settings', label: 'Settings',           description: 'Maintenance and configuration',          icon: '⚙', path: '/settings' },
  { id: 'import',   label: 'Import',             description: 'Bring in data from Rocket Money',        icon: '↑', path: '/import' },
  { id: 'mortgage', label: 'Mortgage Calculator', description: 'Amortization and extra-payment what-ifs', icon: '🏠', comingSoon: true },
  { id: 'networth', label: 'Net Worth',          description: 'Assets minus liabilities over time',     icon: '📈', comingSoon: true },
  { id: 'goals',    label: 'Goals',              description: 'Track savings targets',                  icon: '◎',  comingSoon: true },
  { id: 'credit',   label: 'Credit Score',       description: 'Track your credit health',               icon: '⬡',  comingSoon: true }
];

export default function MoreSheet({ open, onClose }) {
  const navigate = useNavigate();
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);

  // Reset closing state whenever the parent opens us anew.
  useEffect(() => {
    if (open) setClosing(false);
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
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [open]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  if (!open) return null;

  function handleItemClick(item) {
    if (item.comingSoon) return;
    close();
    setTimeout(() => navigate(item.path), ANIM_MS);
  }

  return (
    <div
      className={`more-sheet-backdrop ${closing ? 'closing' : ''}`}
      onClick={close}
      role="dialog"
      aria-modal="true"
    >
      <div
        className={`more-sheet ${closing ? 'closing' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="more-sheet-handle" aria-hidden />
        <div className="more-sheet-header">
          <h3>More</h3>
          <button type="button" className="btn-ghost" onClick={close} aria-label="Close">
            ✕
          </button>
        </div>

        <div className="more-grid">
          {ITEMS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`more-card ${item.comingSoon ? 'coming-soon' : ''}`}
              onClick={() => handleItemClick(item)}
              disabled={item.comingSoon}
            >
              <div className="more-card-icon">{item.icon}</div>
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
