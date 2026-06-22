import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import api from '../services/api';

interface User {
  userId: string;
  username: string;
  email: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,

      login: async (username, password) => {
        const res = await api.post('/api/auth/login', { username, password });
        const { token, userId, username: uname, email } = res.data;
        localStorage.setItem('token', token);
        set({ token, user: { userId, username: uname, email }, isAuthenticated: true });
      },

      register: async (username, email, password) => {
        const res = await api.post('/api/auth/register', { username, email, password });
        const { token, userId, username: uname, email: em } = res.data;
        localStorage.setItem('token', token);
        set({ token, user: { userId, username: uname, email: em }, isAuthenticated: true });
      },

      logout: async () => {
        try {
          await api.post('/api/auth/logout');
        } catch (_) {}
        localStorage.removeItem('token');
        set({ token: null, user: null, isAuthenticated: false });
      }
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({ token: state.token, user: state.user, isAuthenticated: state.isAuthenticated })
    }
  )
);
