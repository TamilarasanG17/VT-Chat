const timeOf = (d) => d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
const dayStart = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

export function formatLastSeen(iso, now = new Date()) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';

  const seconds = Math.floor((now.getTime() - d.getTime()) / 1000);
  if (seconds < 60) return 'Last seen just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `Last seen ${minutes} minute${minutes === 1 ? '' : 's'} ago`;

  const days = Math.round((dayStart(now) - dayStart(d)) / 86_400_000);
  if (days <= 0) return `Last seen today at ${timeOf(d)}`;
  if (days === 1) return `Last seen yesterday at ${timeOf(d)}`;
  const date = d.toLocaleDateString([], { month: 'short', day: 'numeric', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
  return `Last seen ${date} at ${timeOf(d)}`;
}

export function describePresence(presence, now = new Date()) {
  if (!presence) return { kind: 'none', text: '' };
  if (presence.status === 'ONLINE') return { kind: 'online', text: 'Online' };
  const text = formatLastSeen(presence.lastSeenAt, now);
  if (text) return { kind: 'lastSeen', text };
  if (presence.status === 'OFFLINE') return { kind: 'offline', text: 'Offline' };
  return { kind: 'none', text: '' };
}
