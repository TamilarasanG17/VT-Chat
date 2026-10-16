import { useEffect, useRef, useSyncExternalStore } from 'react';
import { useAuth } from './useAuth';
import { websocketService } from '../services/websocketService';

export function useChatSocket() {
  const { accessToken } = useAuth();
  const status = useSyncExternalStore(websocketService.onStatus, websocketService.getStatus);

  useEffect(() => {
    websocketService.retain();
    return () => websocketService.release();
  }, []);

  const lastToken = useRef(accessToken);
  useEffect(() => {
    if (accessToken && lastToken.current && accessToken !== lastToken.current) {
      websocketService.refreshAuth();
    }
    lastToken.current = accessToken;
  }, [accessToken]);

  return {
    status,
    isConnected: status === 'connected',
    subscribe: websocketService.subscribeToConversation,
    publish: websocketService.publish,
    onUserEvent: websocketService.onUserEvent,
    onSendError: websocketService.onError,
  };
}
