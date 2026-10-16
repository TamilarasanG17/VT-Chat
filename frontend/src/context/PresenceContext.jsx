import { createContext, useContext, useEffect, useRef } from 'react';
import { useAuth } from '../hooks/useAuth';
import { presenceService } from '../services/presenceService';
import { websocketService } from '../services/websocketService';

const TYPING_EXPIRY_MS = 6000;

export function createPresenceStore() {
  const presence = new Map();
  const typing = new Map();
  const typingTimers = new Map();
  const attempted = new Set();
  const listeners = new Set();
  const notify = () => listeners.forEach((fn) => fn());

  return {
    subscribe(fn) {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },
    getPresence: (userId) => presence.get(Number(userId)),
    isTyping: (conversationId) => typing.has(Number(conversationId)),

    setPresence(entry) {
      if (!entry || entry.userId == null) return;
      const id = Number(entry.userId);
      const old = presence.get(id);
      const lastSeenAt = entry.lastSeenAt ?? null;
      if (old && old.status === entry.status && old.lastSeenAt === lastSeenAt) return;
      presence.set(id, { userId: id, status: entry.status, lastSeenAt });
      notify();
    },
    replaceAllPresence(list) {
      presence.clear();
      (list ?? []).forEach((p) => presence.set(Number(p.userId), { userId: Number(p.userId), status: p.status, lastSeenAt: p.lastSeenAt ?? null }));
      notify();
    },
    markAttempted(userId) {
      const id = Number(userId);
      if (attempted.has(id)) return false;
      attempted.add(id);
      return true;
    },

    setTyping(conversationId, on) {
      const id = Number(conversationId);
      clearTimeout(typingTimers.get(id));
      typingTimers.delete(id);
      if (on) {
        typingTimers.set(id, setTimeout(() => this.setTyping(id, false), TYPING_EXPIRY_MS));
        if (!typing.has(id)) {
          typing.set(id, true);
          notify();
        }
      } else if (typing.delete(id)) {
        notify();
      }
    },
    clearTyping() {
      typingTimers.forEach(clearTimeout);
      typingTimers.clear();
      if (typing.size) {
        typing.clear();
        notify();
      }
    },
    reset() {
      this.clearTyping();
      presence.clear();
      attempted.clear();
      notify();
    },
  };
}

export const PresenceContext = createContext(null);

export function usePresenceStore() {
  const store = useContext(PresenceContext);
  if (!store) throw new Error('usePresence must be used inside <PresenceProvider>');
  return store;
}

export function PresenceProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const storeRef = useRef(null);
  storeRef.current ??= createPresenceStore();
  const store = storeRef.current;

  useEffect(() => {
    if (!isAuthenticated) {
      store.reset();
      return undefined;
    }
    websocketService.retain();

    const loadPartners = () =>
      presenceService
        .getPartnersPresence()
        .then((res) => store.replaceAllPresence(res.data))
        .catch(() => {
        });

    const offs = [
      websocketService.onPresenceEvent((e) => store.setPresence(e)),
      websocketService.onTypingEvent((e) => store.setTyping(e.conversationId, !!e.typing)),
      websocketService.onStatus((s) => {
        if (s === 'connected') loadPartners();
        else store.clearTyping();
      }),
    ];
    if (websocketService.getStatus() === 'connected') loadPartners();

    return () => {
      offs.forEach((off) => off());
      websocketService.release();
      store.reset();
    };
  }, [isAuthenticated, store]);

  return <PresenceContext.Provider value={store}>{children}</PresenceContext.Provider>;
}
