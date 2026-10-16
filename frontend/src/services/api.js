import axios from 'axios';
import { tokenStore } from '../utils/tokenStore';
import { toastError } from '../utils/alerts';
import { isRateLimited, rateLimitMessage } from '../utils/errors';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
});

let sessionListener = null;
let failureListener = null;
export const setSessionListener = (fn) => {
  sessionListener = fn;
};
export const setAuthFailureListener = (fn) => {
  failureListener = fn;
};

let refreshPromise = null;

function runExclusive(task) {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request('vtchat-refresh', task);
  }
  return task();
}

export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = runExclusive(async () => {
      const res = await axios.post(`${API_URL}/auth/refresh`, null, { withCredentials: true, timeout: 20000 });
      return res.data.data;
    })
      .then((session) => {
        tokenStore.set(session.accessToken);
        sessionListener?.(session);
        return session;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

const blockedUntil = new Map();
let lastToastAt = 0;

const endpointKey = (config) => `${(config?.method || 'get').toUpperCase()} ${String(config?.url || '').split('?')[0].replace(/\d+/g, ':id')}`;

function rateLimitedError(config, seconds) {
  const error = new Error('Too many requests');
  error.isAxiosError = true;
  error.synthetic = true;
  error.config = config;
  error.response = {
    status: 429,
    data: { success: false, message: rateLimitMessage(seconds), code: 'RATE_LIMITED', retryAfter: seconds, retryAfterSeconds: seconds },
  };
  return error;
}

api.interceptors.request.use((config) => {
  const until = blockedUntil.get(endpointKey(config));
  if (until && until > Date.now()) {
    return Promise.reject(rateLimitedError(config, Math.ceil((until - Date.now()) / 1000)));
  }
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const url = original?.url || '';
    const isAuthEndpoint = url.startsWith('/auth/') && url !== '/auth/me';

    if (isRateLimited(error) && original) {
      const seconds = Number(error.response.data.retryAfterSeconds ?? error.response.data.retryAfter) || 30;
      if (!error.synthetic) blockedUntil.set(endpointKey(original), Date.now() + seconds * 1000);
      if (!url.startsWith('/auth/') && Date.now() - lastToastAt > 3000) {
        lastToastAt = Date.now();
        toastError(rateLimitMessage(seconds));
      }
      return Promise.reject(error);
    }

    if (status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true;
      try {
        const session = await refreshSession();
        original.headers.Authorization = `Bearer ${session.accessToken}`;
        return api(original);
      } catch {
        tokenStore.clear();
        failureListener?.();
      }
    }
    return Promise.reject(error);
  },
);

export default api;
