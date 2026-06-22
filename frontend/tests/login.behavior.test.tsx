import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import LoginPage from '../src/pages/LoginPage';
import api from '../src/services/api';
import { useAuthStore } from '../src/stores/authStore';

vi.mock('../src/services/api', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    defaults: { baseURL: '' },
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}));

describe('LoginPage behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useAuthStore.setState({ user: null, token: null, isAuthenticated: false });
  });

  it('submits credentials through the auth store and navigates after login', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: {
        token: 'jwt-token',
        userId: 'user-1',
        username: 'alice',
        email: 'alice@example.com',
      },
    });

    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    );

    const username = container.querySelector('input[type="text"]');
    const password = container.querySelector('input[type="password"]');
    const submit = container.querySelector('button[type="submit"]');

    expect(username).not.toBeNull();
    expect(password).not.toBeNull();
    expect(submit).not.toBeNull();

    await user.type(username!, 'alice');
    await user.type(password!, 'correct-password');
    await user.click(submit!);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/auth/login', {
        username: 'alice',
        password: 'correct-password',
      });
    });
    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(await screen.findByText('home')).toBeInTheDocument();
  });
});
