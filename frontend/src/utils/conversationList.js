export const EVENT_STATE = 'CONVERSATION_STATE';

const group = (c) => (c.archived ? 2 : c.pinned ? 0 : 1);

export function sortConversations(items) {
  return [...items].sort(
    (a, b) => group(a) - group(b) || new Date(b.updatedAt ?? 0) - new Date(a.updatedAt ?? 0) || b.id - a.id,
  );
}

export function applyStateEvent(items, event) {
  if (event.type !== EVENT_STATE) return { items, unknown: false };
  const index = items.findIndex((c) => c.id === event.conversationId);
  if (event.deleted) return { items: index === -1 ? items : items.filter((_, i) => i !== index), unknown: false };
  if (index === -1) return { items, unknown: true };
  const patched = items.map((c, i) =>
    i === index
      ? { ...c, unreadCount: event.unreadCount, muted: event.muted, pinned: event.pinned, archived: event.archived }
      : c,
  );
  return { items: sortConversations(patched), unknown: false };
}

export const activeChats = (items) => items.filter((c) => !c.archived);
export const archivedChats = (items) => items.filter((c) => c.archived);
export const totalUnread = (items) => items.reduce((sum, c) => sum + (c.unreadCount > 0 ? 1 : 0), 0);

export const badgeText = (count) => (count > 99 ? '99+' : String(count));
