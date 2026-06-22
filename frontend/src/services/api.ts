import axios from 'axios';
import { API_BASE_URL, clearAuthStorage, getAuthToken } from './apiBase';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' }
});

// 璇锋眰鎷︽埅鍣細鑷姩娣诲姞 Token + TraceId
api.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // 鍏ㄩ摼璺拷韪細姣忎釜璇锋眰鎼哄甫鍞竴 traceId
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '').substring(0, 16);
  return config;
});

// 鍝嶅簲鎷︽埅鍣細401 娓呴櫎鏈湴鐘舵€佸苟璺宠浆鐧诲綍
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuthStorage();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login');
      }
    }
    return Promise.reject(error);
  }
);

export default api;
