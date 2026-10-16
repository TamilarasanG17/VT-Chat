import { maxStatus, STATUS_RANK } from './receipts';

const rank = (s) => STATUS_RANK[s] ?? 0;

export const MAX_MESSAGE_LENGTH = 2000;

export const messageLength = (text) => Array.from(text).length;

const time = (m) => new Date(m.updatedAt ?? m.createdAt).getTime();

export function mergeMessages(current, incoming) {
  const byId = new Map(current.map((m) => [m.id, m]));
  incoming.forEach((m) => {
    const existing = byId.get(m.id);
    if (!existing) byId.set(m.id, m);
    else if (time(m) >= time(existing)) byId.set(m.id, { ...m, deliveryStatus: maxStatus(existing.deliveryStatus, m.deliveryStatus) });
    else if (rank(m.deliveryStatus) > rank(existing.deliveryStatus)) byId.set(m.id, { ...existing, deliveryStatus: m.deliveryStatus });
  });
  return Array.from(byId.values()).sort(
    (a, b) => new Date(a.createdAt) - new Date(b.createdAt) || a.id - b.id,
  );
}

export const REACTIONS = ['\u{1F44D}', '\u2764\uFE0F', '\u{1F602}', '\u{1F62E}', '\u{1F622}', '\u{1F64F}'];

export const normalizeEmoji = (emoji) => (emoji ?? '').replace(/\uFE0F/g, '');
