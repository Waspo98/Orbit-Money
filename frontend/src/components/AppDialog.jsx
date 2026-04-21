import { useCallback, useRef, useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';

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
        resolve
      });
    });
  }, []);

  function Dialog() {
    const resultRef = useRef(false);
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
                    onClick={() => finish(false)}
                  >
                    {dialog.cancelLabel}
                  </button>
                )}
                <button
                  type="button"
                  className={dialog.destructive ? 'btn-danger' : 'btn-primary'}
                  onClick={() => finish(true, { animate: true })}
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
