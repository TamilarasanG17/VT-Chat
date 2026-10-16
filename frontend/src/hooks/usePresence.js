import { useEffect, useSyncExternalStore } from 'react';
import { usePresenceStore } from '../context/PresenceContext';
import { presenceService } from '../services/presenceService';

export function usePresence(userId, { fetch = false } = {}) {
  const store = usePresenceStore();
  const presence = useSyncExternalStore(store.subscribe, () => (userId == null ? undefined : store.getPresence(userId)));

  useEffect(() => {
    if (!fetch || userId == null || presence || !store.markAttempted(userId)) return undefined;
    let active = true;
    presenceService
      .getPresence(userId)
      .then((res) => active && store.setPresence(res.data))
      .catch(() => {
      });
    return () => {
      active = false;
    };
  }, [fetch, userId, presence, store]);

  return presence;
}
