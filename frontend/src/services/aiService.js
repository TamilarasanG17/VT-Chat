import api, { API_URL, refreshSession } from './api';
import { tokenStore } from '../utils/tokenStore';
import { getErrorMessage } from '../utils/errors';

export class AiRequestError extends Error {
  constructor(message, { status = 0, code = '', retryAfterSeconds = 0, aborted = false } = {}) {
    super(message);
    this.name = 'AiRequestError';
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
    this.aborted = aborted;
  }
}

export function toAiError(err) {
  if (err instanceof AiRequestError) return err;
  if (err?.name === 'AbortError' || err?.code === 'ERR_CANCELED') return new AiRequestError('Stopped', { aborted: true });
  const data = err?.response?.data;
  return new AiRequestError(getErrorMessage(err, 'The AI could not answer. Please try again.'), {
    status: err?.response?.status || 0,
    code: data?.code || '',
    retryAfterSeconds: Number(data?.retryAfterSeconds ?? data?.retryAfter) || 0,
  });
}

export const newRequestId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID().replace(/-/g, '');
  return Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join('');
};

const unwrap = (res) => res.data.data;
const withRequestId = (extra = {}) => ({ headers: { 'X-Request-Id': newRequestId() }, ...extra });

export const supportsStreaming = () =>
  typeof fetch === 'function' && typeof ReadableStream !== 'undefined' && typeof TextDecoder !== 'undefined';

async function openStream(url, body, signal, token) {
  return fetch(url, {
    method: 'POST',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json',
      Authorization: `Bearer ${token}`,
      'X-Request-Id': newRequestId(),
    },
    body: JSON.stringify(body),
  });
}

async function readEvents(response, handler) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let boundary;
      while ((boundary = buffer.indexOf('\n\n')) !== -1) {
        const raw = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        let event = 'message';
        const data = [];
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim();
          else if (line.startsWith('data:')) data.push(line.slice(5).trim());
        }
        let parsed = {};
        try {
          parsed = data.length ? JSON.parse(data.join('\n')) : {};
        } catch {
          continue;
        }
        handler(event, parsed);
      }
    }
  } finally {
    reader.cancel().catch(() => {});
  }
}

export const aiService = {
  listConversations: () => api.get('/ai/conversations').then(unwrap),
  createConversation: (title) => api.post('/ai/conversations', title ? { title } : {}).then(unwrap),
  getConversation: (id) => api.get(`/ai/conversations/${id}`).then(unwrap),
  deleteConversation: (id) => api.delete(`/ai/conversations/${id}`).then(unwrap),
  getMessages: (id, { before, limit = 100 } = {}) =>
    api.get(`/ai/conversations/${id}/messages`, { params: { before, limit } }).then(unwrap),

  sendMessage: (id, message, signal, rag) =>
    rag
      ? api.post(`/ai/conversations/${id}/rag`, { message, documentIds: rag.documentIds }, withRequestId({ signal, timeout: 120000 })).then(unwrap)
      : api.post(`/ai/conversations/${id}/messages`, { message }, withRequestId({ signal, timeout: 120000 })).then(unwrap),

  async streamMessage(id, message, { signal, onStart, onDelta, onDone, rag }) {
    const url = `${API_URL}/ai/conversations/${id}/${rag ? 'rag/stream' : 'messages/stream'}`;
    const body = rag ? { message, documentIds: rag.documentIds } : { message };
    try {
      let response = await openStream(url, body, signal, tokenStore.get());
      if (response.status === 401) {
        const session = await refreshSession();
        response = await openStream(url, body, signal, session.accessToken);
      }
      if (!response.ok) {
        let data = {};
        try {
          data = await response.json();
        } catch {
        }
        const retry = Number(data.retryAfterSeconds ?? data.retryAfter ?? response.headers.get('Retry-After')) || 0;
        throw new AiRequestError(data.message || 'The AI could not answer. Please try again.', {
          status: response.status,
          code: data.code || '',
          retryAfterSeconds: retry,
        });
      }
      if (!response.body) throw new AiRequestError('Streaming is not supported by this browser.');

      let finished = false;
      let failure = null;
      await readEvents(response, (event, data) => {
        if (event === 'start') onStart?.(data.userMessage);
        else if (event === 'delta') onDelta?.(data.text || '');
        else if (event === 'done') {
          finished = true;
          onDone?.(data);
        } else if (event === 'error') {
          failure = new AiRequestError(data.message || 'The AI could not finish that answer.', {
            code: data.code || '',
            retryAfterSeconds: Number(data.retryAfterSeconds) || 0,
          });
        }
      });
      if (failure) throw failure;
      if (!finished) throw new AiRequestError('The connection to the AI was interrupted. Please try again.');
    } catch (err) {
      if (err instanceof AiRequestError) throw err;
      if (err?.name === 'AbortError') throw new AiRequestError('Stopped', { aborted: true });
      if (err instanceof TypeError) throw new AiRequestError('Cannot reach the server. Check your connection and try again.');
      throw toAiError(err);
    }
  },
};
