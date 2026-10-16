import api from './api';

const SAFETY_MS = 60_000;
const cache = new Map();
const inflight = new Map();

const multipart = { 'Content-Type': 'multipart/form-data' };

function post(url, form, { onProgress, signal } = {}) {
  return api
    .post(url, form, {
      headers: multipart,
      timeout: 0,
      signal,
      onUploadProgress: (e) => {
        if (onProgress && e.total) onProgress(Math.min(100, Math.round((e.loaded / e.total) * 100)));
      },
    })
    .then((res) => res.data.data.message);
}

export const mediaService = {
  uploadMedia(conversationId, { file, caption, replyToMessageId, onProgress, signal }) {
    const form = new FormData();
    form.append('file', file);
    if (caption) form.append('caption', caption);
    if (replyToMessageId) form.append('replyToMessageId', String(replyToMessageId));
    return post(`/conversations/${conversationId}/media`, form, { onProgress, signal });
  },

  uploadVoice(conversationId, { blob, duration, replyToMessageId, onProgress, signal }) {
    const ext = blob.type.includes('ogg') ? 'ogg' : blob.type.includes('mp4') ? 'm4a' : blob.type.includes('wav') ? 'wav' : 'webm';
    const form = new FormData();
    form.append('file', blob, `voice.${ext}`);
    form.append('duration', String(duration));
    if (replyToMessageId) form.append('replyToMessageId', String(replyToMessageId));
    return post(`/conversations/${conversationId}/voice`, form, { onProgress, signal });
  },

  getAccess(messageId, { fresh = false } = {}) {
    const hit = cache.get(messageId);
    if (!fresh && hit && hit.expiresAt > Date.now()) return Promise.resolve(hit.value);
    if (inflight.has(messageId)) return inflight.get(messageId);
    const request = api
      .get(`/media/${messageId}/access`)
      .then((res) => {
        const value = res.data.data;
        cache.set(messageId, { value, expiresAt: Date.now() + value.expiresIn * 1000 - SAFETY_MS });
        return value;
      })
      .finally(() => inflight.delete(messageId));
    inflight.set(messageId, request);
    return request;
  },

  async download(messageId, apiBase) {
    const access = await mediaService.getAccess(messageId);
    const a = document.createElement('a');
    a.href = `${apiBase.replace(/\/api$/, '')}${access.downloadUrl}`;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
  },

  peekAccess(messageId) {
    const hit = cache.get(messageId);
    return hit && hit.expiresAt > Date.now() ? hit.value : null;
  },

  async openInNewTab(messageId, apiBase) {
    const target = (a) => `${apiBase.replace(/\/api$/, '')}${a.viewUrl}`;
    const cached = mediaService.peekAccess(messageId);
    if (cached) {
      const w = window.open(target(cached), '_blank', 'noopener,noreferrer');
      if (!w) throw new Error('POPUP_BLOCKED');
      return;
    }
    const w = window.open('about:blank', '_blank');
    if (!w) throw new Error('POPUP_BLOCKED');
    try {
      w.opener = null;
      const access = await mediaService.getAccess(messageId);
      w.location.href = target(access);
    } catch (err) {
      w.close();
      throw err;
    }
  },

  forget(messageId) {
    cache.delete(messageId);
  },
};

export const absoluteMediaUrl = (apiBase, path) => (path ? `${apiBase.replace(/\/api$/, '')}${path}` : null);
