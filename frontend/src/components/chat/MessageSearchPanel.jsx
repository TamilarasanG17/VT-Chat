import { previewText } from '../../utils/media';
import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { fadeVariants } from '../../utils/motion';
import Spinner from '../Spinner.jsx';
import { CloseIcon, SearchIcon } from '../Icons.jsx';
import { useDebounce } from '../../hooks/useDebounce';
import { conversationService } from '../../services/conversationService';
import { formatMessageTime } from '../../utils/format';
import { getChatErrorMessage } from '../../utils/errors';

const MIN_LENGTH = 2;
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function Highlight({ text, query }) {
  const parts = text.split(new RegExp(`(${escapeRegExp(query)})`, 'ig'));
  return parts.map((part, i) =>
    part.toLowerCase() === query.toLowerCase() ? (
      <mark key={i} className="rounded bg-amber-200 px-0.5 text-slate-900 dark:bg-amber-400/40 dark:text-white">{part}</mark>
    ) : (
      <span key={i}>{part}</span>
    ),
  );
}

export default function MessageSearchPanel({ conversationId, nameOf, onPick, onClose }) {
  const [query, setQuery] = useState('');
  const trimmed = query.trim();
  const debounced = useDebounce(trimmed, 350);
  const [state, setState] = useState({ status: 'idle', results: [], error: '' });
  const inputRef = useRef(null);

  useEffect(() => inputRef.current?.focus(), []);

  useEffect(() => {
    if (debounced.length < MIN_LENGTH) {
      setState({ status: 'idle', results: [], error: '' });
      return undefined;
    }
    let active = true;
    setState({ status: 'loading', results: [], error: '' });
    conversationService
      .searchMessages(conversationId, debounced)
      .then((res) => active && setState({ status: 'done', results: res.data ?? [], error: '' }))
      .catch((err) =>
        active && setState({ status: 'error', results: [], error: getChatErrorMessage(err, 'Unable to search messages') }),
      );
    return () => {
      active = false;
    };
  }, [conversationId, debounced]);

  const note = 'px-4 py-4 text-center text-sm text-slate-500 dark:text-slate-400';
  let body = null;
  if (trimmed.length > 0 && trimmed.length < MIN_LENGTH) body = <p className={note}>Type at least {MIN_LENGTH} characters.</p>;
  else if (trimmed.length >= MIN_LENGTH && (trimmed !== debounced || state.status === 'loading')) {
    body = (
      <p className={`${note} flex items-center justify-center gap-2`} role="status">
        <Spinner className="h-4 w-4" /> Searching…
      </p>
    );
  } else if (state.status === 'error') {
    body = <p className={`${note} text-red-600 dark:text-red-400`} role="alert">{state.error}</p>;
  } else if (state.status === 'done' && state.results.length === 0) {
    body = <p className={note}>No messages found.</p>;
  } else if (state.status === 'done') {
    body = (
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {state.results.map((r) => (
          <li key={r.id}>
            <button
              type="button"
              onClick={() => onPick(r.id)}
              className="block w-full px-4 py-2.5 text-left transition hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none dark:hover:bg-slate-800/60 dark:focus-visible:bg-slate-800/60"
            >
              <span className="flex items-baseline justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold">{nameOf(r.senderId)}</span>
                <span>
                  {new Date(r.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} · {formatMessageTime(r.createdAt)}
                </span>
              </span>
              <span className="mt-0.5 line-clamp-2 break-words text-sm text-slate-800 dark:text-slate-100">
                <Highlight text={r.messageType && r.messageType !== 'TEXT' ? previewText(r) : r.content} query={debounced} />
              </span>
            </button>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <motion.section variants={fadeVariants} initial="initial" animate="animate" aria-label="Search messages" className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center gap-2 p-3">
        <div className="relative min-w-0 flex-1">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
          <input
            ref={inputRef}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Escape' && onClose()}
            maxLength={100}
            placeholder="Search messages..."
            aria-label="Search messages"
            autoComplete="off"
            className="min-h-11 w-full rounded-xl border border-slate-300 bg-white py-2 pl-10 pr-3 text-base text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white [&::-webkit-search-cancel-button]:hidden"
          />
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close search"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-slate-400 dark:hover:bg-slate-800"
        >
          <CloseIcon className="h-5 w-5" />
        </button>
      </div>
      {body && <div className="max-h-64 overflow-y-auto border-t border-slate-100 dark:border-slate-800">{body}</div>}
    </motion.section>
  );
}
