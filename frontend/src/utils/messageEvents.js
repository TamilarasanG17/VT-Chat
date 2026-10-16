import { mergeMessages } from './messages';
import { sortConversations } from './conversationList';
import { applyReceipt, EVENT_DELIVERED, EVENT_READ, maxStatus } from './receipts';

export const EVENT = {
  CREATED: 'MESSAGE_CREATED',
  UPDATED: 'MESSAGE_UPDATED',
  DELETED_FOR_ME: 'MESSAGE_DELETED_FOR_ME',
  DELETED_FOR_EVERYONE: 'MESSAGE_DELETED_FOR_EVERYONE',
  REACTION_UPDATED: 'REACTION_UPDATED',
  DELIVERED: EVENT_DELIVERED,
  READ: EVENT_READ,
};

const stamp = (m) => new Date(m.updatedAt ?? m.createdAt).getTime();

export function reduceMessages(messages, event, hiddenIds) {
  switch (event.type) {
    case EVENT.CREATED: {
      const m = event.message;
      if (!m || hiddenIds.has(m.id) || messages.some((x) => x.id === m.id)) return messages;
      return mergeMessages(messages, [m]);
    }

    case EVENT.UPDATED: {
      const m = event.message;
      const existing = messages.find((x) => x.id === m?.id);
      if (!existing || stamp(m) < stamp(existing)) return messages;
      return messages.map((x) =>
        x.id === m.id ? { ...m, replyToMessage: x.replyToMessage ?? m.replyToMessage, deliveryStatus: maxStatus(x.deliveryStatus, m.deliveryStatus) } : x,
      );
    }

    case EVENT.DELETED_FOR_EVERYONE: {
      const tombstone = event.message;
      if (!tombstone || hiddenIds.has(tombstone.id)) return messages;
      const withTombstone = messages.some((x) => x.id === tombstone.id)
        ? messages.map((x) => (x.id === tombstone.id ? { ...tombstone, deliveryStatus: maxStatus(x.deliveryStatus, tombstone.deliveryStatus) } : x))
        : mergeMessages(messages, [tombstone]);
      return withTombstone.map((x) =>
        x.replyToMessage?.id === tombstone.id
          ? { ...x, replyToMessage: { ...x.replyToMessage, content: null, mediaName: null, deleted: true } }
          : x,
      );
    }

    case EVENT.DELETED_FOR_ME:
      return messages
        .filter((x) => x.id !== event.messageId)
        .map((x) =>
          x.replyToMessage?.id === event.messageId
            ? { ...x, replyToMessage: { ...x.replyToMessage, content: null, mediaName: null, unavailable: true } }
            : x,
        );

    case EVENT.REACTION_UPDATED:
      return messages.map((x) => (x.id === event.messageId && !x.deleted ? { ...x, reactions: event.reactions ?? [] } : x));

    case EVENT.DELIVERED:
    case EVENT.READ:
      return applyReceipt(messages, event);

    default:
      return messages;
  }
}

export function createMessageEventHandler({ conversationId, setMessages, hiddenIds, onEvent }) {
  return (event) => {
    if (event.conversationId !== conversationId) return;
    if (event.type === EVENT.DELETED_FOR_ME) hiddenIds.add(event.messageId);
    setMessages((current) => reduceMessages(current, event, hiddenIds));
    onEvent?.(event);
  };
}

const toLastMessage = (m) => ({
  id: m.id,
  senderId: m.senderId,
  content: m.deleted ? null : m.content,
  messageType: m.messageType ?? 'TEXT',
  mediaName: m.deleted ? null : m.mediaName ?? null,
  callStatus: m.callStatus ?? null,
  duration: m.duration ?? null,
  deleted: m.deleted,
  createdAt: m.createdAt,
});

export function reduceConversations(items, event) {
  const index = items.findIndex((c) => c.id === event.conversationId);
  if (index === -1) return items;
  const conversation = items[index];

  switch (event.type) {
    case EVENT.CREATED: {
      const m = event.message;
      if (conversation.lastMessage && conversation.lastMessage.id >= m.id) return items;
      const updated = { ...conversation, updatedAt: m.createdAt, lastMessage: toLastMessage(m) };
      return sortConversations(items.map((c, i) => (i === index ? updated : c)));
    }
    case EVENT.UPDATED:
    case EVENT.DELETED_FOR_EVERYONE: {
      const m = event.message;
      if (conversation.lastMessage?.id !== m.id) return items;
      return items.map((c, i) => (i === index ? { ...c, lastMessage: toLastMessage(m) } : c));
    }
    default:
      return items;
  }
}
