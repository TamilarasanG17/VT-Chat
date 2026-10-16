import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import Avatar from '../profile/Avatar.jsx';
import Spinner from '../Spinner.jsx';
import ComposerBanner from './ComposerBanner.jsx';
import ConnectionStatus from './ConnectionStatus.jsx';
import PresenceStatus from '../PresenceStatus.jsx';
import TypingIndicator from './TypingIndicator.jsx';
import { usePresence } from '../../hooks/usePresence';
import { useTypingIndicator } from '../../hooks/useTypingIndicator';
import ForwardModal from './ForwardModal.jsx';
import MessageActionMenu from './MessageActionMenu.jsx';
import MessageInput from './MessageInput.jsx';
import MessageList from './MessageList.jsx';
import MediaUpload from './MediaUpload.jsx';
import MessageSearchPanel from './MessageSearchPanel.jsx';
import SelectionBar from './SelectionBar.jsx';
import TranslateDialog from './TranslateDialog.jsx';
import VoiceRecorder from './VoiceRecorder.jsx';
import { ArrowLeftIcon, ClockIcon, PhoneIcon, SearchIcon, VideoIcon } from '../Icons.jsx';
import { useAuth } from '../../hooks/useAuth';
import { useChatSocket } from '../../hooks/useChatSocket';
import { API_URL } from '../../services/api';
import { conversationService } from '../../services/conversationService';
import { mediaService } from '../../services/mediaService';
import { downloadFileMessage } from '../../utils/fileDownload';
import { translationService } from '../../services/translationService';
import { messageActions } from '../../services/messageService';
import { chooseDeleteScope, showError, toastError, toastInfo, toastSuccess } from '../../utils/alerts';
import { copyText } from '../../utils/clipboard';
import { getChatErrorMessage, rateLimitMessage } from '../../utils/errors';
import { isCallError, isCallType } from '../../utils/calls';
import { useCall } from '../../hooks/useCall';
import { ChatPeerContext } from '../../context/ChatPeerContext';
import CallHistoryPanel from './CallHistoryPanel.jsx';
import ChatHeaderMenu from './ChatHeaderMenu.jsx';
import UserPreviewModal from './UserPreviewModal.jsx';
import { CHAT_REQUEST_EVENTS } from '../../services/chatRequestService';
import { validateFile } from '../../utils/media';
import { FALLBACK_LANGUAGES, rememberTargetLanguage, translationErrorMessage } from '../../utils/translation';
import { EVENT, createMessageEventHandler } from '../../utils/messageEvents';
import { MAX_MESSAGE_LENGTH, mergeMessages, messageLength, normalizeEmoji } from '../../utils/messages';

const headerBtn =
  'flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-[background-color,transform] focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30';

const SENDING_TIMEOUT_MS = 8000;
const REACTION_COOLDOWN_MS = 500;

