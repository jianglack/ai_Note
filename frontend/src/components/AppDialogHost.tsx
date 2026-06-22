import { useEffect, useRef, useState } from 'react';
import {
  getDialogSnapshot,
  resolveDialog,
  subscribeDialog,
  type DialogState,
} from '../services/dialogService';

export default function AppDialogHost() {
  const [dialog, setDialog] = useState<DialogState | null>(getDialogSnapshot());
  const [inputValue, setInputValue] = useState('');
  const panelRef = useRef<HTMLDivElement | null>(null);
  const primaryRef = useRef<HTMLButtonElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => subscribeDialog(() => setDialog(getDialogSnapshot())), []);

  useEffect(() => {
    if (!dialog) return;
    setInputValue(dialog.defaultValue ?? '');
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusTarget = dialog.kind === 'prompt' ? inputRef : primaryRef;
    window.setTimeout(() => focusTarget.current?.focus(), 0);
    return () => previous?.focus();
  }, [dialog]);

  useEffect(() => {
    if (!dialog) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        resolveDialog(dialog.kind === 'confirm' ? false : null);
      }

      if (event.key !== 'Tab' || !panelRef.current) return;
      const focusable = Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled])'
        )
      );
      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [dialog]);

  if (!dialog) return null;

  const isPrompt = dialog.kind === 'prompt';
  const isAlert = dialog.kind === 'alert';
  const cancelLabel = dialog.cancelLabel ?? 'Cancel';
  const confirmLabel = dialog.confirmLabel ?? (isAlert ? 'OK' : 'Confirm');

  const confirm = () => {
    if (isPrompt) {
      resolveDialog(inputValue);
      return;
    }
    resolveDialog(true);
  };

  const cancel = () => resolveDialog(dialog.kind === 'confirm' ? false : null);

  return (
    <div className="dialog-overlay" onMouseDown={(event) => {
      if (event.target === event.currentTarget) cancel();
    }}>
      <div
        ref={panelRef}
        className="dialog-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="app-dialog-title"
      >
        <div className="dialog-header">
          <h3 id="app-dialog-title">{dialog.title}</h3>
          <button className="dialog-close" onClick={cancel} aria-label="Close dialog">x</button>
        </div>
        <div className="dialog-body">
          {dialog.message && <p className="confirm-message">{dialog.message}</p>}
          {isPrompt && (
            <input
              ref={inputRef}
              className="feat-input"
              value={inputValue}
              placeholder={dialog.placeholder}
              onChange={(event) => setInputValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  confirm();
                }
              }}
            />
          )}
        </div>
        <div className="dialog-footer">
          {!isAlert && (
            <button className="dialog-btn dialog-btn-cancel" onClick={cancel}>
              {cancelLabel}
            </button>
          )}
          <button
            ref={primaryRef}
            className={`dialog-btn ${dialog.danger ? 'dialog-btn-danger' : 'dialog-btn-confirm'}`}
            onClick={confirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
