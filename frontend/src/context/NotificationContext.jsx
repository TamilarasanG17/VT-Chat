import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { notificationService } from '../services/notificationService';
import { websocketService } from '../services/websocketService';
import { toastError } from '../utils/alerts';
import {
  applyEvent, browserSupportsNotifications, EVENT_CREATED, INITIAL, markAllReadLocally, markReadLocally,
  mergeNotifications, removeLocally, routeFor, shouldShowBrowserNotification,
} from '../utils/notifications';

const PAGE_SIZE = 20;
const NotificationContext = createContext(null);

export function useNotificationContext() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error('useNotifications must be used inside <NotificationProvider>');
  return ctx;
}

export function NotificationProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [state, setState] = useState(INITIAL);
  const [settings, setSettings] = useState(null);
  const [permission, setPermission] = useState(() => (browserSupportsNotifications() ? Notification.permission : 'unsupported'));
  const settingsRef = useRef(null);
  settingsRef.current = settings;
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;

  const load = useCallback(async (page = 0) => {
    setState((s) => ({ ...s, status: page === 0 ? 'loading' : s.status, error: '' }));
    try {
      const res = await notificationService.list(page, PAGE_SIZE);
      const data = res.data;
      setState((s) => ({
        ...s,
        status: 'ready',
        items: page === 0 ? mergeNotifications([], data.items) : mergeNotifications(s.items, data.items),
        unread: data.unreadCount,
        page: data.page,
        hasMore: data.hasMore,
        error: '',
      }));
    } catch {
      setState((s) => ({ ...s, status: 'error', error: 'Unable to load notifications' }));
    }
  }, []);

  const refreshUnread = useCallback(() => {
    notificationService
      .unreadCount()
      .then((res) => setState((s) => ({ ...s, unread: res.data.count })))
      .catch(() => {});
  }, []);

  const showBrowserNotification = useCallback((n) => {
    if (!browserSupportsNotifications()) return;
    if (!shouldShowBrowserNotification({ settings: settingsRef.current, permission: Notification.permission, visibilityState: document.visibilityState })) return;
    try {
      const popup = new Notification(n.title, { body: n.message, tag: `vtchat-${n.id}`, icon: '/favicon.ico' });
      popup.onclick = () => {
        window.focus();
        const to = routeFor(n);
        if (to) navigateRef.current(to);
        popup.close();
      };
    } catch {
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setState(INITIAL);
      setSettings(null);
      return undefined;
    }
    websocketService.retain();
    load(0);
    notificationService.getSettings().then((res) => setSettings(res.data)).catch(() => {});

    const offs = [
      websocketService.onNotificationEvent((event) => {
        setState((s) => applyEvent(s, event));
        if (event.type === EVENT_CREATED && event.notification) showBrowserNotification(event.notification);
      }),
      websocketService.onReconnect(() => load(0)),
    ];
    return () => {
      offs.forEach((off) => off());
      websocketService.release();
    };
  }, [isAuthenticated, load, showBrowserNotification]);

  const markRead = useCallback(async (id) => {
    setState((s) => markReadLocally(s, id));
    try {
      const res = await notificationService.markRead(id);
      setState((s) => ({ ...s, unread: res.data.count }));
    } catch {
      refreshUnread();
    }
  }, [refreshUnread]);

  const markAllRead = useCallback(async () => {
    setState((s) => markAllReadLocally(s));
    try {
      await notificationService.markAllRead();
    } catch {
      toastError('Could not mark notifications as read');
      load(0);
    }
  }, [load]);

  const remove = useCallback(async (id) => {
    setState((s) => removeLocally(s, id));
    try {
      const res = await notificationService.remove(id);
      setState((s) => ({ ...s, unread: res.data.count }));
    } catch {
      toastError('Could not delete the notification');
      load(0);
    }
  }, [load]);

  const loadMore = useCallback(() => load(state.page + 1), [load, state.page]);

  const updateSettings = useCallback(async (patch) => {
    const previous = settingsRef.current;
    setSettings((s) => ({ ...(s ?? {}), ...patch }));
    try {
      const res = await notificationService.updateSettings(patch);
      setSettings(res.data);
      return true;
    } catch {
      setSettings(previous);
      toastError('Could not save your notification settings');
      return false;
    }
  }, []);

  const requestBrowserPermission = useCallback(async () => {
    if (!browserSupportsNotifications()) return 'unsupported';
    if (Notification.permission !== 'default') {
      setPermission(Notification.permission);
      return Notification.permission;
    }
    const result = await Notification.requestPermission();
    setPermission(result);
    return result;
  }, []);

  const value = useMemo(
    () => ({ ...state, settings, permission, load, loadMore, markRead, markAllRead, remove, updateSettings, requestBrowserPermission }),
    [state, settings, permission, load, loadMore, markRead, markAllRead, remove, updateSettings, requestBrowserPermission],
  );

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}
