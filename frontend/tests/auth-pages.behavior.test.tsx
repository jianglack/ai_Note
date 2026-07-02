import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RegisterPage from '../src/pages/RegisterPage';
import ResetPasswordPage from '../src/pages/ResetPasswordPage';
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

describe('auth page behavior', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    vi.useRealTimers();
    useAuthStore.setState({ user: null, token: null, isAuthenticated: false });
  });

  it('registers once on double submit and navigates home', async () => {
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
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    );

    const username = container.querySelector('input[type="text"]')!;
    const email = container.querySelector('input[type="email"]')!;
    const password = container.querySelector('input[type="password"]')!;
    const submit = container.querySelector('button[type="submit"]')!;

    await user.type(username, 'alice');
    await user.type(email, 'alice@example.com');
    await user.type(password, 'correct-password');
    await user.dblClick(submit);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledTimes(1);
      expect(api.post).toHaveBeenCalledWith('/api/auth/register', {
        username: 'alice',
        email: 'alice@example.com',
        password: 'correct-password',
      });
    });
    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(await screen.findByText('home')).toBeInTheDocument();
  });

  it('validates reset password confirmation before posting', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter>
        <ResetPasswordPage />
      </MemoryRouter>
    );

    const inputs = container.querySelectorAll('input');
    await user.type(inputs[0], 'alice');
    await user.type(inputs[1], 'alice@example.com');
    await user.type(inputs[2], 'first-password');
    await user.type(inputs[3], 'second-password');
    await user.click(container.querySelector('button[type="submit"]')!);

    expect(api.post).not.toHaveBeenCalled();
    expect(await screen.findByText(/两次输入的密码不一致/)).toBeInTheDocument();
  });

  it('resets password once on double submit and redirects to login', async () => {
    vi.useFakeTimers();
    vi.mocked(api.post).mockResolvedValueOnce({ data: {} });
    const { container } = render(
      <MemoryRouter initialEntries={['/reset-password']}>
        <Routes>
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    );

    const inputs = container.querySelectorAll('input');
    fireEvent.change(inputs[0], { target: { value: 'alice' } });
    fireEvent.change(inputs[1], { target: { value: 'alice@example.com' } });
    fireEvent.change(inputs[2], { target: { value: 'new-password' } });
    fireEvent.change(inputs[3], { target: { value: 'new-password' } });

    await act(async () => {
      fireEvent.click(container.querySelector('button[type="submit"]')!);
      fireEvent.click(container.querySelector('button[type="submit"]')!);
    });

    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.post).toHaveBeenCalledWith('/api/auth/reset-password', {
      username: 'alice',
      email: 'alice@example.com',
      newPassword: 'new-password',
    });
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(screen.getByText('login page')).toBeInTheDocument();
  });
});
