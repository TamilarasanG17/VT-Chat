import { useState } from 'react';
import { previewText } from '../../utils/media';
import { AnimatePresence, motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import Avatar from '../profile/Avatar.jsx';
import { ArchiveIcon, ChatIcon, DotsIcon, PinIcon, SpeakerOffIcon } from '../Icons.jsx';
import ChatContextMenu from './ChatContextMenu.jsx';
import NotificationBadge from '../notifications/NotificationBadge.jsx';
import PresenceDot from '../PresenceDot.jsx';
import { formatListTime } from '../../utils/format';
import EmptyState from '../EmptyState.jsx';

const note = 'px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400';

export default function ConversationList({ conversations, status, error, activeId, currentUserId, onRetry, onAction, emptyTitle = 'No conversations yet', emptyHint = 'Search for someone above to start chatting.' }) {
  const [menuFor, setMenuFor] = useState(null);

  if (status === 'loading') {
    return (
      <div role="status" aria-label="Loading conversations">
        {[0, 1, 2, 3, 4].map((i) => (
          <div key={i} className="flex animate-pulse items-center gap-3 px-4 py-3">
            <div className="h-12 w-12 shrink-0 rounded-full bg-slate-200 dark:bg-slate-700" />
            <div className="min-w-0 flex-1 space-y-2">
              <div className="h-3.5 w-2/5 rounded bg-slate-200 dark:bg-slate-700" />
              <div className="h-3 w-3/4 rounded bg-slate-200 dark:bg-slate-700" />
            </div>
          </div>
        ))}
        <span className="sr-only">Loading conversations…</span>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className={note} role="alert">
        <p className="text-red-600 dark:text-red-400">{error}</p>
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-lg px-3 py-1.5 font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
        >
          Try again
        </button>
      </div>
    );
  }

  if (conversations.length === 0) {
    return (
      <EmptyState Icon={ChatIcon} title={emptyTitle} hint={emptyHint} />
    );
  }

  return (
    <ul>
      {conversations.map((c) => {
        const active = String(c.id) === String(activeId);
        const last = c.lastMessage;
        return (
          <motion.li key={c.id} layout="position" transition={{ duration: 0.2 }} className="group relative" onContextMenu={(e) => { if (onAction) { e.preventDefault(); setMenuFor(c.id); } }}>
            <Link
              to={`/chat/${c.id}`}
              aria-current={active ? 'page' : undefined}
              className={`flex min-h-[4.25rem] items-center gap-3 py-2.5 pl-4 pr-12 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-indigo-500/50 ${
                active ? 'bg-indigo-50 dark:bg-indigo-500/15' : 'hover:bg-slate-50 active:bg-slate-100 dark:hover:bg-slate-800/60 dark:active:bg-slate-800'
              }`}
            >
              {active && <span aria-hidden="true" className="absolute inset-y-2 left-0 w-1 rounded-r bg-indigo-600 dark:bg-indigo-400" />}
              <span className="relative shrink-0">
                <Avatar src={c.otherUser.profilePictureUrl} name={c.otherUser.username} size="md" />
                <PresenceDot userId={c.otherUser.id} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-baseline justify-between gap-2">
                  <span className={`truncate text-slate-900 dark:text-white ${c.unreadCount > 0 ? 'font-bold' : 'font-semibold'}`}>{c.otherUser.username}</span>
                  {last && (
                    <span className={`shrink-0 text-xs ${c.unreadCount > 0 ? 'font-semibold text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'}`}>{formatListTime(last.createdAt)}</span>
                  )}
                </span>
                <span className="flex items-center gap-2">
                  <span className={`block min-w-0 flex-1 truncate text-sm ${c.unreadCount > 0 ? 'font-medium text-slate-800 dark:text-slate-100' : 'text-slate-500 dark:text-slate-400'}`}>
                    {c.messagingEnabled === false && <span className="mr-1 rounded bg-slate-200 px-1.5 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">Connection ended</span>}
                    {!last && 'No messages yet'}
                    {last?.deleted && <em>This message was deleted</em>}
                    {last && !last.deleted && `${last.senderId === currentUserId ? 'You: ' : ''}${previewText(last, currentUserId)}`}
                  </span>
                  {c.pinned && <PinIcon className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-label="Pinned" />}
                  {c.muted && <SpeakerOffIcon className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-label="Muted" />}
                  {c.archived && <ArchiveIcon className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-label="Archived" />}
                  <NotificationBadge
                    count={c.unreadCount}
                    label={`${c.unreadCount} unread messages`}
                    className={c.muted ? '!bg-slate-400 dark:!bg-slate-600' : ''}
                  />
                </span>
              </span>
            </Link>
            {onAction && (
              <button
                type="button"
                aria-label={`Chat actions for ${c.otherUser.username}`}
                aria-haspopup="menu"
                aria-expanded={menuFor === c.id}
                onClick={() => setMenuFor(menuFor === c.id ? null : c.id)}
                className="absolute right-1 top-3 flex h-10 w-10 items-center justify-center rounded-full text-slate-400 opacity-100 transition hover:bg-slate-200 hover:text-slate-700 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 md:opacity-0 md:group-hover:opacity-100 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              >
                <DotsIcon className="h-4 w-4" />
              </button>
            )}
            <AnimatePresence>
              {menuFor === c.id && (
                <ChatContextMenu
                  conversation={c}
                  onClose={() => setMenuFor(null)}
                  onSelect={(key) => {
                    setMenuFor(null);
                    onAction(c, key);
                  }}
                />
              )}
            </AnimatePresence>
          </motion.li>
        );
      })}
    </ul>
  );
}
