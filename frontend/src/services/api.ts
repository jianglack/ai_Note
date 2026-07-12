import axios from 'axios';
import { API_BASE_URL, clearAuthStorage } from './apiBase';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
});

let redirectingToLogin = false;

// 璇锋眰鎷︽埅鍣細鑷姩娣诲姞 Token + TraceId
api.interceptors.request.use((config) => {
  // 鍏ㄩ摼璺拷韪細姣忎釜璇锋眰鎼哄甫鍞竴 traceId
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '').substring(0, 16);
  return config;
});

// 鍝嶅簲鎷︽埅鍣細401 娓呴櫎鏈湴鐘舵€佸苟璺宠浆鐧诲綍
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as (typeof error.config & { _csrfRetried?: boolean }) | undefined;
    const method = original?.method?.toLowerCase();
    const unsafe = method != null && ['post', 'put', 'patch', 'delete'].includes(method);
    if (error.response?.status === 403 && unsafe && original && !original._csrfRetried) {
      original._csrfRetried = true;
      await api.get('/api/auth/csrf');
      return api.request(original);
    }
    if (error.response?.status === 401) {
      clearAuthStorage();
      if (!redirectingToLogin && !window.location.pathname.startsWith('/login')) {
        redirectingToLogin = true;
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  }
);

export default api;
