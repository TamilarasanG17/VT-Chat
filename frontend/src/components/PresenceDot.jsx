import { usePresence } from '../hooks/usePresence';

export default function PresenceDot({ userId }) {
  const presence = usePresence(userId);
  if (presence?.status !== 'ONLINE') return null;
  return (
    <span
      role="img"
      aria-label="Online"
      className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-white bg-emerald-500 dark:border-slate-900"
    />
  );
}