export default function ChatPanel({ conversationId }) {
  const { user } = useAuth();
  const { status, isConnected, subscribe, publish, onSendError, onUserEvent } = useChatSocket();

  const [conversation, setConversation] = useState({ status: 'loading', data: null, error: '' });
  const [messages, setMessages] = useState([]);
  const [history, setHistory] = useState({ status: 'loading', error: '' });
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [composer, setComposer] = useState(null);
  const [focusKey, setFocusKey] = useState(0);
  const [selection, setSelection] = useState(() => new Set());
  const [menu, setMenu] = useState(null);
  const [forwarding, setForwarding] = useState(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [highlightId, setHighlightId] = useState(null);
  const [scrollTarget, setScrollTarget] = useState(null);
  const [attachment, setAttachment] = useState(null);
  const [translations, setTranslations] = useState({});
  const [translateTarget, setTranslateTarget] = useState(null);
  const [languages, setLanguages] = useState(FALLBACK_LANGUAGES);
  const [profileOpen, setProfileOpen] = useState(false);
  const [recording, setRecording] = useState(null);

  const translating = useRef(new Set());
  const liveIds = useRef(new Set());
  const hiddenIds = useRef(new Set());
  const pendingText = useRef('');
  const sendingTimer = useRef(null);
  const highlightTimer = useRef(null);
  const reacting = useRef(new Set());
  const messagesRef = useRef([]);
  messagesRef.current = messages;

  const numericId = /^\d+$/.test(conversationId) ? Number(conversationId) : null;
  const myId = user?.id;

  const stopSending = useCallback(() => {
    clearTimeout(sendingTimer.current);
    pendingText.current = '';
    setSending(false);
  }, []);

  useEffect(() => {
    if (numericId === null) {
      setConversation({ status: 'error', data: null, error: 'Conversation not found' });
      return undefined;
    }
    let active = true;
    setConversation({ status: 'loading', data: null, error: '' });
    conversationService
      .getConversation(numericId)
      .then((res) => active && setConversation({ status: 'ready', data: res.data, error: '' }))
      .catch((err) =>
        active &&
        setConversation({ status: 'error', data: null, error: getChatErrorMessage(err, 'Unable to load conversation') }),
      );
    return () => {
      active = false;
    };
  }, [numericId]);

  const ready = conversation.status === 'ready';
  const other = conversation.data?.otherUser;
  const messagingEnabled = conversation.data?.messagingEnabled !== false;

  const { startCall, inCall } = useCall();
  const presence = usePresence(other?.id, { fetch: true });
  const { isTyping, notifyTyping, stopTyping } = useTypingIndicator(numericId);

  const refreshConversation = useCallback(() => {
    if (numericId === null) return;
    conversationService
      .getConversation(numericId)
      .then((res) => setConversation((c) => (c.status === 'ready' && c.data?.id === res.data?.id ? { ...c, data: res.data } : c)))
      .catch(() => {});
  }, [numericId]);
  const otherId = other?.id;
  useEffect(() => {
    if (!ready || !otherId) return undefined;
    return onUserEvent((event) => {
      if (!CHAT_REQUEST_EVENTS.includes(event?.type)) return;
      if (event.otherUserId !== otherId && event.conversationId !== numericId) return;
      if (event.type === 'FRIEND_REMOVED') {
        setComposer(null);
        setRecording(null);
        setAttachment(null);
        stopTyping();
      }
      refreshConversation();
    });
  }, [ready, otherId, numericId, onUserEvent, refreshConversation, stopTyping]);
  useEffect(() => {
    if (ready && status === 'connected') refreshConversation();
  }, [ready, status, refreshConversation]);
  const handleDraftChange = useCallback(
    (value) => {
      setDraft(value);
      if (composer?.mode !== 'edit' && value.trim()) notifyTyping();
      else stopTyping();
    },
    [composer?.mode, notifyTyping, stopTyping],
  );
  const peerValue = useMemo(() => (other ? { conversationId: numericId, user: other } : null), [numericId, other]);
  const [callsOpen, setCallsOpen] = useState(false);

  const [searchParams, setSearchParams] = useSearchParams();
  useEffect(() => {
    if (searchParams.get('calls') === '1') {
      setCallsOpen(true);
      searchParams.delete('calls');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const location = useLocation();
  const clearedAt = location.state?.cleared;
  useEffect(() => {
    if (clearedAt) setMessages([]);
  }, [clearedAt]);
  const callButtonsDisabled = inCall || !isConnected || numericId === null || !other || !messagingEnabled;
  const nameOf = useCallback((senderId) => (senderId === myId ? 'You' : other?.username ?? 'User'), [myId, other?.username]);

  const handleEvent = useMemo(
    () =>
      createMessageEventHandler({
        conversationId: numericId,
        setMessages,
        hiddenIds: hiddenIds.current,
        onEvent: (event) => {
          if (event.type === EVENT.CREATED) {
            liveIds.current.add(event.message.id);
            if (event.message.senderId === myId) stopSending();
          } else if (event.type === EVENT.DELETED_FOR_ME) {
            setSelection((prev) => {
              if (!prev.has(event.messageId)) return prev;
              const next = new Set(prev);
              next.delete(event.messageId);
              return next;
            });
          } else if (event.type === EVENT.REACTION_UPDATED) {
            reacting.current.delete(event.messageId);
          }
        },
      }),
    [numericId, myId, stopSending],
  );

  useEffect(() => {
    if (!ready) return undefined;
    const offTopic = subscribe(numericId, handleEvent);
    const offUser = onUserEvent(handleEvent);
    return () => {
      offTopic();
      offUser();
    };
  }, [ready, numericId, subscribe, onUserEvent, handleEvent]);

  const loadHistory = useCallback(() => {
    if (numericId === null) return () => {};
    let active = true;
    setHistory((h) => ({ ...h, status: 'loading', error: '' }));
    conversationService
      .getMessages(numericId)
      .then((res) => {
        if (!active) return;
        setMessages((current) => mergeMessages(current, res.data ?? []));
        setHistory({ status: 'ready', error: '' });
      })
      .catch((err) => active && setHistory({ status: 'error', error: getChatErrorMessage(err, 'Unable to load messages') }));
    return () => {
      active = false;
    };
  }, [numericId]);

  const historyRequested = useRef(false);
  useEffect(() => {
    if (!ready) return undefined;
    if (!isConnected && historyRequested.current) return undefined;
    historyRequested.current = true;
    return loadHistory();
  }, [ready, isConnected, loadHistory]);

  const lastReadSent = useRef(0);
  useEffect(() => {
    lastReadSent.current = 0;
  }, [numericId]);
  useEffect(() => {
    if (isConnected) lastReadSent.current = 0;
  }, [isConnected]);
  const reportRead = useCallback(
    (upToId) => {
      if (!isConnected || numericId === null || upToId <= lastReadSent.current) return;
      if (publish('/app/chat.read', { conversationId: numericId, lastReadMessageId: upToId })) lastReadSent.current = upToId;
    },
    [isConnected, numericId, publish],
  );

  useEffect(() => {
    if (!ready || !isConnected || numericId === null) return undefined;
    const report = () =>
      publish('/app/chat.active', { conversationId: document.visibilityState === 'visible' ? numericId : null });
    report();
    document.addEventListener('visibilitychange', report);
    return () => {
      document.removeEventListener('visibilitychange', report);
      publish('/app/chat.active', { conversationId: null });
    };
  }, [ready, isConnected, numericId, publish]);

  useEffect(
    () =>
      onSendError((error) => {
        if (isCallError(error)) return;
        if (pendingText.current) setDraft((current) => current || pendingText.current);
        stopSending();
        const text = error.message || 'Something went wrong. Please try again.';
        if (error.status === 429 || error.code === 'RATE_LIMITED') toastError(rateLimitMessage(error.retryAfterSeconds));
        else if (error.status === 401) toastError('Your session expired. Reconnecting…');
        else if (error.code === 'FORBIDDEN') showError('No access', 'You do not have access to this conversation');
        else if (error.code === 'CONVERSATION_NOT_FOUND') showError('Not found', 'This conversation no longer exists');
        else if ([403, 404, 409, 422].includes(error.status)) toastError(text);
        else showError('Unable to complete that action', 'Something went wrong. Please try again.');
      }),
    [onSendError, stopSending],
  );

  useEffect(
    () => () => {
      clearTimeout(sendingTimer.current);
      clearTimeout(highlightTimer.current);
    },
    [],
  );

  const run = useCallback(
    async (action, fallbackMessage) => {
      try {
        return await action();
      } catch (err) {
        toastError(getChatErrorMessage(err, fallbackMessage));
        return undefined;
      }
    },
    [],
  );

  const toggleReaction = useCallback(
    async (message, emoji) => {
      if (message.deleted || reacting.current.has(message.id)) return;
      reacting.current.add(message.id);
      setTimeout(() => reacting.current.delete(message.id), REACTION_COOLDOWN_MS);

      const mine = message.reactions?.find((r) => r.userId === myId)?.reaction;
      const remove = normalizeEmoji(mine) === normalizeEmoji(emoji);
      const result = await run(
        () => (remove ? messageActions.unreact(message.id) : messageActions.react(message.id, emoji)),
        'Unable to react to message',
      );
      if (result?.data) {
        handleEvent({ type: EVENT.REACTION_UPDATED, conversationId: numericId, messageId: message.id, reactions: result.data });
      }
    },
    [myId, numericId, run, handleEvent],
  );

  const openMenu = useCallback((message, mine, anchor) => setMenu({ message, mine, anchor }), []);
  const closeMenu = useCallback(() => setMenu(null), []);

  const toggleSelect = useCallback((id) => {
    setSelection((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);
  const clearSelection = useCallback(() => setSelection(new Set()), []);

  const startReply = useCallback((message) => {
    setComposer({ mode: 'reply', message });
    setFocusKey((k) => k + 1);
  }, []);

  const startEdit = useCallback((message) => {
    setComposer({ mode: 'edit', message });
    setDraft(message.content);
    setFocusKey((k) => k + 1);
  }, []);

  const cancelComposer = useCallback(() => {
    setComposer((current) => {
      if (current?.mode === 'edit') setDraft('');
      return null;
    });
  }, []);

  const copyMessages = useCallback(async (list) => {
    const text = list
      .filter((m) => !m.deleted)
      .sort((a, b) => a.id - b.id)
      .map((m) => m.content)
      .join('\n');
    if (!text) {
      toastInfo('Nothing to copy');
      return;
    }
    if (await copyText(text)) toastSuccess(list.length > 1 ? 'Messages copied' : 'Message copied');
    else toastError('Unable to copy');
  }, []);

  const deleteMessages = useCallback(
    async (targets) => {
      const canDeleteForEveryone = targets.length > 0 && targets.every((m) => m.senderId === myId && !m.deleted);
      const scope = await chooseDeleteScope({ canDeleteForEveryone, count: targets.length });
      if (!scope) return;
      try {
        for (const m of targets) {
          const result = await messageActions.remove(m.id, scope);
          if (scope === 'ME' && result) {
            handleEvent({ type: EVENT.DELETED_FOR_ME, conversationId: numericId, messageId: m.id });
          } else if (scope === 'EVERYONE' && result?.data?.message) {
            handleEvent({ type: EVENT.DELETED_FOR_EVERYONE, conversationId: numericId, messageId: m.id, message: result.data.message });
          }
        }
        clearSelection();
      } catch (err) {
        await showError(
          scope === 'ME' ? 'Unable to delete message for you' : 'Unable to delete message',
          getChatErrorMessage(err, 'Unable to delete message'),
        );
      }
    },
    [myId, numericId, handleEvent, clearSelection],
  );

  const jumpTo = useCallback(
    async (messageId) => {
      if (!messagesRef.current.some((m) => m.id === messageId)) {
        try {
          const res = await conversationService.getMessages(numericId, messageId);
          setMessages((current) => mergeMessages(current, res.data ?? []));
        } catch (err) {
          toastError(getChatErrorMessage(err, 'Unable to find that message'));
          return;
        }
      }
      setHighlightId(messageId);
      setScrollTarget({ id: messageId, nonce: Date.now() });
      clearTimeout(highlightTimer.current);
      highlightTimer.current = setTimeout(() => setHighlightId(null), 2000);
    },
    [numericId],
  );

  const replyId = composer?.mode === 'reply' ? composer.message.id : null;

  const attachFile = useCallback(
    (file) => {
      const problem = validateFile(file);
      if (problem) {
        toastError(problem);
        return;
      }
      setAttachment({ file, replyToMessageId: replyId });
    },
    [replyId],
  );

  const startVoice = useCallback(() => setRecording({ replyToMessageId: replyId }), [replyId]);

  const mediaSent = useCallback(
    (message) => {
      liveIds.current.add(message.id);
      handleEvent({ type: EVENT.CREATED, conversationId: numericId, messageId: message.id, message });
      setComposer((current) => (current?.mode === 'reply' ? null : current));
    },
    [handleEvent, numericId],
  );

  useEffect(() => {
    let active = true;
    translationService
      .getLanguages()
      .then((list) => active && Array.isArray(list) && list.length > 0 && setLanguages(list))
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  const runTranslation = useCallback(async (message, language) => {
    const key = `${message.id}:${language}`;
    if (translating.current.has(key)) return;
    translating.current.add(key);
    const sourceText = message.content;
    setTranslations((prev) => ({ ...prev, [message.id]: { status: 'loading', language, sourceText } }));
    try {
      const res = await translationService.translateMessage(message.id, language);
      setTranslations((prev) => ({
        ...prev,
        [message.id]: {
          status: 'ready',
          language: res.targetLanguage,
          text: res.translatedText,
          sourceText,
          sourceLanguage: res.sourceLanguage,
          romanized: res.romanized,
          hidden: false,
        },
      }));
    } catch (err) {
      setTranslations((prev) => ({
        ...prev,
        [message.id]: { status: 'error', language, sourceText, error: translationErrorMessage(err) },
      }));
    } finally {
      translating.current.delete(key);
    }
  }, []);

  const submitTranslation = useCallback(
    (language) => {
      const message = translateTarget;
      setTranslateTarget(null);
      if (!message) return;
      rememberTargetLanguage(language);
      runTranslation(message, language);
    },
    [translateTarget, runTranslation],
  );

  const toggleTranslation = useCallback(
    (id) => setTranslations((prev) => (prev[id] ? { ...prev, [id]: { ...prev[id], hidden: !prev[id].hidden } } : prev)),
    [],
  );
  const retryTranslation = useCallback(
    (message) => {
      const language = translations[message.id]?.language;
      if (language) runTranslation(message, language);
    },
    [translations, runTranslation],
  );
  const dismissTranslation = useCallback(
    (id) =>
      setTranslations((prev) => {
        const { [id]: _removed, ...rest } = prev;
        return rest;
      }),
    [],
  );

  const downloadMedia = useCallback(async (message) => {
    if (message.messageType === 'FILE') {
      await downloadFileMessage(message, myId);
      return;
    }
    try {
      await mediaService.download(message.id, API_URL);
    } catch (err) {
      toastError(getChatErrorMessage(err, 'Unable to download this file'));
    }
  }, [myId]);

  const handleSend = async () => {
    const text = draft.trim();
    if (!text || messageLength(text) > MAX_MESSAGE_LENGTH) return;

    if (composer?.mode === 'edit') {
      const target = composer.message;
      setComposer(null);
      setDraft('');
      if (text === target.content) return;
      const result = await run(() => messageActions.edit(target.id, text), 'Unable to edit message');
      if (result?.data) handleEvent({ type: EVENT.UPDATED, conversationId: numericId, messageId: target.id, message: result.data });
      return;
    }

    const payload = {
      conversationId: numericId,
      content: text,
      replyToMessageId: composer?.mode === 'reply' ? composer.message.id : null,
    };
    stopTyping();
    if (!messageActions.send(payload)) {
      toastError('Not connected yet. Please wait a moment.');
      return;
    }
    pendingText.current = text;
    setDraft('');
    setComposer(null);
    setSending(true);
    clearTimeout(sendingTimer.current);
    sendingTimer.current = setTimeout(stopSending, SENDING_TIMEOUT_MS);
  };

  const selectedMessages = useMemo(() => messages.filter((m) => selection.has(m.id)), [messages, selection]);
  const usable = selectedMessages.filter((m) => !m.deleted && !isCallType(m.messageType));

  const backLink = (
    <Link
      to="/chats"
      aria-label="Back to chats"
      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 md:hidden dark:text-slate-300 dark:hover:bg-slate-800"
    >
      <ArrowLeftIcon className="h-5 w-5" />
    </Link>
  );

  if (conversation.status === 'loading') {
    return (
      <div className="flex flex-1 items-center justify-center gap-2 text-sm text-slate-500 dark:text-slate-400" role="status">
        <Spinner className="h-4 w-4" /> Loading conversation…
      </div>
    );
  }

  if (conversation.status === 'error') {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="border-b border-slate-200 p-2 md:hidden dark:border-slate-800">{backLink}</div>
        <div className="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center" role="alert">
          <p className="text-red-600 dark:text-red-400">{conversation.error}</p>
          <Link
            to="/chats"
            className="rounded-lg px-3 py-1.5 text-sm font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
          >
            Back to chats
          </Link>
        </div>
      </div>
    );
  }

  return (
    <ChatPeerContext.Provider value={peerValue}>
    <>
      {selection.size > 0 ? (
        <SelectionBar
          count={selection.size}
          canCopyOrForward={usable.length > 0}
          onCopy={() => copyMessages(usable)}
          onForward={() => setForwarding(usable)}
          onDelete={() => deleteMessages(selectedMessages)}
          onCancel={clearSelection}
        />
      ) : (
        <header className="flex min-h-14 shrink-0 items-center gap-1.5 border-b border-slate-200 bg-white px-1.5 py-2 min-[400px]:gap-2 min-[400px]:px-2 sm:px-4 dark:border-slate-800 dark:bg-slate-900">
          {backLink}
          <button
            type="button"
            onClick={() => setProfileOpen(true)}
            aria-label={`View ${other.username}'s profile`}
            className="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-lg px-0.5 text-left transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:hover:bg-slate-800/60"
          >
            <Avatar src={other.profilePictureUrl} name={other.username} size="sm" />
            <span className="min-w-0 flex-1 px-0.5">
              <h1 className="truncate text-[15px] font-semibold leading-tight text-slate-900 dark:text-white">{other.username}</h1>
              <span className="block truncate leading-tight">
                {!messagingEnabled ? (
                  <span className="text-xs text-slate-500 dark:text-slate-400">Connection ended</span>
                ) : status === 'connected' ? (
                  <PresenceStatus presence={presence} />
                ) : (
                  <ConnectionStatus status={status} />
                )}
              </span>
            </span>
          </button>
          {[
            { type: 'VOICE', label: 'Start voice call', Icon: PhoneIcon },
            { type: 'VIDEO', label: 'Start video call', Icon: VideoIcon },
          ].map(({ type, label, Icon }) => (
            <button
              key={type}
              type="button"
              onClick={() => startCall(numericId, other, type)}
              disabled={callButtonsDisabled}
              aria-label={label}
              title={inCall ? 'You are already in a call' : label}
              className={`${headerBtn} text-slate-600 hover:bg-slate-100 active:scale-90 disabled:cursor-not-allowed disabled:opacity-40 dark:text-slate-300 dark:hover:bg-slate-800`}
            >
              <Icon className="h-5 w-5" />
            </button>
          ))}
          <button
            type="button"
            onClick={() => setCallsOpen((open) => !open)}
            aria-label="Call history"
            aria-pressed={callsOpen}
            className={`${headerBtn} max-[379px]:hidden ${
              callsOpen
                ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            <ClockIcon className="h-5 w-5" />
          </button>
          <button
            type="button"
            onClick={() => setSearchOpen((open) => !open)}
            aria-label="Search messages"
            aria-pressed={searchOpen}
            className={`${headerBtn} max-[379px]:hidden ${
              searchOpen
                ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            <SearchIcon className="h-5 w-5" />
          </button>
          <ChatHeaderMenu
            callsOpen={callsOpen}
            searchOpen={searchOpen}
            onCalls={() => setCallsOpen((open) => !open)}
            onSearch={() => setSearchOpen((open) => !open)}
          />
        </header>
      )}

      {callsOpen && numericId !== null && <CallHistoryPanel conversationId={numericId} onClose={() => setCallsOpen(false)} />}

      {searchOpen && (
        <MessageSearchPanel
          conversationId={numericId}
          nameOf={nameOf}
          onClose={() => setSearchOpen(false)}
          onPick={(id) => {
            if (window.innerWidth < 768) setSearchOpen(false);
            jumpTo(id);
          }}
        />
      )}

      <MessageList
        messages={messages}
        currentUserId={myId}
        liveIds={liveIds.current}
        status={history.status}
        error={history.error}
        onRetry={loadHistory}
        onIncomingSeen={reportRead}
        scrollTarget={scrollTarget}
        highlightId={highlightId}
        selection={selection}
        hiddenIds={hiddenIds.current}
        nameOf={nameOf}
        onOpenMenu={openMenu}
        onToggleSelect={toggleSelect}
        onJumpTo={jumpTo}
        onToggleReaction={toggleReaction}
        onMissingTarget={() => toastInfo('That message is no longer available')}
        translations={translations}
        languages={languages}
        onToggleTranslation={toggleTranslation}
        onRetryTranslation={retryTranslation}
        onDismissTranslation={dismissTranslation}
      />

      {isTyping && <TypingIndicator name={other?.username} />}

      {!messagingEnabled ? (
        <div
          role="status"
          className="shrink-0 border-t border-slate-200 bg-slate-50 px-4 py-3 text-center text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
        >
          <p>Your connection with this user has ended. This chat is read-only history.</p>
          <button
            type="button"
            onClick={() => setProfileOpen(true)}
            className="mt-1 min-h-11 rounded-lg px-3 font-medium text-indigo-600 hover:underline focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400"
          >
            Request to chat again
          </button>
        </div>
      ) : recording ? (
        <VoiceRecorder
          conversationId={numericId}
          replyToMessageId={recording.replyToMessageId}
          banner={<ComposerBanner composer={composer} nameOf={nameOf} onCancel={cancelComposer} />}
          onSent={mediaSent}
          onClose={() => setRecording(null)}
        />
      ) : (
      <MessageInput
        value={draft}
        onChange={handleDraftChange}
        onSend={handleSend}
        connected={isConnected}
        sending={sending}
        focusKey={focusKey}
        onEscape={composer ? cancelComposer : undefined}
        sendLabel={composer?.mode === 'edit' ? 'Save' : 'Send'}
        banner={<ComposerBanner composer={composer} nameOf={nameOf} onCancel={cancelComposer} />}
        mediaEnabled={composer?.mode !== 'edit'}
        onAttach={attachFile}
        onRecord={startVoice}
      />
      )}

      {attachment && messagingEnabled && (
        <MediaUpload
          file={attachment.file}
          conversationId={numericId}
          replyToMessageId={attachment.replyToMessageId}
          onSent={mediaSent}
          onClose={() => setAttachment(null)}
        />
      )}

      {profileOpen && other && (
        <UserPreviewModal
          user={other}
          hideEmail
          onOpenChat={() => setProfileOpen(false)}
          onChanged={refreshConversation}
          onClose={() => setProfileOpen(false)}
        />
      )}

      {menu && (
        <MessageActionMenu
          menu={menu}
          myReaction={menu.message.reactions?.find((r) => r.userId === myId)?.reaction}
          onClose={closeMenu}
          onReact={toggleReaction}
          onReply={startReply}
          onCopy={(m) => copyMessages([m])}
          onForward={setForwarding}
          onEdit={startEdit}
          onSelect={(m) => setSelection(new Set([m.id]))}
          onDelete={deleteMessages}
          onDownload={downloadMedia}
          onTranslate={setTranslateTarget}
          translating={translations[menu.message.id]?.status === 'loading'}
        />
      )}

      {translateTarget && (
        <TranslateDialog message={translateTarget} onCancel={() => setTranslateTarget(null)} onTranslate={submitTranslation} />
      )}

      {forwarding && (
        <ForwardModal
          messages={forwarding}
          onClose={(done) => {
            setForwarding(null);
            if (done === true) clearSelection();
          }}
        />
      )}
    </>
    </ChatPeerContext.Provider>
  );
}
