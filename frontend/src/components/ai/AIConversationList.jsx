import EmptyState from '../EmptyState.jsx';
import { PlusIcon, SparklesIcon, TrashIcon } from '../Icons.jsx';
import { formatListTime } from '../../utils/format';

const note = 'px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400';

export default function AIConversationList({ conversations, status, error, activeId, busy, onOpen, onNew, onDelete, onRetry }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-slate-100 px-3 py-2.5 min-[400px]:px-4 dark:border-slate-800">
        <h1 className="flex min-w-0 items-center gap-2 truncate text-base font-bold text-slate-900 dark:text-white">
          <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 text-white">
            <SparklesIcon className="h-4 w-4" />
          </span>
          VTChat AI
        </h1>
        <button
          type="button"
          onClick={onNew}
          disabled={busy}
          className="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-xl bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white transition-[background-color,transform] active:scale-95 hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <PlusIcon className="h-4 w-4" />
          New chat
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {status === 'loading' && (
          <div role="status" aria-label="Loading chats">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse space-y-2 px-4 py-3">
                <div className="h-3.5 w-3/5 rounded bg-slate-200 dark:bg-slate-700" />
                <div className="h-3 w-1/4 rounded bg-slate-200 dark:bg-slate-700" />
              </div>
            ))}
            <span className="sr-only">Loading chats…</span>
          </div>
        )}

        {status === 'error' && (
          <div className={note} role="alert">
            <p className="text-red-600 dark:text-red-400">{error}</p>
            <button type="button" onClick={onRetry} className="mt-3 rounded-lg px-3 py-1.5 font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400 dark:hover:bg-indigo-500/10">
              Try again
            </button>
          </div>
        )}

        {status === 'ready' && conversations.length === 0 && (
          <EmptyState Icon={SparklesIcon} title="No AI chats yet" hint="Ask something to start your first one." />
        )}

        {status === 'ready' && conversations.length > 0 && (
          <ul>
            {conversations.map((c) => {
              const active = c.id === activeId;
              return (
                <li key={c.id} className={`group relative transition-colors ${active ? 'bg-indigo-50 dark:bg-indigo-500/10' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'}`}>
                  {active && <span aria-hidden="true" className="absolute inset-y-2 left-0 w-1 rounded-r bg-indigo-600 dark:bg-indigo-400" />}
                  <button
                    type="button"
                    onClick={() => onOpen(c.id)}
                    disabled={busy && !active}
                    aria-current={active ? 'true' : undefined}
                    title={busy && !active ? 'Wait for the answer to finish, or press Stop' : undefined}
                    className="block w-full min-w-0 px-4 py-3 pr-12 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <span className="block truncate text-sm font-semibold text-slate-900 dark:text-slate-100">{c.title}</span>
                    <span className="block text-xs text-slate-500 dark:text-slate-400">{formatListTime(c.updatedAt)}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => onDelete(c)}
                    disabled={busy && active}
                    aria-label={`Delete chat ${c.title}`}
                    className="absolute right-1 top-1/2 inline-flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full text-slate-400 opacity-100 transition hover:bg-red-50 hover:text-red-600 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:cursor-not-allowed disabled:opacity-40 md:opacity-0 md:group-hover:opacity-100 dark:hover:bg-red-500/10"
                  >
                    <TrashIcon className="h-4 w-4" />
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
