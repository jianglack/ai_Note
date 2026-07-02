function runtimeApiBaseUrl(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.localStorage.getItem('ainote.apiBaseUrl');
}

export const API_BASE_URL =
  runtimeApiBaseUrl() ?? import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.MODE === 'production' ? '' : 'http://localhost:8081');

export function getAuthToken(): string | null {
  // See docs/security/auth-token-storage.md before changing this to cookie auth.
  return localStorage.getItem('token');
}

export function clearAuthStorage(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('auth-storage');
}
