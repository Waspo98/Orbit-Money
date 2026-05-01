import { useEffect, useRef } from 'react';

let lockCount = 0;
let lockSnapshot = null;
let pageBlurCount = 0;
let overlayHistoryId = 0;
const OVERLAY_HISTORY_KEY = '__orbitOverlayId';

export const OVERLAY_ANIM_MS = 180;

export function releaseCurrentOverlayHistoryEntry() {
  const currentState = window.history.state;
  if (
    !currentState ||
    typeof currentState !== 'object' ||
    !currentState[OVERLAY_HISTORY_KEY]
  ) {
    return false;
  }

  const { [OVERLAY_HISTORY_KEY]: _overlayId, ...nextState } = currentState;
  window.history.replaceState(nextState, '', window.location.href);
  return true;
}

export function useBodyScrollLock(active) {
  useEffect(() => {
    if (!active) return undefined;

    if (lockCount === 0) {
      const scrollY = window.scrollY;
      lockSnapshot = {
        scrollY,
        overflow: document.body.style.overflow,
        position: document.body.style.position,
        top: document.body.style.top,
        left: document.body.style.left,
        right: document.body.style.right,
        width: document.body.style.width
      };

      document.body.style.overflow = 'hidden';
      document.body.style.position = 'fixed';
      document.body.style.top = `-${scrollY}px`;
      document.body.style.left = '0';
      document.body.style.right = '0';
      document.body.style.width = '100%';
    }

    lockCount += 1;

    return () => {
      lockCount = Math.max(0, lockCount - 1);
      if (lockCount > 0 || !lockSnapshot) return;

      const snapshot = lockSnapshot;
      lockSnapshot = null;
      document.body.style.overflow = snapshot.overflow;
      document.body.style.position = snapshot.position;
      document.body.style.top = snapshot.top;
      document.body.style.left = snapshot.left;
      document.body.style.right = snapshot.right;
      document.body.style.width = snapshot.width;
      window.scrollTo(0, snapshot.scrollY);
    };
  }, [active]);
}

export function usePageBackdropBlur(active) {
  useEffect(() => {
    if (!active) return undefined;

    if (pageBlurCount === 0) {
      document.body.classList.add('modal-page-blur-active');
    }

    pageBlurCount += 1;

    return () => {
      pageBlurCount = Math.max(0, pageBlurCount - 1);
      if (pageBlurCount === 0) {
        document.body.classList.remove('modal-page-blur-active');
      }
    };
  }, [active]);
}

export function useOverlayBackDismiss(active, onDismiss) {
  const onDismissRef = useRef(onDismiss);

  useEffect(() => {
    onDismissRef.current = onDismiss;
  }, [onDismiss]);

  useEffect(() => {
    if (!active) return undefined;

    const id = `orbit-overlay-${++overlayHistoryId}`;
    let dismissedByBack = false;
    const currentState = window.history.state;
    const stateBase = currentState && typeof currentState === 'object'
      ? currentState
      : {};

    window.history.pushState(
      { ...stateBase, [OVERLAY_HISTORY_KEY]: id },
      '',
      window.location.href
    );

    function handlePopState(event) {
      if (event.state?.[OVERLAY_HISTORY_KEY] === id) return;
      dismissedByBack = true;
      onDismissRef.current?.();
    }

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
      if (dismissedByBack) return;
      if (window.history.state?.[OVERLAY_HISTORY_KEY] === id) {
        window.history.back();
      }
    };
  }, [active]);
}
