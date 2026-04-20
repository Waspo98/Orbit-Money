import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const ANIM_MS = 180;

const ITEMS = [
  { id: 'settings', label: 'Settings', description: 'Maintenance and configuration', icon: '\u2699', path: '/settings' },
  { id: 'rules', label: 'Rules', description: 'Automate merchant names and categories', icon: '\u2299', path: '/rules' },
  { id: 'housing', label: 'Housing Calculator', description: 'Selling, buying, and payment estimates', icon: '\u2302', path: '/housing-calculator' },
  { id: 'networth', label: 'Net Worth', description: 'Assets minus liabilities over time', icon: '$', path: '/net-worth' },
  { id: 'mha', label: 'MHA Tracker', description: 'Track housing allowance transactions', icon: 'H', path: '/mha-tracker', feature: 'mha' },
  { id: 'goals', label: 'Goals', description: 'Track savings targets', icon: '\u25ce', comingSoon: true },
  { id: 'categories', label: 'Category Manager', description: 'Organize spending categories', icon: '#', path: '/categories' }
];

export default function MoreSheet({ open, onClose, mhaTrackerEnabled = false }) {
  const navigate = useNavigate();
  const [closing, setClosing] = useState(false);
  const timerRef = useRef(null);
  const visibleItems = ITEMS.filter((item) => item.feature !== 'mha' || mhaTrackerEnabled);

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
            {'\u2715'}
          </button>
        </div>

        <div className="more-grid">
          {visibleItems.map((item) => (
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
