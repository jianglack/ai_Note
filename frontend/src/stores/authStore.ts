import { create } from 'zustand';
import api from '../services/api';
import { clearAuthStorage } from '../services/apiBase';

interface User {
  userId: string;
  username: string;
  email: string;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  initialized: boolean;
  initialize: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

let initializationPromise: Promise<void> | null = null;

export const useAuthStore = create<AuthState>()((set, get) => ({
  user: null,
  isAuthenticated: false,
  initialized: false,

  initialize: async () => {
    if (get().initialized) return;
    if (initializationPromise) return initializationPromise;
    initializationPromise = (async () => {
      clearAuthStorage();
      try {
        const res = await api.get('/api/auth/me');
        const { userId, username, email } = res.data;
        set({ user: { userId, username, email }, isAuthenticated: true, initialized: true });
      } catch {
        set({ user: null, isAuthenticated: false, initialized: true });
      } finally {
        initializationPromise = null;
      }
    })();
    return initializationPromise;
  },

  login: async (username, password) => {
    const res = await api.post('/api/auth/login', { username, password });
    const { userId, username: authenticatedUsername, email } = res.data;
    set({
      user: { userId, username: authenticatedUsername, email },
      isAuthenticated: true,
      initialized: true,
    });
  },

  register: async (username, email, password) => {
    const res = await api.post('/api/auth/register', { username, email, password });
    const { userId, username: authenticatedUsername, email: authenticatedEmail } = res.data;
    set({
      user: { userId, username: authenticatedUsername, email: authenticatedEmail },
      isAuthenticated: true,
      initialized: true,
    });
  },

  logout: async () => {
    try {
      await api.post('/api/auth/logout');
    } catch {
      // Local auth state must still be cleared when the server session already expired.
    }
    clearAuthStorage();
    set({ user: null, isAuthenticated: false, initialized: true });
  },
}));
