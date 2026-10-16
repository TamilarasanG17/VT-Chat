export const INITIAL = { status: 'idle', items: [], unread: 0, page: 0, hasMore: false, error: '' };

export const EVENT_CREATED = 'NOTIFICATION_CREATED';
export const EVENT_UPDATED = 'NOTIFICATIONS_UPDATED';

export function mergeNotifications(current, incoming) {
  const byId = new Map(current.map((n) => [n.id, n]));
  incoming.forEach((n) => {
    const old = byId.get(n.id);
    byId.set(n.id, old && old.read && !n.read ? old : n);
  });
  return [...byId.values()].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt) || b.id - a.id);
}

export function applyEvent(state, event) {
  if (event.type === EVENT_CREATED && event.notification) {
    return { ...state, items: mergeNotifications(state.items, [event.notification]), unread: event.unreadCount ?? state.unread };
  }
  if (event.type === EVENT_UPDATED) {
    const items = event.conversationId
      ? state.items.map((n) => (n.conversationId === event.conversationId && !n.read ? { ...n, read: true } : n))
      : state.items;
    return { ...state, items, unread: event.unreadCount ?? state.unread };
  }
  return state;
}

export const markReadLocally = (state, id) => {
  const target = state.items.find((n) => n.id === id);
  if (!target || target.read) return state;
  return { ...state, items: state.items.map((n) => (n.id === id ? { ...n, read: true } : n)), unread: Math.max(0, state.unread - 1) };
};

export const markAllReadLocally = (state) => ({ ...state, items: state.items.map((n) => (n.read ? n : { ...n, read: true })), unread: 0 });

export const removeLocally = (state, id) => {
  const target = state.items.find((n) => n.id === id);
  if (!target) return state;
  return { ...state, items: state.items.filter((n) => n.id !== id), unread: target.read ? state.unread : Math.max(0, state.unread - 1) };
};

export function routeFor(n) {
  if (n?.type === 'CHAT_REQUEST') return '/chats?tab=requests';
  if (!n?.conversationId) return null;
  const isCall = n.type === 'MISSED_CALL' || n.type === 'INCOMING_CALL';
  return `/chat/${n.conversationId}${isCall ? '?calls=1' : ''}`;
}

export function shouldShowBrowserNotification({ settings, permission, visibilityState }) {
  return !!settings?.browserNotifications && permission === 'granted' && visibilityState !== 'visible';
}

export const browserSupportsNotifications = () => typeof window !== 'undefined' && 'Notification' in window;
