import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_URL } from './api';
import { tokenStore } from '../utils/tokenStore';

const WS_URL = new URL('/ws', new URL(API_URL, window.location.origin)).href;
const ERROR_QUEUE = '/user/queue/errors';
const USER_EVENT_QUEUE = '/user/queue/messages';
const CALL_QUEUE = '/user/queue/call';
const PRESENCE_QUEUE = '/user/queue/presence';
const TYPING_QUEUE = '/user/queue/typing';
const NOTIFICATION_QUEUE = '/user/queue/notifications';
const IDLE_DISCONNECT_MS = 2000;
const MAX_CONSECUTIVE_REJECTIONS = 3;

let client = null;
let status = 'disconnected';
let retainCount = 0;
let idleTimer = null;
let attempt = 0;
let rejections = 0;
let usedToken = null;
let hasConnectedBefore = false;

const statusListeners = new Set();
const errorListeners = new Set();
const userEventListeners = new Set();
const callListeners = new Set();
const presenceListeners = new Set();
const typingListeners = new Set();
const notificationListeners = new Set();
const reconnectListeners = new Set();
const topics = new Map();

function setStatus(next) {
  if (status === next) return;
  status = next;
  statusListeners.forEach((fn) => fn(next));
}

function parse(frame) {
  try {
    return JSON.parse(frame.body);
  } catch {
    return null;
  }
}

function attachTopic(destination, entry) {
  if (entry.subscription || !client?.connected) return;
  entry.subscription = client.subscribe(destination, (frame) => {
    const message = parse(frame);
    if (message) entry.listeners.forEach((fn) => fn(message));
  });
}

function attachAll() {
  client.subscribe(ERROR_QUEUE, (frame) => {
    const error = parse(frame);
    if (error) errorListeners.forEach((fn) => fn(error));
  });
  client.subscribe(USER_EVENT_QUEUE, (frame) => {
    const event = parse(frame);
    if (event) userEventListeners.forEach((fn) => fn(event));
  });
  client.subscribe(PRESENCE_QUEUE, (frame) => {
    const event = parse(frame);
    if (event) presenceListeners.forEach((fn) => fn(event));
  });
  client.subscribe(TYPING_QUEUE, (frame) => {
    const event = parse(frame);
    if (event) typingListeners.forEach((fn) => fn(event));
  });
  client.subscribe(NOTIFICATION_QUEUE, (frame) => {
    const event = parse(frame);
    if (event) notificationListeners.forEach((fn) => fn(event));
  });
  client.subscribe(CALL_QUEUE, (frame) => {
    const event = parse(frame);
    if (event) callListeners.forEach((fn) => fn(event));
  });
  topics.forEach((entry, destination) => attachTopic(destination, entry));
}

function forgetSubscriptions() {
  topics.forEach((entry) => {
    entry.subscription = null;
  });
}

function createClient() {
  const c = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: 1000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: () => {
      usedToken = tokenStore.get();
      c.connectHeaders = { Authorization: `Bearer ${usedToken ?? ''}` };
    },
    onConnect: () => {
      if (c !== client) return;
      attempt = 0;
      rejections = 0;
      const isReconnect = hasConnectedBefore;
      hasConnectedBefore = true;
      attachAll();
      setStatus('connected');
      if (isReconnect) reconnectListeners.forEach((fn) => fn());
    },
    onStompError: () => {
      if (c !== client) return;
      rejections += 1;
      if (rejections >= MAX_CONSECUTIVE_REJECTIONS) {
        disconnect(true);
      }
    },
    onWebSocketClose: () => {
      if (c !== client) return;
      forgetSubscriptions();
      attempt += 1;
      c.reconnectDelay = Math.min(1000 * 2 ** (attempt - 1), 15000);
      const offline = typeof navigator !== 'undefined' && navigator.onLine === false;
      setStatus(offline ? 'disconnected' : hasConnectedBefore ? 'reconnecting' : 'connecting');
    },
  });
  return c;
}

function connect() {
  if (client) return;
  clearTimeout(idleTimer);
  client = createClient();
  setStatus(hasConnectedBefore ? 'reconnecting' : 'connecting');
  client.activate();
}

function disconnect(force = false) {
  clearTimeout(idleTimer);
  const old = client;
  client = null;
  forgetSubscriptions();
  if (force) {
    topics.clear();
    errorListeners.clear();
    userEventListeners.clear();
    callListeners.clear();
    presenceListeners.clear();
    typingListeners.clear();
    notificationListeners.clear();
    reconnectListeners.clear();
  }
  hasConnectedBefore = false;
  attempt = 0;
  rejections = 0;
  usedToken = null;
  setStatus('disconnected');
  if (old) void old.deactivate();
}

export const websocketService = {
  retain() {
    retainCount += 1;
    clearTimeout(idleTimer);
    if (tokenStore.get()) connect();
  },

  release() {
    retainCount = Math.max(0, retainCount - 1);
    if (retainCount === 0) {
      clearTimeout(idleTimer);
      idleTimer = setTimeout(() => {
        if (retainCount === 0) disconnect(false);
      }, IDLE_DISCONNECT_MS);
    }
  },

  disconnect,

  refreshAuth() {
    if (!client || usedToken === tokenStore.get()) return;
    const old = client;
    client = null;
    forgetSubscriptions();
    void old.deactivate();
    connect();
  },

  getStatus: () => status,

  onStatus(listener) {
    statusListeners.add(listener);
    return () => statusListeners.delete(listener);
  },

  onError(listener) {
    errorListeners.add(listener);
    return () => errorListeners.delete(listener);
  },

  subscribeToConversation(conversationId, onMessage) {
    const destination = `/topic/chat/${conversationId}`;
    let entry = topics.get(destination);
    if (!entry) {
      entry = { listeners: new Set(), subscription: null };
      topics.set(destination, entry);
    }
    entry.listeners.add(onMessage);
    attachTopic(destination, entry);

    return () => {
      entry.listeners.delete(onMessage);
      if (entry.listeners.size === 0) {
        try {
          entry.subscription?.unsubscribe();
        } catch {
        }
        if (topics.get(destination) === entry) topics.delete(destination);
      }
    };
  },

  onUserEvent(listener) {
    userEventListeners.add(listener);
    return () => userEventListeners.delete(listener);
  },

  onCallEvent(listener) {
    callListeners.add(listener);
    return () => callListeners.delete(listener);
  },

  onPresenceEvent(listener) {
    presenceListeners.add(listener);
    return () => presenceListeners.delete(listener);
  },

  onTypingEvent(listener) {
    typingListeners.add(listener);
    return () => typingListeners.delete(listener);
  },

  onNotificationEvent(listener) {
    notificationListeners.add(listener);
    return () => notificationListeners.delete(listener);
  },

  onReconnect(listener) {
    reconnectListeners.add(listener);
    return () => reconnectListeners.delete(listener);
  },

  isConnected: () => !!client?.connected,

  publish(destination, payload) {
    if (!client?.connected) return false;
    client.publish({
      destination,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return true;
  },
};
