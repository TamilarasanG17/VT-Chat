import { useEffect, useState } from 'react';
import { describePresence } from '../utils/presence';

export default function PresenceStatus({ presence, className = '' }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (presence?.status === 'ONLINE' || !presence) return undefined;
    const id = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(id);
  }, [presence]);

  const d = describePresence(presence, now);
  if (d.kind === 'none') return null;
  return d.kind === 'online' ? (
    <span role="status" className={`inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700 dark:text-emerald-400 ${className}`}>
      <span className="h-2 w-2 rounded-full bg-emerald-500" aria-hidden="true" />
      Online
    </span>
  ) : (
    <span role="status" className={`text-xs text-slate-500 dark:text-slate-400 ${className}`}>
      {d.text}
    </span>
  );
}
