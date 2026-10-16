import api, { API_URL } from './api';
import { getErrorMessage, getErrorCode } from '../utils/errors';
import { BROWSER_VIEWABLE, extensionOfName } from '../utils/ai';
import { absoluteMediaUrl } from './mediaService';

export class KnowledgeError extends Error {
  constructor(message, { status = 0, code = '' } = {}) {
    super(message);
    this.name = 'KnowledgeError';
    this.status = status;
    this.code = code;
  }
}

const fail = (err, fallback) =>
  new KnowledgeError(getErrorMessage(err, fallback), { status: err?.response?.status || 0, code: getErrorCode(err) || '' });

const unwrap = (res) => res.data.data;

export const knowledgeService = {
  list: () => api.get('/ai/knowledge/documents').then(unwrap).catch((e) => Promise.reject(fail(e, 'Could not load your documents.'))),

  get: (id) => api.get(`/ai/knowledge/documents/${id}`).then(unwrap).catch((e) => Promise.reject(fail(e, 'Could not load this document.'))),

  upload(file, { onProgress, signal } = {}) {
    const form = new FormData();
    form.append('file', file);
    return api
      .post('/ai/knowledge/documents', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 0,
        signal,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.min(100, Math.round((e.loaded / e.total) * 100)));
        },
      })
      .then(unwrap)
      .catch((e) => Promise.reject(fail(e, 'The upload failed. Please try again.')));
  },

  remove: (id) => api.delete(`/ai/knowledge/documents/${id}`).then(unwrap).catch((e) => Promise.reject(fail(e, 'Could not delete this document.'))),

  reprocess: (id) =>
    api.post(`/ai/knowledge/documents/${id}/reprocess`).then(unwrap).catch((e) => Promise.reject(fail(e, 'Could not start processing.'))),

  access: (id) => api.get(`/ai/knowledge/documents/${id}/access`).then(unwrap),

  async open(documentId, fileName, page) {
    const viewable = BROWSER_VIEWABLE.includes(extensionOfName(fileName));
    let tab = null;
    if (viewable) {
      tab = window.open('about:blank', '_blank');
      if (!tab) throw new KnowledgeError('Your browser blocked the new tab. Allow pop-ups for this site and try again.', { code: 'POPUP_BLOCKED' });
      tab.opener = null;
    }
    try {
      const access = await knowledgeService.access(documentId);
      if (viewable && access.viewUrl) {
        const suffix = page && extensionOfName(fileName) === 'pdf' ? `#page=${page}` : '';
        tab.location.href = `${absoluteMediaUrl(API_URL, access.viewUrl)}${suffix}`;
        return;
      }
      tab?.close();
      const a = document.createElement('a');
      a.href = absoluteMediaUrl(API_URL, access.downloadUrl);
      a.rel = 'noopener';
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch (err) {
      tab?.close();
      throw err instanceof KnowledgeError ? err : fail(err, 'Could not open this document.');
    }
  },
};
