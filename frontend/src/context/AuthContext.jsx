import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { authService } from '../services/authService';
import { websocketService } from '../services/websocketService';
import { refreshSession, setAuthFailureListener, setSessionListener } from '../services/api';
import { sessionHint, tokenStore } from '../utils/tokenStore';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [status, setStatus] = useState('loading');
  const [user, setUser] = useState(null);
  const [accessToken, setAccessToken] = useState(null);
  const [expiresAt, setExpiresAt] = useState(null);

  const applySession = useCallback((session) => {
    tokenStore.set(session.accessToken);
    sessionHint.set();
    setAccessToken(session.accessToken);
    setUser(session.user);
    setExpiresAt(Date.now() + session.expiresIn * 1000);
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback((keepHint = false) => {
    websocketService.disconnect(true);
    tokenStore.clear();
    if (!keepHint) sessionHint.clear();
    setAccessToken(null);
    setUser(null);
    setExpiresAt(null);
    setStatus('unauthenticated');
  }, []);

  useEffect(() => {
    setSessionListener(applySession);
    setAuthFailureListener(() => clearSession());
    return () => {
      setSessionListener(null);
      setAuthFailureListener(null);
    };
  }, [applySession, clearSession]);

  useEffect(() => {
    if (!sessionHint.has()) {
      setStatus('unauthenticated');
      return;
    }
    refreshSession().catch((err) => {
      clearSession(!err.response);
    });
  }, [clearSession]);

  useEffect(() => {
    if (!expiresAt) return undefined;
    const delay = Math.max(expiresAt - Date.now() - 60_000, 5_000);
    const timer = setTimeout(() => {
      refreshSession().catch(() => {
      });
    }, delay);
    return () => clearTimeout(timer);
  }, [expiresAt]);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo(
    () => ({
      status,
      loading: status === 'loading',
      isAuthenticated: status === 'authenticated',
      user,
      accessToken,
      completeAuth: applySession,
      updateUser: setUser,
      logout,
    }),
    [status, user, accessToken, applySession, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
