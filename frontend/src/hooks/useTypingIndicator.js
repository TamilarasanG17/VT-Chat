import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { usePresenceStore } from '../context/PresenceContext';
import { websocketService } from '../services/websocketService';

const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n));
const IDLE_MS = clamp(Number(import.meta.env.VITE_TYPING_IDLE_MS) || 2500, 1500, 3000);
const REFRESH_MS = 3000;

export function useTypingIndicator(conversationId) {
  const store = usePresenceStore();
  const isTyping = useSyncExternalStore(store.subscribe, () => (conversationId == null ? false : store.isTyping(conversationId)));

  const local = useRef({ typing: false, lastSent: 0, timer: null });

  const send = useCallback(
    (typing) => websocketService.publish('/app/chat.typing', { conversationId, typing }),
    [conversationId],
  );

  const stopTyping = useCallback(() => {
    const s = local.current;
    clearTimeout(s.timer);
    s.timer = null;
    if (s.typing) {
      s.typing = false;
      send(false);
    }
  }, [send]);

  const notifyTyping = useCallback(() => {
    if (conversationId == null) return;
    const s = local.current;
    const now = Date.now();
    if (!s.typing) {
      s.typing = true;
      s.lastSent = now;
      send(true);
    } else if (now - s.lastSent >= REFRESH_MS) {
      s.lastSent = now;
      send(true);
    }
    clearTimeout(s.timer);
    s.timer = setTimeout(stopTyping, IDLE_MS);
  }, [conversationId, send, stopTyping]);

  useEffect(() => stopTyping, [stopTyping]);

  useEffect(() => {
    const forget = () => {
      const s = local.current;
      clearTimeout(s.timer);
      s.timer = null;
      s.typing = false;
    };
    const offStatus = websocketService.onStatus((status) => {
      if (status !== 'connected') forget();
    });
    const onHidden = () => {
      if (document.hidden) stopTyping();
    };
    document.addEventListener('visibilitychange', onHidden);
    window.addEventListener('pagehide', stopTyping);
    return () => {
      offStatus();
      document.removeEventListener('visibilitychange', onHidden);
      window.removeEventListener('pagehide', stopTyping);
    };
  }, [stopTyping]);

  return { isTyping, notifyTyping, stopTyping };
}
