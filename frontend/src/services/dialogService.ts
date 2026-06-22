export type DialogKind = 'confirm' | 'prompt' | 'alert';

export interface DialogOptions {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  defaultValue?: string;
  placeholder?: string;
  danger?: boolean;
}

export interface DialogState extends DialogOptions {
  id: number;
  kind: DialogKind;
  resolve: (value: boolean | string | null | undefined) => void;
}

let currentDialog: DialogState | null = null;
let nextId = 1;
const listeners = new Set<() => void>();
const queue: Array<{
  kind: DialogKind;
  options: DialogOptions;
  resolve: (value: boolean | string | null | undefined) => void;
}> = [];

function emit() {
  listeners.forEach((listener) => listener());
}

export function getDialogSnapshot() {
  return currentDialog;
}

export function subscribeDialog(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function openDialog(kind: DialogKind, options: DialogOptions) {
  return new Promise<boolean | string | null | undefined>((resolve) => {
    const request = { kind, options, resolve };
    if (currentDialog) {
      queue.push(request);
      return;
    }
    showDialog(request);
  });
}

function showDialog(request: {
  kind: DialogKind;
  options: DialogOptions;
  resolve: (value: boolean | string | null | undefined) => void;
}) {
  currentDialog = {
    ...request.options,
    id: nextId++,
    kind: request.kind,
    resolve: (value) => {
      currentDialog = null;
      request.resolve(value);
      const next = queue.shift();
      if (next) {
        showDialog(next);
        return;
      }
      emit();
    },
  };
  emit();
}

export async function askConfirm(options: DialogOptions): Promise<boolean> {
  return Boolean(await openDialog('confirm', options));
}

export async function askPrompt(options: DialogOptions): Promise<string | null> {
  const result = await openDialog('prompt', options);
  return typeof result === 'string' ? result : null;
}

export async function showAlert(options: DialogOptions): Promise<void> {
  await openDialog('alert', options);
}

export function resolveDialog(value?: boolean | string | null) {
  currentDialog?.resolve(value);
}
