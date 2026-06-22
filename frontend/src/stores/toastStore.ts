import { create } from 'zustand';

export type ToastType = 'success' | 'partial' | 'failed' | 'info';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  action?: ToastAction;
  duration?: number; // ms, 0 = no auto-dismiss
  exiting?: boolean; // for slide-out animation
}

interface ToastState {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, 'id'>) => string;
  removeToast: (id: string) => void;
  clearAll: () => void;
  markExiting: (id: string) => void;
}

let nextId = 0;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  addToast: (toast) => {
    const id = `toast-${++nextId}`;
    const newToast: Toast = { ...toast, id };

    set((s) => ({
      toasts: [...s.toasts.slice(-2), newToast], // keep max 3
    }));

    // Auto-dismiss: skip if duration is 0 or has action
    const duration = toast.duration ?? (toast.action ? 0 : 3000);
    if (duration > 0) {
      setTimeout(() => {
        get().markExiting(id);
        setTimeout(() => get().removeToast(id), 300);
      }, duration);
    }

    return id;
  },

  removeToast: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },

  markExiting: (id) => {
    set((s) => ({
      toasts: s.toasts.map((t) => (t.id === id ? { ...t, exiting: true } : t)),
    }));
  },

  clearAll: () => set({ toasts: [] }),
}));
