import { describe, expect, it, vi } from 'vitest';
import {
  askConfirm,
  askPrompt,
  getDialogSnapshot,
  resolveDialog,
  showAlert,
  subscribeDialog,
} from '../src/services/dialogService';

describe('dialogService behavior under Vitest coverage', () => {
  it('opens, resolves, and notifies subscribers for confirm dialogs', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeDialog(listener);

    const result = askConfirm({
      title: 'Delete note',
      message: 'Move to trash?',
      confirmLabel: 'Delete',
      danger: true,
    });

    expect(getDialogSnapshot()).toMatchObject({
      kind: 'confirm',
      title: 'Delete note',
      danger: true,
    });
    expect(listener).toHaveBeenCalledTimes(1);

    resolveDialog(true);

    await expect(result).resolves.toBe(true);
    expect(getDialogSnapshot()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
  });

  it('queues dialogs and normalizes prompt values', async () => {
    const first = askPrompt({ title: 'Rename', defaultValue: 'Old' });
    const second = showAlert({ title: 'Saved' });

    expect(getDialogSnapshot()).toMatchObject({ kind: 'prompt', title: 'Rename' });
    resolveDialog('New name');
    await expect(first).resolves.toBe('New name');

    expect(getDialogSnapshot()).toMatchObject({ kind: 'alert', title: 'Saved' });
    resolveDialog();
    await expect(second).resolves.toBeUndefined();

    const cancelled = askPrompt({ title: 'Cancelled' });
    resolveDialog(false);
    await expect(cancelled).resolves.toBeNull();
  });
});
