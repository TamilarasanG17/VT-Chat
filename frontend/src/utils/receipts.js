export const STATUS_RANK = { SENT: 0, DELIVERED: 1, READ: 2 };

const rank = (s) => STATUS_RANK[s] ?? 0;

export const maxStatus = (a, b) => (rank(b) > rank(a) ? b : a);

export const EVENT_DELIVERED = 'MESSAGE_DELIVERED';
export const EVENT_READ = 'MESSAGE_READ';

export function applyReceipt(messages, event) {
  const target = event.type === EVENT_READ ? 'READ' : event.type === EVENT_DELIVERED ? 'DELIVERED' : null;
  if (!target) return messages;
  let changed = false;
  const next = messages.map((m) => {
    if (m.senderId === event.userId || m.id > event.upToMessageId || m.deleted) return m;
    const status = maxStatus(m.deliveryStatus ?? 'SENT', target);
    if (status === m.deliveryStatus) return m;
    changed = true;
    return { ...m, deliveryStatus: status };
  });
  return changed ? next : messages;
}

export const highestId = (ids) => (ids.length ? Math.max(...ids) : null);

export const unreadIncoming = (messages, myId) =>
  messages.filter((m) => m.senderId !== myId && !m.deleted && m.deliveryStatus !== 'READ' && !m.messageType?.endsWith('_CALL'));

export const showsTicks = (m, myId) => m.senderId === myId && !m.deleted && !m.messageType?.endsWith('_CALL');
