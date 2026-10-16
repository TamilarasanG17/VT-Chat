import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import EmptyState from '../EmptyState.jsx';
import { ChatIcon } from '../Icons.jsx';
import { fadeVariants } from '../../utils/motion';
import ConnectionStatus from './ConnectionStatus.jsx';
import ConversationList from './ConversationList.jsx';
import UserPreviewModal from './UserPreviewModal.jsx';
import UserSearch from './UserSearch.jsx';
import MessageRequests from './MessageRequests.jsx';
import { useChatRequests } from '../../hooks/useChatRequests';
import { useAuth } from '../../hooks/useAuth';
import { useChatSocket } from '../../hooks/useChatSocket';
import { websocketService } from '../../services/websocketService';
import { conversationService } from '../../services/conversationService';
import { getChatErrorMessage } from '../../utils/errors';
import { EVENT, reduceConversations } from '../../utils/messageEvents';
import { activeChats, applyStateEvent, archivedChats, EVENT_STATE } from '../../utils/conversationList';
import { confirmAction, showError, toastSuccess } from '../../utils/alerts';

export default function ChatsShell({ conversationId, children }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { status: socketStatus, subscribe, onUserEvent } = useChatSocket();

  const [list, setList] = useState({ status: 'loading', items: [], error: '' });
  const [preview, setPreview] = useState(null);
  const [searchParams] = useSearchParams();
  const [view, setView] = useState(() => (searchParams.get('tab') === 'requests' ? 'requests' : 'chats'));

  const tabParam = searchParams.get('tab');
  useEffect(() => {
    if (tabParam === 'requests') setView('requests');
  }, [tabParam]);

  const loadConversations = useCallback(() => {
    let active = true;
    setList((s) => ({ ...s, status: 'loading', error: '' }));
    conversationService
      .getConversations()
      .then((res) => active && setList({ status: 'ready', items: res.data ?? [], error: '' }))
      .catch((err) =>
        active &&
        setList({ status: 'error', items: [], error: getChatErrorMessage(err, 'Unable to load conversations') }),
      );
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => loadConversations(), [loadConversations]);

  const conversationIds = useMemo(() => list.items.map((c) => c.id), [list.items]);
  const idsKey = [...conversationIds].sort((a, b) => a - b).join(',');
  useEffect(() => {
    const unsubscribers = conversationIds.map((id) =>
      subscribe(id, (event) => setList((current) => ({ ...current, items: reduceConversations(current.items, event) }))),
    );
    return () => unsubscribers.forEach((off) => off());
  }, [idsKey, subscribe]);

  useEffect(
    () =>
      onUserEvent((event) => {
        const refetch = () =>
          conversationService
            .getConversations()
            .then((res) => setList({ status: 'ready', items: res.data ?? [], error: '' }))
            .catch(() => {
            });
        if (event.type === EVENT.DELETED_FOR_ME) {
          refetch();
        } else if (event.type === EVENT_STATE) {
          let unknown = false;
          setList((current) => {
            const result = applyStateEvent(current.items, event);
            unknown = result.unknown;
            return { ...current, items: result.items };
          });
          if (unknown) refetch();
        }
      }),
    [onUserEvent],
  );

  useEffect(() => websocketService.onReconnect(() => loadConversations()), [loadConversations]);

  const requests = useChatRequests({
    onEvent: (event) => {
      if (event.type === 'CHAT_REQUEST_ACCEPTED' || event.type === 'FRIEND_REMOVED') loadConversations();
    },
  });

  const chats = useMemo(() => activeChats(list.items), [list.items]);
  const archived = useMemo(() => archivedChats(list.items), [list.items]);

  const runAction = async (c, key) => {
    const name = c.otherUser.username;
    const apply = (res) => setList((cur) => ({ ...cur, items: applyStateEvent(cur.items, { type: EVENT_STATE, conversationId: c.id, ...res.data }).items }));
    try {
      if (key === 'open') return navigate(`/chat/${c.id}`);
      if (key === 'read') apply(await conversationService.markRead(c.id));
      else if (key === 'mute' || key === 'unmute') {
        apply(await conversationService.mute(c.id, key === 'mute'));
        toastSuccess(key === 'mute' ? 'Chat muted' : 'Chat unmuted');
      } else if (key === 'pin' || key === 'unpin') {
        apply(await conversationService.pin(c.id, key === 'pin'));
        toastSuccess(key === 'pin' ? 'Chat pinned' : 'Chat unpinned');
      } else if (key === 'archive' || key === 'unarchive') {
        apply(key === 'archive' ? await conversationService.archive(c.id) : await conversationService.unarchive(c.id));
        toastSuccess(key === 'archive' ? 'Chat archived' : 'Chat moved back to Chats');
      } else if (key === 'clear') {
        if (!(await confirmAction({ title: 'Clear this chat?', text: `This removes the messages from your view only. ${name} keeps their copy.`, confirmText: 'Clear chat', icon: 'warning' }))) return;
        apply(await conversationService.clearChat(c.id));
        if (String(c.id) === String(conversationId)) navigate(`/chat/${c.id}`, { replace: true, state: { cleared: Date.now() } });
        setList((cur) => ({ ...cur, items: cur.items.map((x) => (x.id === c.id ? { ...x, lastMessage: null, unreadCount: 0 } : x)) }));
        toastSuccess('Chat cleared');
      } else if (key === 'delete') {
        if (!(await confirmAction({ title: 'Are you sure?', text: `This will remove this conversation from your chat list. ${name} keeps their copy, and it comes back if either of you writes again.`, confirmText: 'Delete', icon: 'warning' }))) return;
        await conversationService.deleteConversation(c.id);
        setList((cur) => ({ ...cur, items: cur.items.filter((x) => x.id !== c.id) }));
        if (String(c.id) === String(conversationId)) navigate('/chats');
        toastSuccess('Conversation deleted');
      }
    } catch (err) {
      showError('Action failed', getChatErrorMessage(err, 'Something went wrong. Please try again.'));
    }
  };

  return (
    <>
      <div className="flex h-full min-h-0 overflow-hidden bg-white md:rounded-2xl md:border md:border-slate-200 md:shadow-sm dark:bg-slate-900 md:dark:border-slate-800">
        <aside
          className={`${conversationId ? 'hidden md:flex' : 'flex'} min-h-0 w-full flex-col border-slate-200 md:w-72 md:shrink-0 md:border-r lg:w-80 xl:w-96 dark:border-slate-800`}
        >
          <div className="flex items-center justify-between gap-2 px-4 pb-1 pt-3">
            <h1 className="text-xl font-bold tracking-tight text-slate-900 dark:text-white">Chats</h1>
            <ConnectionStatus status={socketStatus} />
          </div>
          <UserSearch onSelect={setPreview}>
            <div role="tablist" aria-label="Chat lists" className="flex gap-1.5 overflow-x-auto px-3 py-2">
              {[['chats', 'Chats', chats.length], ['requests', 'Requests', requests.count], ['archived', 'Archived', archived.length]].map(([key, label, count]) => (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={view === key}
                  onClick={() => setView(key)}
                  className={`min-h-9 shrink-0 rounded-full px-3.5 py-1 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
                    view === key ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
                  }`}
                >
                  {label}
                  {key === 'archived' && count > 0 ? ` (${count})` : ''}
                  {key === 'requests' && count > 0 && (
                    <span className="ml-1.5 inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 text-[11px] font-bold leading-5 text-white" aria-label={`${count} pending`}>
                      {count > 99 ? '99+' : count}
                    </span>
                  )}
                </button>
              ))}
            </div>
            <motion.div key={view} variants={fadeVariants} initial="initial" animate="animate">
            {view === 'requests' ? (
              <MessageRequests requests={requests} onChanged={loadConversations} />
            ) : (
            <ConversationList
              conversations={view === 'chats' ? chats : archived}
              status={list.status}
              error={list.error}
              activeId={conversationId}
              currentUserId={user?.id}
              onRetry={loadConversations}
              onAction={runAction}
              emptyTitle={view === 'chats' ? 'No conversations yet' : 'No archived chats'}
              emptyHint={view === 'chats' ? 'Search for someone above and send a chat request.' : 'Archived chats appear here. New messages keep them archived.'}
            />
            )}
            </motion.div>
          </UserSearch>
        </aside>

        <section className={`${conversationId ? 'flex' : 'hidden md:flex'} min-h-0 min-w-0 flex-1 flex-col`}>
          {children || (
            <EmptyState
              Icon={ChatIcon}
              title="Your conversations"
              hint="Select a conversation, or search for someone to start chatting."
              className="flex-1 bg-slate-50 dark:bg-slate-950/40"
            />
          )}
        </section>
      </div>

      <UserPreviewModal
        user={preview}
        isSelf={preview?.id === user?.id}
        onOpenChat={(id) => {
          setPreview(null);
          loadConversations();
          navigate(`/chat/${id}`);
        }}
        onChanged={() => requests.reload({ quiet: true })}
        onClose={() => setPreview(null)}
      />
    </>
  );
}
