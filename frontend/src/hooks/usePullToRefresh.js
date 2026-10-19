import React, { useEffect, useRef, useState } from 'react';
import {
  hapticThreshold,
  hapticRefreshSnap,
  hapticSuccess
} from '../lib/haptics.js';
import { PullToRefreshIndicator } from '../components/PullToRefreshIndicator.jsx';

export { PullToRefreshIndicator };

/**
 * Material 3 Expressive Pull-To-Refresh Hook
 *
 * Implements logarithmic rubber-band physics:
 * offset = Math.min(88, 45 * Math.log(1 + dy / 35))
 *
 * - Activation threshold: 56px (fires hapticThreshold)
 * - Snap holding position: 48px (fires hapticRefreshSnap)
 * - Resolution: springs back to 0px with hapticSuccess
 * - Only listens when window.scrollY <= 0
 */
export function usePullToRefresh({ onRefresh, disabled = false } = {}) {
  const [pullOffset, setPullOffset] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isDragging, setIsDragging] = useState(false);

  const startYRef = useRef(0);
  const startXRef = useRef(0);
  const isTrackingRef = useRef(false);
  const isPullingRef = useRef(false);
  const armedRef = useRef(false);
  const isRefreshingRef = useRef(false);
  const onRefreshRef = useRef(onRefresh);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    isRefreshingRef.current = isRefreshing;
  }, [isRefreshing]);

  const THRESHOLD = 56;
  const HOLDING_POSITION = 48;
  const MAX_OFFSET = 88;

  useEffect(() => {
    if (disabled || typeof window === 'undefined') return undefined;

    function getScrollTop() {
      return (
        window.scrollY ||
        window.pageYOffset ||
        document.documentElement.scrollTop ||
        document.body.scrollTop ||
        0
      );
    }

    function onTouchStart(e) {
      if (isRefreshingRef.current) return;
      if (e.touches.length !== 1) return;

      // Only engage gesture when at the very top of the scroll container
      if (getScrollTop() > 0) return;

      const touch = e.touches[0];
      startYRef.current = touch.clientY;
      startXRef.current = touch.clientX;
      isTrackingRef.current = true;
      isPullingRef.current = false;
      armedRef.current = false;
    }

    function onTouchMove(e) {
      if (!isTrackingRef.current || isRefreshingRef.current) return;
      if (e.touches.length !== 1) return;

      const touch = e.touches[0];
      const dy = touch.clientY - startYRef.current;
      const dx = touch.clientX - startXRef.current;

      // If scrolling upward or page has scrolled down, drop tracking
      if (dy <= 0 || getScrollTop() > 0) {
        if (!isPullingRef.current) {
          isTrackingRef.current = false;
        }
        return;
      }

      // If horizontal gesture dominates before pulling started, drop tracking
      if (!isPullingRef.current && Math.abs(dx) > dy) {
        isTrackingRef.current = false;
        return;
      }

      // Initiate pull when exceeding small slop threshold
      if (dy > 4 && dy > Math.abs(dx)) {
        if (!isPullingRef.current) {
          isPullingRef.current = true;
          setIsDragging(true);
        }

        if (e.cancelable) {
          e.preventDefault();
        }

        // Logarithmic rubber-band tension formula:
        // offset = Math.min(88, 45 * Math.log(1 + dy / 35))
        const offset = Math.min(MAX_OFFSET, 45 * Math.log(1 + dy / 35));

        // Threshold check (56px)
        if (offset >= THRESHOLD && !armedRef.current) {
          armedRef.current = true;
          hapticThreshold();
        } else if (offset < THRESHOLD && armedRef.current) {
          armedRef.current = false;
        }

        setPullOffset(offset);
      }
    }

    async function onTouchEnd() {
      if (!isTrackingRef.current && !isPullingRef.current) return;

      const wasArmed = armedRef.current;
      isTrackingRef.current = false;
      isPullingRef.current = false;
      setIsDragging(false);

      if (wasArmed && onRefreshRef.current && !isRefreshingRef.current) {
        setIsRefreshing(true);
        isRefreshingRef.current = true;
        setPullOffset(HOLDING_POSITION);
        hapticRefreshSnap();

        try {
          await onRefreshRef.current();
        } catch (err) {
          console.error('Pull-to-refresh failed:', err);
        } finally {
          hapticSuccess();
          setPullOffset(0);
          setIsRefreshing(false);
          isRefreshingRef.current = false;
          armedRef.current = false;
        }
      } else {
        setPullOffset(0);
        armedRef.current = false;
      }
    }

    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('touchend', onTouchEnd, { passive: true });
    window.addEventListener('touchcancel', onTouchEnd, { passive: true });

    return () => {
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('touchend', onTouchEnd);
      window.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [disabled]);

  const pullProgress = Math.min(1, pullOffset / THRESHOLD);
  const isArmed = pullOffset >= THRESHOLD;

  return {
    pullOffset,
    pullProgress,
    isRefreshing,
    isArmed,
    isDragging,
    indicator: React.createElement(PullToRefreshIndicator, {
      pullOffset,
      pullProgress,
      isRefreshing,
      isArmed,
      isDragging
    }),
    PullToRefreshIndicator
  };
}
