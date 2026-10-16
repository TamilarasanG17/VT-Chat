import { useNavigate } from 'react-router-dom';
import NotificationItem from './NotificationItem.jsx';
import Spinner from '../Spinner.jsx';
import { BellIcon } from '../Icons.jsx';
import EmptyState from '../EmptyState.jsx';
import { useNotifications } from '../../hooks/useNotifications';
import { routeFor } from '../../utils/notifications';

export default function NotificationPanel({ onNavigate, className = '' }) {
  const { items, unread, status, error, hasMore, load, loadMore, markRead, markAllRead, remove } = useNotifications();
  const navigate = useNavigate();

  const open = (n) => {
    if (!n.read) markRead(n.id);
    const to = routeFor(n);
    if (to) navigate(to);
    onNavigate?.();
  };

  return (
    <div className={`flex min-h-0 flex-col ${className}`}>
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">Notifications</h2>
        <button
          type="button"
          onClick={markAllRead}
          disabled={unread === 0}
          className="min-h-9 rounded-lg px-2 py-1 text-sm font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50 dark:text-indigo-300 dark:hover:bg-indigo-500/10"
        >
          Mark all as read
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {status === 'loading' && items.length === 0 && (
          <p className="flex items-center justify-center gap-2 px-4 py-10 text-sm text-slate-500 dark:text-slate-400" role="status">
            <Spinner className="h-4 w-4" /> Loading notifications…
          </p>
        )}

        {status === 'error' && items.length === 0 && (
          <div className="px-4 py-10 text-center text-sm" role="alert">
            <p className="text-red-600 dark:text-red-400">{error}</p>
            <button type="button" onClick={() => load(0)} className="mt-3 rounded-lg px-3 py-1.5 font-semibold text-indigo-600 hover:bg-indigo-50 dark:text-indigo-300 dark:hover:bg-indigo-500/10">
              Try again
            </button>
          </div>
        )}

        {status === 'ready' && items.length === 0 && (
          <EmptyState Icon={BellIcon} title="No notifications yet" hint="Missed calls and new messages will show up here." />
        )}

        {items.length > 0 && (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {items.map((n) => (
              <NotificationItem key={n.id} notification={n} onOpen={open} onDelete={(item) => remove(item.id)} />
            ))}
          </ul>
        )}

        {hasMore && (
          <div className="p-3 text-center">
            <button type="button" onClick={loadMore} className="rounded-lg px-3 py-1.5 text-sm font-semibold text-indigo-600 hover:bg-indigo-50 dark:text-indigo-300 dark:hover:bg-indigo-500/10">
              Load more
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
