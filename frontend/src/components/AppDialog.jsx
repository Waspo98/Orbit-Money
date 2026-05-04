import { useCallback, useEffect, useRef, useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';

const APP_DIALOG_ACTION_DELAY_MS = 75;

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
        actionDelayMs: Math.max(0, Number(options.actionDelayMs) || APP_DIALOG_ACTION_DELAY_MS),
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
        actionDelayMs: Math.max(0, Number(options.actionDelayMs) || APP_DIALOG_ACTION_DELAY_MS),
        resolve
      });
    });
  }, []);

  function Dialog() {
    const resultRef = useRef(false);
    const actionDelayMs = Math.max(0, Number(dialog?.actionDelayMs) || 0);
    const [actionsReady, setActionsReady] = useState(actionDelayMs === 0);

    useEffect(() => {
      resultRef.current = false;

      if (!dialog || actionDelayMs === 0) {
        setActionsReady(true);
        return undefined;
      }

      setActionsReady(false);
      const timer = window.setTimeout(() => {
        setActionsReady(true);
      }, actionDelayMs);

      return () => window.clearTimeout(timer);
    }, [actionDelayMs, dialog]);

    if (!dialog) return null;

    return (
      <AnimatedModal onClose={() => close(resultRef.current)} size="sm">
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
                      if (!actionsReady) return;
                      finish(false);
                    }}
                    disabled={!actionsReady}
                  >
                    {dialog.cancelLabel}
                  </button>
                )}
                <button
                  type="button"
                  className={dialog.destructive ? 'btn-danger' : 'btn-primary'}
                  onClick={() => {
                    if (!actionsReady) return;
                    finish(true, { animate: true });
                  }}
                  disabled={!actionsReady}
                  autoFocus={actionsReady}
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
