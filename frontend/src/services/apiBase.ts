function runtimeApiBaseUrl(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.localStorage.getItem('ainote.apiBaseUrl');
}

export const API_BASE_URL =
  runtimeApiBaseUrl() ?? import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.MODE === 'production' ? '' : 'http://localhost:8081');

export function clearAuthStorage(): void {
  // Remove credentials left by pre-cookie releases. JWTs are never stored client-side now.
  localStorage.removeItem('token');
  localStorage.removeItem('auth-storage');
}
