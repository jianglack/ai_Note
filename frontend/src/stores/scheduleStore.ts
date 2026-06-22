import { create } from 'zustand';
import type { Schedule } from '../api';

interface ScheduleState {
  schedules: Schedule[];
  editingSchedule: Schedule | undefined;

  setSchedules: (schedules: Schedule[]) => void;
  setEditingSchedule: (schedule: Schedule | undefined) => void;
  addSchedule: (schedule: Schedule) => void;
  addSchedules: (schedules: Schedule[]) => void;
  updateScheduleInList: (updated: Schedule) => void;
  removeSchedule: (id: string) => void;
}

export const useScheduleStore = create<ScheduleState>((set) => ({
  schedules: [],
  editingSchedule: undefined,

  setSchedules: (schedules) => set({ schedules }),
  setEditingSchedule: (schedule) => set({ editingSchedule: schedule }),
  addSchedule: (schedule) => set((s) => ({ schedules: [schedule, ...s.schedules] })),
  addSchedules: (schedules) => set((s) => ({ schedules: [...schedules, ...s.schedules] })),
  updateScheduleInList: (updated) =>
    set((s) => ({ schedules: s.schedules.map((sc) => (sc.id === updated.id ? updated : sc)) })),
  removeSchedule: (id) =>
    set((s) => ({ schedules: s.schedules.filter((sc) => sc.id !== id) })),
}));
