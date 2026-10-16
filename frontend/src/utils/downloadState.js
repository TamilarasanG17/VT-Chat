import { useSyncExternalStore } from 'react';

const done = new Set();
const listeners = new Set();

const key = (userId, messageId) => `${userId}:${messageId}`;

export const downloadState = {
  has: (userId, messageId) => done.has(key(userId, messageId)),
  mark(userId, messageId) {
    const k = key(userId, messageId);
    if (done.has(k)) return;
    done.add(k);
    listeners.forEach((l) => l());
  },
  subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  clear() {
    done.clear();
    listeners.forEach((l) => l());
  },
};

export const useDownloaded = (userId, messageId) =>
  useSyncExternalStore(downloadState.subscribe, () => downloadState.has(userId, messageId));

export function canShowDownload(message, userId, downloadedLocally = false) {
  if (!message || message.messageType !== 'FILE' || message.deleted) return false;
  if (message.senderId === userId) return false;
  if (message.downloadableByCurrentUser === false) return false;
  if (message.downloadedByCurrentUser === true || downloadedLocally) return false;
  return true;
}

export const isPdf = (message) =>
  message?.mimeType === 'application/pdf' || (!message?.mimeType && /\.pdf$/i.test(message?.mediaName || ''));
