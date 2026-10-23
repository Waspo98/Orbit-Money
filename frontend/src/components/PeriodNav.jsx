import AppSelect from './AppSelect.jsx';
import { triggerHaptic } from '../lib/haptics.js';

export default function PeriodNav({
  className = '',
  variant,
  size,
  value,
  options,
  onPrev,
  onNext,
  onJump,
  canGoForward = true,
  previousLabel = 'Previous period',
  nextLabel = 'Next period',
  jumpLabel = 'Jump to period',
  menuPlacement = 'default'
}) {
  const isConnected = variant === 'hero' || variant === 'connected';
  const classes = [
    'period-nav',
    variant ? `period-nav-${variant}` : '',
    isConnected ? 'period-nav-connected m3-period-nav' : '',
    size ? `period-nav-${size}` : '',
    className
  ].filter(Boolean).join(' ');

  return (
    <div className={classes}>
      <button
        type="button"
        className="btn-icon period-nav-arrow period-nav-prev"
        onClick={(e) => {
          triggerHaptic('selection');
          onPrev?.(e);
        }}
        aria-label={previousLabel}
      >
        <span className="material-symbols-rounded" aria-hidden="true">chevron_left</span>
      </button>

      <div className="period-nav-label-wrap">
        <AppSelect
          className="period-nav-select"
          value={value}
          options={options}
          onChange={(val) => {
            triggerHaptic('selection');
            onJump?.(val);
          }}
          ariaLabel={jumpLabel}
          menuPlacement={menuPlacement}
        />
      </div>

      <button
        type="button"
        className="btn-icon period-nav-arrow period-nav-next"
        onClick={(e) => {
          if (!canGoForward) return;
          triggerHaptic('selection');
          onNext?.(e);
        }}
        disabled={!canGoForward}
        aria-label={nextLabel}
      >
        <span className="material-symbols-rounded" aria-hidden="true">chevron_right</span>
      </button>
    </div>
  );
}
