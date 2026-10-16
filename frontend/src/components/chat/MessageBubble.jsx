import { memo, useMemo, useRef } from 'react';
import { motion } from 'framer-motion';
import { messageVariants } from '../../utils/motion';
import { CheckIcon, DotsIcon, ForwardIcon } from '../Icons.jsx';
import MessageStatus from './MessageStatus.jsx';
import { showsTicks } from '../../utils/receipts';
import { formatMessageTime } from '../../utils/format';
import { previewText } from '../../utils/media';
import { normalizeEmoji } from '../../utils/messages';
import MessageRenderer from './MessageRenderer.jsx';
import TranslatedMessage from './TranslatedMessage.jsx';

const LONG_PRESS_MS = 450;
const MOVE_TOLERANCE_PX = 10;

function groupReactions(reactions, currentUserId) {
  const groups = new Map();
  (reactions ?? []).forEach((r) => {
    const key = normalizeEmoji(r.reaction);
    const group = groups.get(key) ?? { emoji: r.reaction, count: 0, mine: false };
    group.count += 1;
    if (r.userId === currentUserId) group.mine = true;
    groups.set(key, group);
  });
  return Array.from(groups.values());
}

function MessageBubble({
  message, mine, currentUserId, animate, grouped, selectionMode, selected, highlighted, hiddenIds, nameOf,
  onOpenMenu, onToggleSelect, onJumpTo, onToggleReaction,
  translation, languages, onToggleTranslation, onRetryTranslation, onDismissTranslation,
}) {
  const press = useRef({ timer: null, x: 0, y: 0 });
  const deleted = message.deleted;
  const reply = message.replyToMessage;
  const reactions = useMemo(() => (deleted ? [] : groupReactions(message.reactions, currentUserId)), [deleted, message.reactions, currentUserId]);

  const cancelPress = () => clearTimeout(press.current.timer);
  const startPress = (e) => {
    if (e.pointerType !== 'touch' || selectionMode) return;
    press.current.x = e.clientX;
    press.current.y = e.clientY;
    cancelPress();
    press.current.timer = setTimeout(() => onOpenMenu(message, mine, { x: press.current.x, y: press.current.y }), LONG_PRESS_MS);
  };
  const movePress = (e) => {
    if (Math.hypot(e.clientX - press.current.x, e.clientY - press.current.y) > MOVE_TOLERANCE_PX) cancelPress();
  };

  const replyUnavailable = reply && (reply.unavailable || hiddenIds.has(reply.id));
  const replyText = reply
    ? reply.deleted
      ? 'This message was deleted'
      : replyUnavailable
        ? 'Original message unavailable'
        : previewText(reply)
    : '';
  const canJump = reply && !reply.deleted && !replyUnavailable;

  return (
    <motion.div
      id={`msg-${message.id}`}
      variants={messageVariants}
      initial={animate ? 'initial' : false}
      animate="animate"
      onClick={selectionMode ? () => onToggleSelect(message.id) : undefined}
      onContextMenu={(e) => {
        if (selectionMode) return;
        e.preventDefault();
        onOpenMenu(message, mine, { x: e.clientX, y: e.clientY });
      }}
      onPointerDown={startPress}
      onPointerMove={movePress}
      onPointerUp={cancelPress}
      onPointerCancel={cancelPress}
      onPointerLeave={cancelPress}
      className={`group relative flex items-center gap-1 rounded-xl px-0.5 transition-colors ${grouped ? '' : 'mt-2 first:mt-0'}  [-webkit-touch-callout:none] ${
        mine ? 'justify-end' : 'justify-start'
      } ${selected ? 'bg-indigo-500/15' : ''} ${highlighted ? 'bg-amber-300/30 dark:bg-amber-400/20' : ''} ${
        selectionMode ? 'cursor-pointer' : ''
      }`}
    >
      {selectionMode && (
        <span
          aria-hidden="true"
          className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${
            mine ? 'mr-auto' : ''
          } ${selected ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-400 dark:border-slate-500'}`}
        >
          {selected && <CheckIcon className="h-3 w-3" />}
        </span>
      )}

      <div className={`relative min-w-0 max-w-[85%] sm:max-w-[75%] lg:max-w-[65%] ${reactions.length ? 'mb-3' : ''}`}>
        <div
          className={`break-anywhere rounded-2xl px-3 py-1.5 shadow-sm min-[400px]:px-3.5 min-[400px]:py-2 ${
            mine
              ? `bg-indigo-600 text-white ${grouped ? '' : 'rounded-br-md'}`
              : `border border-slate-200 bg-white text-slate-900 dark:border-transparent dark:bg-slate-800 dark:text-slate-100 ${grouped ? '' : 'rounded-bl-md'}`
          }`}
        >
          {!deleted && message.forwarded && (
            <p className={`mb-1 flex items-center gap-1 text-xs italic ${mine ? 'text-indigo-100' : 'text-slate-500 dark:text-slate-400'}`}>
              <ForwardIcon className="h-3 w-3" /> Forwarded
            </p>
          )}

          {!deleted && reply && (
            <button
              type="button"
              disabled={!canJump || selectionMode}
              onClick={(e) => {
                e.stopPropagation();
                onJumpTo(reply.id);
              }}
              className={`mb-1.5 block w-full rounded-lg border-l-4 px-2 py-1 text-left text-xs ${
                mine ? 'border-indigo-200 bg-indigo-700/60' : 'border-indigo-400 bg-slate-200/80 dark:bg-slate-700/60'
              } ${canJump ? 'cursor-pointer' : 'cursor-default'}`}
            >
              <span className="block font-semibold">{nameOf(reply.senderId)}</span>
              <span className={`block truncate ${canJump ? '' : 'italic opacity-80'}`}>↳ {replyText}</span>
            </button>
          )}

          {deleted ? (
            <p className={`text-[15px] italic ${mine ? 'text-indigo-100' : 'text-slate-500 dark:text-slate-400'}`}>
              This message was deleted
            </p>
          ) : (
            <MessageRenderer message={message} mine={mine} selectionMode={selectionMode} currentUserId={currentUserId} />
          )}

          {!deleted && translation && translation.sourceText === message.content && (
            <TranslatedMessage
              entry={translation}
              mine={mine}
              languages={languages}
              onToggle={() => onToggleTranslation(message.id)}
              onRetry={() => onRetryTranslation(message)}
              onDismiss={() => onDismissTranslation(message.id)}
            />
          )}

          <p className={`mt-1 flex items-center justify-end gap-1.5 text-[11px] ${mine ? 'text-indigo-100' : 'text-slate-500 dark:text-slate-400'}`}>
            {!deleted && message.edited && <span className="italic">edited</span>}
            <span>{formatMessageTime(message.createdAt)}</span>
            {showsTicks(message, currentUserId) && <MessageStatus status={message.deliveryStatus} />}
          </p>
        </div>

        {reactions.length > 0 && (
          <div className={`absolute -bottom-3 flex gap-1 ${mine ? 'right-2' : 'left-2'}`}>
            {reactions.map((g) => (
              <button
                key={g.emoji}
                type="button"
                disabled={selectionMode}
                aria-label={`${g.emoji} ${g.count}${g.mine ? ', your reaction' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleReaction(message, g.emoji);
                }}
                className={`inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
                  g.mine
                    ? 'border-indigo-400 bg-indigo-50 text-indigo-700 dark:border-indigo-400/60 dark:bg-indigo-500/20 dark:text-indigo-200'
                    : 'border-slate-200 bg-white text-slate-700 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-200'
                }`}
              >
                <span>{g.emoji}</span>
                {g.count > 1 && <span className="font-medium">{g.count}</span>}
              </button>
            ))}
          </div>
        )}
      </div>

      {!selectionMode && (
        <button
          type="button"
          aria-label="Message actions"
          aria-haspopup="menu"
          onClick={(e) => {
            e.stopPropagation();
            const rect = e.currentTarget.getBoundingClientRect();
            onOpenMenu(message, mine, { x: mine ? rect.left - 200 : rect.right, y: rect.top });
          }}
          className={`shrink-0 rounded-full p-1.5 text-slate-400 opacity-0 transition hover:bg-slate-200 hover:text-slate-700 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 group-hover:opacity-100 dark:hover:bg-slate-700 dark:hover:text-slate-200 [@media(hover:none)]:opacity-60 ${
            mine ? 'order-first' : ''
          }`}
        >
          <DotsIcon className="h-5 w-5" />
        </button>
      )}
    </motion.div>
  );
}

export default memo(MessageBubble);
