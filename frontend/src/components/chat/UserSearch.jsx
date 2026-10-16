import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import Avatar from '../profile/Avatar.jsx';
import Spinner from '../Spinner.jsx';
import { CloseIcon, SearchIcon } from '../Icons.jsx';
import { useDebounce } from '../../hooks/useDebounce';
import { userService } from '../../services/userService';
import { getChatErrorMessage } from '../../utils/errors';

const MIN_LENGTH = 2;

const note = 'px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400';

export default function UserSearch({ onSelect, children }) {
  const [query, setQuery] = useState('');
  const trimmed = query.trim();
  const debounced = useDebounce(trimmed, 350);
  const [state, setState] = useState({ status: 'idle', users: [], error: '' });

  useEffect(() => {
    if (debounced.length < MIN_LENGTH) {
      setState({ status: 'idle', users: [], error: '' });
      return undefined;
    }
    let active = true;
    setState({ status: 'loading', users: [], error: '' });
    userService
      .searchUsers(debounced)
      .then((res) => active && setState({ status: 'done', users: res.data ?? [], error: '' }))
      .catch((err) =>
        active && setState({ status: 'error', users: [], error: getChatErrorMessage(err, 'Unable to search users') }),
      );
    return () => {
      active = false;
    };
  }, [debounced]);

  let body;
  if (trimmed.length === 0) {
    body = children;
  } else if (trimmed.length < MIN_LENGTH) {
    body = <p className={note}>Type at least {MIN_LENGTH} characters to search.</p>;
  } else if (trimmed !== debounced || state.status === 'loading') {
    body = (
      <p className={`${note} flex items-center justify-center gap-2`} role="status">
        <Spinner className="h-4 w-4" /> Searching…
      </p>
    );
  } else if (state.status === 'error') {
    body = (
      <p className={`${note} text-red-600 dark:text-red-400`} role="alert">
        {state.error}
      </p>
    );
  } else if (state.users.length === 0) {
    body = <p className={note}>No users found.</p>;
  } else {
    body = (
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {state.users.map((u) => (
          <motion.li key={u.id} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.15 }}>
            <button
              type="button"
              onClick={() => onSelect(u)}
              className="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none dark:hover:bg-slate-800/60 dark:focus-visible:bg-slate-800/60"
            >
              <Avatar src={u.profilePictureUrl} name={u.username} size="md" />
              <span className="min-w-0">
                <span className="block truncate font-semibold text-slate-900 dark:text-white">{u.username}</span>
                <span className="block truncate text-sm text-slate-500 dark:text-slate-400">{u.email}</span>
              </span>
            </button>
          </motion.li>
        ))}
      </ul>
    );
  }

  return (
    <>
      <div className="px-3 pb-2 pt-1">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={50}
            placeholder="Search people"
            aria-label="Search users"
            autoComplete="off"
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-slate-100 py-2.5 pl-10 pr-10 text-base text-slate-900 placeholder:text-slate-500 transition-colors focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-4 focus:ring-indigo-500/20 dark:border-slate-700 dark:bg-slate-800 dark:placeholder:text-slate-400 dark:focus:bg-slate-900 dark:text-white [&::-webkit-search-cancel-button]:hidden"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              aria-label="Clear search"
              className="absolute right-1 top-1/2 -translate-y-1/2 rounded-lg p-2.5 text-slate-400 hover:text-slate-600 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:hover:text-slate-200"
            >
              <CloseIcon className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">{body}</div>
    </>
  );
}
