import { useCallback, useEffect, useRef, useState } from 'react';
import { aiService, supportsStreaming, toAiError } from '../services/aiService';
import { AI_MAX_MESSAGE_LENGTH, MODE_NORMAL, MODE_RAG, RAG_MAX_QUERY_LENGTH } from '../utils/ai';

let tempCounter = 0;
const tempKey = () => `tmp-${Date.now()}-${tempCounter++}`;
const fromServer = (m) => ({
  key: `m-${m.id}`,
  id: m.id,
  role: m.role,
  content: m.content,
  createdAt: m.createdAt,
  mode: m.mode || MODE_NORMAL,
  sources: m.sources || [],
});

const NOT_STORED = new Set([
  'AI_MESSAGE_TOO_LONG',
  'AI_GENERATION_IN_PROGRESS',
  'AI_CONVERSATION_LIMIT',
  'RATE_LIMITED',
  'RAG_QUERY_TOO_LONG',
  'NO_READY_DOCUMENTS',
  'KNOWLEDGE_DISABLED',
]);

export function useAIChat() {
  const [conversations, setConversations] = useState([]);
  const [listStatus, setListStatus] = useState('loading');
  const [listError, setListError] = useState('');
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [messagesStatus, setMessagesStatus] = useState('ready');
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);
  const [blockedUntil, setBlockedUntil] = useState(0);
  const [mode, setModeState] = useState(MODE_NORMAL);

  const generatingRef = useRef(false);
  const abortRef = useRef(null);
  const activeRef = useRef(null);
  const mountedRef = useRef(true);
  const pendingRef = useRef({ key: null, text: '' });
  const rafRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      cancelAnimationFrame(rafRef.current);
      abortRef.current?.abort();
    };
  }, []);

  const loadConversations = useCallback(async () => {
    setListStatus('loading');
    try {
      const list = await aiService.listConversations();
      if (!mountedRef.current) return;
      setConversations(list);
      setListStatus('ready');
    } catch (err) {
      if (!mountedRef.current) return;
      setListError(toAiError(err).message);
      setListStatus('error');
    }
  }, []);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  const loadMessages = useCallback(async (id, { silent = false } = {}) => {
    if (!silent) setMessagesStatus('loading');
    try {
      const list = await aiService.getMessages(id);
      if (!mountedRef.current || activeRef.current !== id) return;
      setMessages(list.map(fromServer));
      setMessagesStatus('ready');
    } catch (err) {
      if (!mountedRef.current || activeRef.current !== id || silent) return;
      setError({ message: toAiError(err).message, code: 'LOAD_FAILED' });
      setMessagesStatus('error');
    }
  }, []);

  const open = useCallback(
    (id) => {
      if (generatingRef.current || id === activeRef.current) return;
      activeRef.current = id;
      setActiveId(id);
      setMessages([]);
      setError(null);
      if (id) loadMessages(id);
    },
    [loadMessages],
  );

  const newChat = useCallback(() => {
    if (generatingRef.current) return;
    activeRef.current = null;
    setActiveId(null);
    setMessages([]);
    setMessagesStatus('ready');
    setError(null);
  }, []);

  const remove = useCallback(async (id) => {
    if (generatingRef.current && activeRef.current === id) return false;
    try {
      await aiService.deleteConversation(id);
    } catch (err) {
      setError({ message: toAiError(err).message, code: 'DELETE_FAILED' });
      return false;
    }
    setConversations((list) => list.filter((c) => c.id !== id));
    if (activeRef.current === id) {
      activeRef.current = null;
      setActiveId(null);
      setMessages([]);
      setError(null);
    }
    return true;
  }, []);

  const flushPending = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    rafRef.current = 0;
    const { key, text } = pendingRef.current;
    if (!key || !text) return;
    pendingRef.current = { key, text: '' };
    if (!mountedRef.current) return;
    setMessages((list) => list.map((m) => (m.key === key ? { ...m, content: m.content + text } : m)));
  }, []);

  const queueDelta = useCallback(
    (key, text) => {
      pendingRef.current = { key, text: pendingRef.current.text + text };
      if (!rafRef.current) rafRef.current = requestAnimationFrame(flushPending);
    },
    [flushPending],
  );

  const setMode = useCallback((next) => {
    if (generatingRef.current) return;
    setModeState(next === MODE_RAG ? MODE_RAG : MODE_NORMAL);
    setError(null);
  }, []);

  const send = useCallback(
    async (rawText, { documentIds = [] } = {}) => {
      const text = (rawText || '').trim();
      if (!text || generatingRef.current) return false;
      const rag = mode === MODE_RAG ? { documentIds } : null;
      const limit = rag ? Math.min(AI_MAX_MESSAGE_LENGTH, RAG_MAX_QUERY_LENGTH) : AI_MAX_MESSAGE_LENGTH;
      if (text.length > limit) {
        setError({ message: `${rag ? 'Question' : 'Message'} is too long (maximum ${limit} characters).`, code: rag ? 'RAG_QUERY_TOO_LONG' : 'AI_MESSAGE_TOO_LONG' });
        return false;
      }
      if (blockedUntil > Date.now()) return false;

      generatingRef.current = true;
      setGenerating(true);
      setError(null);
      const controller = new AbortController();
      abortRef.current = controller;

      let convId = activeRef.current;
      const userKey = tempKey();
      const aiKey = tempKey();
      let accepted = true;

      try {
        if (!convId) {
          const created = await aiService.createConversation();
          if (!mountedRef.current) return true;
          convId = created.id;
          activeRef.current = convId;
          setActiveId(convId);
          setConversations((list) => [created, ...list]);
        }
        setMessages((list) => [
          ...list,
          { key: userKey, role: 'USER', content: text, createdAt: new Date().toISOString(), pending: true, mode: rag ? MODE_RAG : MODE_NORMAL, sources: [] },
          { key: aiKey, role: 'ASSISTANT', content: '', streaming: true, createdAt: new Date().toISOString(), mode: rag ? MODE_RAG : MODE_NORMAL, sources: [] },
        ]);

        const apply = {
          onStart: (saved) => setMessages((list) => list.map((m) => (m.key === userKey ? { ...fromServer(saved) } : m))),
          onDelta: (piece) => queueDelta(aiKey, piece),
          onDone: (result) => {
            flushPending();
            setMessages((list) =>
              list.map((m) => {
                if (m.key === userKey) return fromServer(result.userMessage);
                if (m.key === aiKey) return fromServer(result.assistantMessage);
                return m;
              }),
            );
            setConversations((list) => {
              const rest = list.filter((c) => c.id !== result.conversation.id);
              return [result.conversation, ...rest];
            });
          },
        };

        if (supportsStreaming()) {
          await aiService.streamMessage(convId, text, { signal: controller.signal, rag, ...apply });
        } else {
          const result = await aiService.sendMessage(convId, text, controller.signal, rag);
          apply.onDone(result);
        }
      } catch (raw) {
        const err = toAiError(raw);
        flushPending();
        if (!mountedRef.current) return true;
        pendingRef.current = { key: null, text: '' };
        if (err.aborted) {
          if (convId) await loadMessages(convId, { silent: true });
        } else {
          accepted = convId ? !(NOT_STORED.has(err.code) || err.status === 401) : false;
          if (err.code === 'RATE_LIMITED' || err.code === 'AI_BUSY') {
            setBlockedUntil(Date.now() + (err.retryAfterSeconds || 30) * 1000);
          }
          setError({ message: err.message, code: err.code, text });
          if (convId) await loadMessages(convId, { silent: true });
          else setMessages([]);
        }
      } finally {
        generatingRef.current = false;
        abortRef.current = null;
        if (mountedRef.current) setGenerating(false);
      }
      return accepted;
    },
    [blockedUntil, flushPending, loadMessages, mode, queueDelta],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const dismissError = useCallback(() => setError(null), []);

  return {
    conversations,
    listStatus,
    listError,
    reloadList: loadConversations,
    activeId,
    messages,
    messagesStatus,
    generating,
    error,
    dismissError,
    blockedUntil,
    mode,
    setMode,
    open,
    newChat,
    remove,
    send,
    stop,
  };
}
