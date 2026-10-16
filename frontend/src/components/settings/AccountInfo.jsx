import VerifiedBadge from '../profile/VerifiedBadge.jsx';
import { formatDate } from '../../utils/format';

export default function AccountInfo({ user }) {
  const items = [
    { label: 'Username', value: user?.username },
    { label: 'Email', value: user?.email },
    { label: 'Email status', value: <VerifiedBadge verified={Boolean(user?.emailVerified)} /> },
    { label: 'Member since', value: formatDate(user?.createdAt) },
  ];

  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <div key={item.label} className="min-w-0 rounded-xl bg-slate-50 p-4 dark:bg-slate-800/60">
          <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{item.label}</dt>
          <dd className="mt-1 break-words text-sm font-medium text-slate-900 dark:text-white">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
