import { useCallback, useEffect, useRef, useState } from 'react';
import { chatRequestService, CHAT_REQUEST_EVENTS } from '../services/chatRequestService';
import { websocketService } from '../services/websocketService';
import { getChatErrorMessage } from '../utils/errors';

export function useChatRequests({ onEvent } = {}) {
  const [state, setState] = useState({ status: 'loading', items: [], error: '' });
  const alive = useRef(true);
  const seq = useRef(0);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const load = useCallback(async ({ quiet = false } = {}) => {
    const mine = ++seq.current;
    if (!quiet) setState((s) => ({ ...s, status: s.items.length ? s.status : 'loading', error: '' }));
    try {
      const res = await chatRequestService.received();
      if (alive.current && mine === seq.current) setState({ status: 'ready', items: res.data ?? [], error: '' });
    } catch (err) {
      if (alive.current && mine === seq.current) {
        setState((s) => ({ ...s, status: 'error', error: getChatErrorMessage(err, 'Unable to load message requests') }));
      }
    }
  }, []);

  useEffect(() => {
    alive.current = true;
    load();
    const offEvents = websocketService.onUserEvent((event) => {
      if (!CHAT_REQUEST_EVENTS.includes(event?.type)) return;
      load({ quiet: true });
      onEventRef.current?.(event);
    });
    const offReconnect = websocketService.onReconnect(() => load({ quiet: true }));
    return () => {
      alive.current = false;
      offEvents();
      offReconnect();
    };
  }, [load]);

  const remove = useCallback((id) => setState((s) => ({ ...s, items: s.items.filter((r) => r.id !== id) })), []);

  return { ...state, count: state.items.length, reload: load, remove };
}
