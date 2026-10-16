import { useCallback, useEffect, useRef, useState } from 'react';
import { API_URL } from '../services/api';
import { absoluteMediaUrl, mediaService } from '../services/mediaService';

export function useMediaUrls(messageId, enabled = true) {
  const [state, setState] = useState({ status: 'idle', url: null, thumbnailUrl: null, error: '' });
  const retries = useRef(0);

  const load = useCallback(
    (fresh) => {
      let active = true;
      setState((s) => ({ ...s, status: 'loading', error: '' }));
      mediaService
        .getAccess(messageId, { fresh })
        .then((a) => {
          if (!active) return;
          setState({
            status: 'ready',
            url: absoluteMediaUrl(API_URL, a.url),
            thumbnailUrl: absoluteMediaUrl(API_URL, a.thumbnailUrl),
            error: '',
          });
        })
        .catch((err) => {
          if (!active) return;
          const gone = err?.response?.status === 404;
          setState({ status: 'error', url: null, thumbnailUrl: null, error: gone ? 'This file is no longer available' : 'Unable to load this file' });
        });
      return () => {
        active = false;
      };
    },
    [messageId],
  );

  useEffect(() => {
    if (!enabled) return undefined;
    retries.current = 0;
    return load(false);
  }, [enabled, load]);

  const reload = useCallback(() => {
    if (retries.current >= 3) {
      setState((s) => ({ ...s, status: 'error', error: 'Unable to load this file' }));
      return;
    }
    retries.current += 1;
    mediaService.forget(messageId);
    load(true);
  }, [load, messageId]);

  const retry = useCallback(() => {
    retries.current = 0;
    mediaService.forget(messageId);
    load(true);
  }, [load, messageId]);

  return { ...state, reload, retry };
}

export function useNearViewport() {
  const [near, setNear] = useState(typeof IntersectionObserver === 'undefined');
  const nodeRef = useRef(null);
  const ref = useCallback((node) => {
    nodeRef.current = node;
  }, []);

  useEffect(() => {
    const node = nodeRef.current;
    if (near || !node) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setNear(true);
          observer.disconnect();
        }
      },
      { rootMargin: '300px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [near]);

  return [ref, near];
}
