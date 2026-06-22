import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  askConfirm,
  getDialogSnapshot,
  resolveDialog,
} from '../src/services/dialogService';

function timeout<T>(promise: Promise<T>, ms = 50): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
  ]);
}

describe('dialog service', () => {
  it('queues dialogs so concurrent opens resolve in order', async () => {
    const first = askConfirm({ title: 'First' });
    const second = askConfirm({ title: 'Second' });

    assert.equal(getDialogSnapshot()?.title, 'First');

    resolveDialog(true);
    assert.equal(await timeout(first), true);
    assert.equal(getDialogSnapshot()?.title, 'Second');

    resolveDialog(false);
    assert.equal(await timeout(second), false);
    assert.equal(getDialogSnapshot(), null);
  });
});
