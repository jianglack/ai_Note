import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it, vi } from 'vitest';

import QuickSwitcher from '../src/components/QuickSwitcher';

beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: vi.fn(),
  });
});

const notes = [
  {
    id: 'alpha',
    title: 'Alpha note',
    content: 'first',
    updatedAt: '2026-01-01T00:00:00.000Z',
  },
  {
    id: 'beta',
    title: 'Beta note',
    content: 'second',
    updatedAt: '2026-01-02T00:00:00.000Z',
  },
];

describe('QuickSwitcher behavior', () => {
  it('selects the latest filtered note with the stable keyboard handler', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onSelectNote = vi.fn();

    render(
      <QuickSwitcher
        isOpen
        onClose={onClose}
        notes={notes}
        onSelectNote={onSelectNote}
      />
    );

    await user.type(screen.getByRole('textbox'), 'beta');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(onSelectNote).toHaveBeenCalledWith('beta'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
