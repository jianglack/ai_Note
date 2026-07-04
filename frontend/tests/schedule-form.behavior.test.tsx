import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ScheduleForm from '../src/components/ScheduleForm';
import { createSchedule } from '../src/api';

vi.mock('../src/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../src/api')>()),
  createSchedule: vi.fn(),
  updateSchedule: vi.fn(),
}));

describe('ScheduleForm behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses the warm paper schedule dialog surface', () => {
    const { container } = render(
      <ScheduleForm
        notes={[]}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(container.querySelector('.schedule-form-overlay')).toBeInTheDocument();
    expect(container.querySelector('.schedule-form-panel')).toHaveClass('schedule-form-paper');
  });

  it('shows a visible error when schedule creation fails', async () => {
    vi.mocked(createSchedule).mockRejectedValueOnce(new Error('network down'));
    const user = userEvent.setup();

    render(
      <ScheduleForm
        notes={[]}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />
    );

    await user.type(screen.getByLabelText('标题'), 'Failing schedule');
    await user.type(screen.getByLabelText('开始'), '2026-06-24T10:00');
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('network down');
    });
  });

  it('submits a schedule when required fields are present', async () => {
    const onSave = vi.fn();
    vi.mocked(createSchedule).mockResolvedValueOnce({
      id: 'schedule-1',
      title: 'Planning session',
      description: 'Created from the form',
      startTime: '2026-06-24T10:00:00',
      endTime: undefined,
      allDay: false,
      rrule: undefined,
      reminderMinutes: undefined,
      status: 'pending',
      notes: [],
      createdAt: '2026-06-23T10:00:00',
      updatedAt: '2026-06-23T10:00:00',
    });
    const user = userEvent.setup();

    render(
      <ScheduleForm
        notes={[]}
        onSave={onSave}
        onClose={vi.fn()}
      />
    );

    await user.type(screen.getByLabelText('标题'), 'Planning session');
    fireEvent.change(screen.getByLabelText('开始'), {
      target: { value: '2026-06-24T10:00' },
    });
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        title: 'Planning session',
        startTime: '2026-06-24T10:00:00',
      }));
      expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ id: 'schedule-1' }));
    });
  });

  it('submits the current datetime input value even if change state is stale', async () => {
    const onSave = vi.fn();
    vi.mocked(createSchedule).mockResolvedValueOnce({
      id: 'schedule-2',
      title: 'Planning session',
      description: undefined,
      startTime: '2026-06-24T11:00:00',
      endTime: undefined,
      allDay: false,
      rrule: undefined,
      reminderMinutes: undefined,
      status: 'pending',
      notes: [],
      createdAt: '2026-06-23T10:00:00',
      updatedAt: '2026-06-23T10:00:00',
    });
    const user = userEvent.setup();

    render(
      <ScheduleForm
        notes={[]}
        onSave={onSave}
        onClose={vi.fn()}
      />
    );

    await user.type(screen.getByLabelText('标题'), 'Planning session');
    Object.defineProperty(screen.getByLabelText('开始'), 'value', {
      configurable: true,
      get: () => '2026-06-24T11:00',
    });
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        title: 'Planning session',
        startTime: '2026-06-24T11:00:00',
      }));
      expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ id: 'schedule-2' }));
    });
  });
});
