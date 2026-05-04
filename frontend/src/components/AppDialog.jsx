import { useCallback, useEffect, useRef, useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';

const APP_DIALOG_INTERACTION_DELAY_MS = 75;

function normalizeInteractionDelay(options = {}) {
  const rawDelay = options.interactionDelayMs ?? options.actionDelayMs;
  if (rawDelay === undefined) return APP_DIALOG_INTERACTION_DELAY_MS;
  const delay = Number(rawDelay);
  return Number.isFinite(delay) && delay > 0 ? delay : 0;
}

export function useAppDialog() {
  const [dialog, setDialog] = useState(null);

  const close = useCallback((result) => {
    setDialog((current) => {
      current?.resolve(result);
      return null;
    });
  }, []);

  const alert = useCallback((message, options = {}) => {
    return new Promise((resolve) => {
      setDialog({
        type: 'alert',
        title: options.title || 'Heads up',
        message,
        confirmLabel: options.confirmLabel || 'OK',
        interactionDelayMs: normalizeInteractionDelay(options),
        resolve
      });
    });
  }, []);

  const confirm = useCallback((message, options = {}) => {
    return new Promise((resolve) => {
      setDialog({
        type: 'confirm',
        title: options.title || 'Are you sure?',
        message,
        confirmLabel: options.confirmLabel || 'Confirm',
        cancelLabel: options.cancelLabel || 'Cancel',
        destructive: !!options.destructive,
        interactionDelayMs: normalizeInteractionDelay(options),
        resolve
      });
    });
  }, []);

  function Dialog() {
    const resultRef = useRef(false);

    useEffect(() => {
      resultRef.current = false;
    }, [dialog]);

    if (!dialog) return null;

    return (
      <AnimatedModal
        onClose={() => close(resultRef.current)}
        size="sm"
        initialInteractionDelayMs={dialog.interactionDelayMs}
      >
        {({ close: closeModal }) => {
          function finish(result, options = {}) {
            resultRef.current = result;
            closeModal(options.animate ? { animate: true } : {});
          }

          return (
            <>
              <h3>{dialog.title}</h3>
              <p className="modal-copy">{dialog.message}</p>
              <div className="modal-actions">
                {dialog.type === 'confirm' && (
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => {
                      finish(false);
                    }}
                  >
                    {dialog.cancelLabel}
                  </button>
                )}
                <button
                  type="button"
                  className={dialog.destructive ? 'btn-danger' : 'btn-primary'}
                  onClick={() => {
                    finish(true, { animate: true });
                  }}
                  autoFocus
                >
                  {dialog.confirmLabel}
                </button>
              </div>
            </>
          );
        }}
      </AnimatedModal>
    );
  }

  return { alert, confirm, Dialog };
}
