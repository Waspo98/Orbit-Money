import React from 'react';

/**
 * Material 3 Expressive Spring Spinner Indicator
 */
export function PullToRefreshIndicator({
  pullOffset = 0,
  pullProgress = 0,
  isRefreshing = false,
  isArmed = false,
  isDragging = false
}) {
  const visible = pullOffset > 0 || isRefreshing;
  const currentOffset = isRefreshing ? 48 : pullOffset;

  return (
    <div
      className={`m3-ptr-indicator ${visible ? 'active' : ''} ${isDragging ? 'dragging' : ''} ${isRefreshing ? 'refreshing' : ''} ${isArmed ? 'armed' : ''}`}
      style={{
        transform: visible
          ? `translate3d(-50%, ${currentOffset}px, 0)`
          : 'translate3d(-50%, -64px, 0)',
        opacity: visible ? Math.min(1, pullOffset / 16) : 0
      }}
      aria-hidden="true"
    >
      <svg
        className="m3-ptr-spinner"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
      >
        <circle
          className="m3-ptr-track"
          cx="12"
          cy="12"
          r="9"
          stroke="var(--border)"
          strokeWidth="2.5"
          opacity={isRefreshing ? 0.25 : 0.4}
        />
        <circle
          className="m3-ptr-arc"
          cx="12"
          cy="12"
          r="9"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray="56.5"
          strokeDashoffset={isRefreshing ? 22 : Math.max(6, 56.5 * (1 - pullProgress))}
          style={{
            transformOrigin: '12px 12px',
            transform: isRefreshing ? undefined : `rotate(${pullOffset * 3.6}deg)`
          }}
        />
      </svg>
    </div>
  );
}
