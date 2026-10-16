import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { highestId, unreadIncoming } from '../../utils/receipts';
import MessageBubble from './MessageBubble.jsx';
import Spinner from '../Spinner.jsx';
import { ChatIcon } from '../Icons.jsx';
import EmptyState from '../EmptyState.jsx';

const NEAR_BOTTOM_PX = 120;
const GROUP_WINDOW_MS = 5 * 60 * 1000;

function isGrouped(prev, m) {
  if (!prev || prev.senderId !== m.senderId) return false;
  if (prev.messageType?.endsWith('_CALL') || m.messageType?.endsWith('_CALL')) return false;
  return Math.abs(new Date(m.createdAt) - new Date(prev.createdAt)) < GROUP_WINDOW_MS;
}

export default function MessageList({
  messages, currentUserId, liveIds, status, error, onRetry, scrollTarget, highlightId,
  selection, hiddenIds, nameOf, onOpenMenu, onToggleSelect, onJumpTo, onToggleReaction, onMissingTarget,
  translations, languages, onToggleTranslation, onRetryTranslation, onDismissTranslation, onIncomingSeen,
}) {
  const containerRef = useRef(null);
  const nearBottomRef = useRef(true);
  const lastIdRef = useRef(null);
  const firstPaintRef = useRef(true);

  const [pageActive, setPageActive] = useState(() => typeof document === 'undefined' || document.visibilityState === 'visible');
  useEffect(() => {
    const update = () => setPageActive(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, []);

  useEffect(() => {
    const root = containerRef.current;
    if (!root || !onIncomingSeen || !pageActive || status !== 'ready') return undefined;
    const pending = unreadIncoming(messages, currentUserId);
    if (pending.length === 0) return undefined;

    const seen = new Set();
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const id = Number(entry.target.id.replace('msg-', ''));
          const rootHeight = entry.rootBounds?.height ?? 0;
          const visible = entry.isIntersecting && (entry.intersectionRatio >= 0.5 || entry.intersectionRect.height >= rootHeight * 0.6);
          if (visible) seen.add(id);
          else seen.delete(id);
        });
        const top = highestId([...seen]);
        if (top !== null) onIncomingSeen(top);
      },
      { root, threshold: [0, 0.25, 0.5, 0.75, 1] },
    );
    pending.forEach((m) => {
      const el = root.querySelector(`#msg-${m.id}`);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [messages, currentUserId, onIncomingSeen, pageActive, status]);

  const handleScroll = () => {
    const el = containerRef.current;
    if (el) nearBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
  };

  useLayoutEffect(() => {
    const el = containerRef.current;
    const last = messages[messages.length - 1];
    if (!el || !last) return;

    const isNew = last.id !== lastIdRef.current;
    lastIdRef.current = last.id;

    if (firstPaintRef.current) {
      el.scrollTop = el.scrollHeight;
      firstPaintRef.current = false;
    } else if (isNew && (nearBottomRef.current || last.senderId === currentUserId)) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    }
  }, [messages, currentUserId]);

  useLayoutEffect(() => {
    const el = containerRef.current;
    if (status === 'ready' && el && nearBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [status]);

  useLayoutEffect(() => {
    if (!scrollTarget) return;
    const target = containerRef.current?.querySelector(`#msg-${scrollTarget.id}`);
    if (target) {
      nearBottomRef.current = false;
      target.scrollIntoView({ block: 'center', behavior: 'smooth' });
    } else {
      onMissingTarget?.();
    }
  }, [scrollTarget]);

  if (status === 'loading' && messages.length === 0) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center gap-2 bg-slate-50 text-sm text-slate-500 dark:bg-slate-950/40 dark:text-slate-400" role="status">
        <Spinner className="h-4 w-4" /> Loading messages…
      </div>
    );
  }

  if (status === 'error' && messages.length === 0) {
    return (
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 p-6 text-center" role="alert">
        <p className="text-red-600 dark:text-red-400">{error}</p>
        <button
          type="button"
          onClick={onRetry}
          className="rounded-lg px-3 py-1.5 text-sm font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
        >
          Try again
        </button>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <EmptyState Icon={ChatIcon} title="No messages yet" hint="Say hello to start the conversation." className="min-h-0 flex-1 bg-slate-50 dark:bg-slate-950/40" />
    );
  }

  const selectionMode = selection.size > 0;

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto overscroll-contain bg-slate-50 px-1.5 py-3 min-[400px]:px-2 sm:px-4 dark:bg-slate-950/40"
      aria-live="polite"
    >
      {messages.map((m, i) => (
        <MessageBubble
          key={m.id}
          message={m}
          mine={m.senderId === currentUserId}
          currentUserId={currentUserId}
          animate={liveIds.has(m.id)}
          grouped={isGrouped(messages[i - 1], m)}
          selectionMode={selectionMode}
          selected={selection.has(m.id)}
          highlighted={highlightId === m.id}
          hiddenIds={hiddenIds}
          nameOf={nameOf}
          onOpenMenu={onOpenMenu}
          onToggleSelect={onToggleSelect}
          onJumpTo={onJumpTo}
          onToggleReaction={onToggleReaction}
          translation={translations?.[m.id]}
          languages={languages}
          onToggleTranslation={onToggleTranslation}
          onRetryTranslation={onRetryTranslation}
          onDismissTranslation={onDismissTranslation}
        />
      ))}
    </div>
  );
}
