import AppSelect from './AppSelect.jsx';

export default function PeriodNav({
  className = '',
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
  const classes = ['month-nav', className].filter(Boolean).join(' ');

  return (
    <div className={classes}>
      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onPrev}
        aria-label={previousLabel}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="M14.5 6.5 9 12l5.5 5.5" />
        </svg>
      </button>

      <div className="month-nav-label-wrap">
        <AppSelect
          className="month-nav-select"
          value={value}
          options={options}
          onChange={onJump}
          ariaLabel={jumpLabel}
          menuPlacement={menuPlacement}
        />
      </div>

      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onNext}
        disabled={!canGoForward}
        aria-label={nextLabel}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="m9.5 6.5L15 12l-5.5 5.5" />
        </svg>
      </button>
    </div>
  );
}
