import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import Avatar from '../profile/Avatar.jsx';
import Button from '../Button.jsx';
import Spinner from '../Spinner.jsx';
import { CloseIcon, SearchIcon } from '../Icons.jsx';
import { conversationService } from '../../services/conversationService';
import { messageActions } from '../../services/messageService';
import { showError, showSuccess } from '../../utils/alerts';
import { getChatErrorMessage } from '../../utils/errors';

export default function ForwardModal({ messages, onClose }) {
  const [list, setList] = useState({ status: 'loading', items: [], error: '' });
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [forwarding, setForwarding] = useState(false);
  const dialogRef = useRef(null);

  useEffect(() => {
    let active = true;
    conversationService
      .getConversations()
      .then((res) => active && setList({ status: 'ready', items: res.data ?? [], error: '' }))
      .catch((err) =>
        active && setList({ status: 'error', items: [], error: getChatErrorMessage(err, 'Unable to load conversations') }),
      );
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    dialogRef.current?.focus();
    const onKey = (e) => e.key === 'Escape' && !forwarding && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [forwarding, onClose]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? list.items.filter((c) => c.otherUser.username.toLowerCase().includes(q)) : list.items;
  }, [list.items, query]);

  const forward = async () => {
    if (!selectedId || forwarding) return;
    setForwarding(true);
    const ordered = [...messages].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt) || a.id - b.id);
    try {
      for (const m of ordered) {
        await messageActions.forward(m.id, selectedId);
      }
      onClose(true);
      await showSuccess('Message forwarded', ordered.length === 1 ? 'Your message was forwarded.' : `${ordered.length} messages were forwarded.`);
    } catch (err) {
      setForwarding(false);
      await showError('Unable to forward message', getChatErrorMessage(err, 'Unable to forward message'));
    }
  };

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-3 min-[400px]:p-4 sm:items-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      onMouseDown={(e) => e.target === e.currentTarget && !forwarding && onClose()}
    >
      <motion.div
        ref={dialogRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Forward message"
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex max-h-[85dvh] w-full max-w-sm flex-col rounded-2xl border border-slate-200 bg-white shadow-xl outline-none dark:border-slate-700 dark:bg-slate-900"
      >
        <div className="flex items-center justify-between px-5 pt-5">
          <h2 className="text-lg font-bold text-slate-900 dark:text-white">Forward message</h2>
          <button type="button" onClick={onClose} disabled={forwarding} aria-label="Close" className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-slate-400 dark:hover:bg-slate-800">
            <CloseIcon className="h-5 w-5" />
          </button>
        </div>

        <div className="relative mx-5 mt-3">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search conversations"
            aria-label="Search conversations"
            className="w-full rounded-xl border border-slate-300 bg-white py-2 pl-10 pr-3 text-base text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white [&::-webkit-search-cancel-button]:hidden"
          />
        </div>

        <div className="mt-3 min-h-[8rem] flex-1 overflow-y-auto border-y border-slate-100 dark:border-slate-800">
          {list.status === 'loading' && (
            <p className="flex items-center justify-center gap-2 px-4 py-8 text-sm text-slate-500 dark:text-slate-400" role="status">
              <Spinner className="h-4 w-4" /> Loading conversations…
            </p>
          )}
          {list.status === 'error' && <p className="px-4 py-8 text-center text-sm text-red-600 dark:text-red-400" role="alert">{list.error}</p>}
          {list.status === 'ready' && visible.length === 0 && (
            <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">No conversations found.</p>
          )}
          {list.status === 'ready' && visible.length > 0 && (
            <ul role="radiogroup" aria-label="Conversations" className="divide-y divide-slate-100 dark:divide-slate-800">
              {visible.map((c) => {
                const active = c.id === selectedId;
                return (
                  <li key={c.id}>
                    <button
                      type="button"
                      role="radio"
                      aria-checked={active}
                      onClick={() => setSelectedId(c.id)}
                      className={`flex w-full items-center gap-3 px-5 py-2.5 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-indigo-500/50 ${
                        active ? 'bg-indigo-50 dark:bg-indigo-500/15' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'
                      }`}
                    >
                      <Avatar src={c.otherUser.profilePictureUrl} name={c.otherUser.username} size="sm" />
                      <span className="min-w-0 flex-1 truncate font-medium text-slate-900 dark:text-white">{c.otherUser.username}</span>
                      <span aria-hidden="true" className={`h-4 w-4 shrink-0 rounded-full border-2 ${active ? 'border-indigo-600 bg-indigo-600 ring-2 ring-inset ring-white dark:ring-slate-900' : 'border-slate-400 dark:border-slate-500'}`} />
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex gap-3 p-5">
          <Button variant="secondary" onClick={onClose} disabled={forwarding}>Cancel</Button>
          <Button onClick={forward} loading={forwarding} disabled={!selectedId}>Forward</Button>
        </div>
      </motion.div>
    </motion.div>
  );
}
