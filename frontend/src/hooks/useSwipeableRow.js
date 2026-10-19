import { useRef, useState, useCallback, useEffect } from 'react';
import { hapticThreshold, hapticSelection } from '../lib/haptics.js';

/**
 * Material 3 Expressive Swipeable Row Hook
 *
 * Provides touch-gated horizontal swipe displacement to reveal action pills,
 * with strict gesture disambiguation that completely avoids conflicting with
 * tap-to-expand accordion rows.
 *
 * CRITICAL REQUIREMENT:
 * `suppressClickRef`: When a swipe occurs (displacement > 6px), captures and
 * suppresses the subsequent `click` event so that swiping NEVER accidentally
 * triggers the row's tap-to-expand accordion! Pure taps (< 6px) pass through
 * unsuppressed.
 */
export function useSwipeableRow({
  disabled = false,
  maxSwipe = 128,
  threshold = 56,
  onOpen,
  onClose
} = {}) {
  const [swipeOffset, setSwipeOffset] = useState(0);
  const [isSwiping, setIsSwiping] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  const suppressClickRef = useRef(false);
  const isDraggingRef = useRef(false);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const startOffsetRef = useRef(0);
  const currentOffsetRef = useRef(0);
  const directionLockedRef = useRef(null); // 'horizontal' | 'vertical' | null
  const hasCrossedThresholdRef = useRef(false);
  const pointerIdRef = useRef(null);

  useEffect(() => {
    currentOffsetRef.current = swipeOffset;
  }, [swipeOffset]);

  const closeSwipe = useCallback(() => {
    setSwipeOffset(0);
    setIsOpen(false);
    setIsSwiping(false);
    isDraggingRef.current = false;
    directionLockedRef.current = null;
    hasCrossedThresholdRef.current = false;
    onClose?.();
  }, [onClose]);

  // Touch-gated pointer down
  const onPointerDown = useCallback(
    (e) => {
      if (disabled) return;
      // CRITICAL: Gate touch events to prevent mouse drag conflicts on desktop
      if (e.pointerType !== 'touch') return;

      pointerIdRef.current = e.pointerId;
      startXRef.current = e.clientX;
      startYRef.current = e.clientY;
      startOffsetRef.current = currentOffsetRef.current;
      isDraggingRef.current = true;
      directionLockedRef.current = null;
      hasCrossedThresholdRef.current = Math.abs(currentOffsetRef.current) >= threshold;
    },
    [disabled, threshold]
  );

  // Pointer move with angle lock and tap-slop filtering
  const onPointerMove = useCallback(
    (e) => {
      if (!isDraggingRef.current || e.pointerId !== pointerIdRef.current) return;
      if (e.pointerType !== 'touch') return;

      const dx = e.clientX - startXRef.current;
      const dy = e.clientY - startYRef.current;

      // Disambiguate horizontal swipe vs vertical scroll
      if (directionLockedRef.current === null) {
        const distance = Math.hypot(dx, dy);
        if (distance < 6) {
          // Within tap slop (< 6px), do not lock or suppress clicks
          return;
        }

        if (Math.abs(dy) >= Math.abs(dx)) {
          // Vertical movement dominates: yield completely to native page scroll
          directionLockedRef.current = 'vertical';
          isDraggingRef.current = false;
          return;
        }

        // Horizontal swipe detected (> 6px displacement)!
        directionLockedRef.current = 'horizontal';
        suppressClickRef.current = true;
        setIsSwiping(true);

        try {
          e.currentTarget?.setPointerCapture?.(e.pointerId);
        } catch {
          // Non-blocking fallback
        }
      }

      if (directionLockedRef.current === 'horizontal') {
        // Active horizontal swipe: maintain click suppression
        suppressClickRef.current = true;

        const rawOffset = startOffsetRef.current + dx;
        let newOffset;

        if (rawOffset > 0) {
          // Dragging right when already closed: rubber-band resistance
          newOffset = Math.min(24, rawOffset * 0.2);
        } else if (rawOffset < -maxSwipe) {
          // Dragging past max swipe: rubber-band resistance
          const excess = -rawOffset - maxSwipe;
          newOffset = -maxSwipe - Math.min(24, excess * 0.2);
        } else {
          newOffset = rawOffset;
        }

        // Haptic tick upon crossing activation threshold
        if (newOffset <= -threshold && !hasCrossedThresholdRef.current) {
          hasCrossedThresholdRef.current = true;
          hapticThreshold();
        } else if (newOffset > -threshold && hasCrossedThresholdRef.current) {
          hasCrossedThresholdRef.current = false;
        }

        setSwipeOffset(newOffset);
      }
    },
    [maxSwipe, threshold]
  );

  // Pointer up: snap open or closed
  const onPointerUp = useCallback(
    (e) => {
      if (e.pointerId !== pointerIdRef.current) return;

      if (isDraggingRef.current && directionLockedRef.current === 'horizontal') {
        isDraggingRef.current = false;
        setIsSwiping(false);

        try {
          if (e.currentTarget?.hasPointerCapture?.(e.pointerId)) {
            e.currentTarget.releasePointerCapture(e.pointerId);
          }
        } catch {
          // Non-blocking fallback
        }

        const currentOffset = currentOffsetRef.current;
        if (currentOffset <= -threshold) {
          // Snap open
          setSwipeOffset(-maxSwipe);
          setIsOpen(true);
          hapticSelection();
          onOpen?.();
        } else {
          // Snap closed
          setSwipeOffset(0);
          setIsOpen(false);
          onClose?.();
        }

        // Guarantee click suppression covers the synthesized click event
        suppressClickRef.current = true;
        setTimeout(() => {
          suppressClickRef.current = false;
        }, 100);
      } else {
        isDraggingRef.current = false;
      }
    },
    [maxSwipe, onClose, onOpen, threshold]
  );

  // Pointer cancel
  const onPointerCancel = useCallback(
    (e) => {
      if (e.pointerId !== pointerIdRef.current) return;
      isDraggingRef.current = false;
      setIsSwiping(false);
      directionLockedRef.current = null;
      if (currentOffsetRef.current !== 0) {
        setSwipeOffset(0);
        setIsOpen(false);
        onClose?.();
      }
    },
    [onClose]
  );

  // Capture phase click handler to suppress click event following swipe
  const onClickCapture = useCallback((e) => {
    if (suppressClickRef.current) {
      e.stopPropagation();
      e.preventDefault();
      setTimeout(() => {
        suppressClickRef.current = false;
      }, 50);
    }
  }, []);

  // Close swipe when tapping/touching outside
  useEffect(() => {
    if (!isOpen) return undefined;

    function handleGlobalTouch(e) {
      closeSwipe();
    }

    const timer = setTimeout(() => {
      window.addEventListener('touchstart', handleGlobalTouch, { passive: true, capture: true });
      window.addEventListener('mousedown', handleGlobalTouch, { capture: true });
    }, 20);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('touchstart', handleGlobalTouch, { capture: true });
      window.removeEventListener('mousedown', handleGlobalTouch, { capture: true });
    };
  }, [isOpen, closeSwipe]);

  return {
    swipeOffset,
    isSwiping,
    isOpen,
    suppressClickRef,
    closeSwipe,
    rowProps: {
      onPointerDown,
      onPointerMove,
      onPointerUp,
      onPointerCancel,
      onClickCapture
    }
  };
}
