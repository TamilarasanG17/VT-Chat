import { ChatIcon, CloseIcon, PhoneIcon, VideoIcon, BellIcon } from '../Icons.jsx';
import { formatRelativeTime } from '../../utils/format';

function iconFor(n) {
  if (n.type === 'NEW_MESSAGE' || n.type === 'CHAT_REQUEST' || n.type === 'CHAT_REQUEST_ACCEPTED') return ChatIcon;
  if (n.type === 'MISSED_CALL' || n.type === 'INCOMING_CALL') return /video/i.test(n.title) ? VideoIcon : PhoneIcon;
  return BellIcon;
}

export default function NotificationItem({ notification: n, onOpen, onDelete }) {
  const Icon = iconFor(n);
  const missed = n.type === 'MISSED_CALL';
  return (
    <li className={`group relative flex items-start gap-2 px-3 py-2.5 transition-colors min-[400px]:gap-3 min-[400px]:px-4 hover:bg-slate-50 dark:hover:bg-slate-800/50 ${n.read ? '' : 'bg-indigo-50/60 dark:bg-indigo-500/10'}`}>
      <button
        type="button"
        onClick={() => onOpen(n)}
        className="flex min-w-0 flex-1 items-start gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/50"
      >
        <span
          className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${
            missed ? 'bg-red-100 text-red-600 dark:bg-red-500/20 dark:text-red-300' : 'bg-indigo-100 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-300'
          }`}
        >
          <Icon className="h-4 w-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className={`block truncate text-sm ${n.read ? 'font-medium text-slate-700 dark:text-slate-200' : 'font-semibold text-slate-900 dark:text-white'}`}>
            {n.title}
          </span>
          <span className="block truncate text-sm text-slate-500 dark:text-slate-400">{n.message}</span>
          <span className="block text-xs text-slate-400 dark:text-slate-500">{formatRelativeTime(n.createdAt)}</span>
        </span>
        {!n.read && <span aria-label="Unread" className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-indigo-600" />}
      </button>
      <button
        type="button"
        onClick={() => onDelete(n)}
        aria-label={`Delete notification: ${n.title}`}
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:hover:bg-slate-800 dark:hover:text-slate-200"
      >
        <CloseIcon className="h-4 w-4" />
      </button>
    </li>
  );
}
