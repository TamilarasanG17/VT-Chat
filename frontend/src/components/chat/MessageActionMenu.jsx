import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { CopyIcon, DownloadIcon, EditIcon, ForwardIcon, ReplyIcon, SelectIcon, TrashIcon, TranslateIcon } from '../Icons.jsx';
import { REACTIONS, normalizeEmoji } from '../../utils/messages';

const item =
  'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-700 transition hover:bg-slate-100 focus-visible:bg-slate-100 focus-visible:outline-none dark:text-slate-200 dark:hover:bg-slate-800 dark:focus-visible:bg-slate-800';

export default function MessageActionMenu({ menu, myReaction, onClose, onReact, onReply, onCopy, onForward, onEdit, onSelect, onDelete, onDownload, onTranslate, translating = false }) {
  const { message, mine, anchor } = menu;
  const ref = useRef(null);
  const [position, setPosition] = useState(null);
  const deleted = message.deleted;
  const isCall = message.messageType === 'VOICE_CALL' || message.messageType === 'VIDEO_CALL';
  const isMedia = !!message.messageType && message.messageType !== 'TEXT' && !isCall;

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    const margin = 8;
    setPosition({
      left: Math.max(margin, Math.min(anchor.x, window.innerWidth - width - margin)),
      top: Math.max(margin, Math.min(anchor.y, window.innerHeight - height - margin)),
    });
  }, [anchor]);

  useEffect(() => {
    const close = () => onClose();
    const onKey = (e) => e.key === 'Escape' && onClose();
    const onPointerDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointerDown, true);
    window.addEventListener('resize', close);
    window.addEventListener('scroll', close, true);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointerDown, true);
      window.removeEventListener('resize', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [onClose]);

  const run = (fn) => () => {
    onClose();
    fn(message);
  };

  return createPortal(
    <motion.div
      ref={ref}
      role="menu"
      aria-label="Message actions"
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.12 }}
      style={{ position: 'fixed', left: position?.left ?? anchor.x, top: position?.top ?? anchor.y, visibility: position ? 'visible' : 'hidden' }}
      className="z-50 max-h-[calc(100dvh-1rem)] w-56 max-w-[calc(100vw-1rem)] overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl dark:border-slate-700 dark:bg-slate-900"
    >
      {!deleted && !isCall && (
        <div className="mb-1 flex items-center justify-between gap-0.5 border-b border-slate-100 px-1 pb-1.5 dark:border-slate-800" role="group" aria-label="React">
          {REACTIONS.map((emoji) => {
            const active = normalizeEmoji(emoji) === normalizeEmoji(myReaction);
            return (
              <button
                key={emoji}
                type="button"
                role="menuitem"
                aria-label={`React ${emoji}`}
                aria-pressed={active}
                onClick={() => {
                  onClose();
                  onReact(message, emoji);
                }}
                className={`rounded-lg p-1.5 text-xl leading-none transition hover:scale-110 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
                  active ? 'bg-indigo-100 dark:bg-indigo-500/25' : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
              >
                {emoji}
              </button>
            );
          })}
        </div>
      )}

      {!deleted && !isCall && (
        <>
          <button type="button" role="menuitem" className={item} onClick={run(onReply)}>
            <ReplyIcon className="h-4 w-4" /> Reply
          </button>
          {(!isMedia || message.content) && (
            <button type="button" role="menuitem" className={item} onClick={run(onCopy)}>
              <CopyIcon className="h-4 w-4" /> {isMedia ? 'Copy caption' : 'Copy'}
            </button>
          )}
          {!isMedia && !!message.content && (
            <button type="button" role="menuitem" className={`${item} disabled:cursor-not-allowed disabled:opacity-50`} disabled={translating} onClick={run(onTranslate)}>
              <TranslateIcon className="h-4 w-4" /> {translating ? 'Translating…' : 'Translate'}
            </button>
          )}
          {isMedia && !(message.messageType === 'FILE' && (mine || message.downloadedByCurrentUser === true)) && (
            <button type="button" role="menuitem" className={item} onClick={run(onDownload)}>
              <DownloadIcon className="h-4 w-4" /> Download
            </button>
          )}
          <button type="button" role="menuitem" className={item} onClick={run((m) => onForward([m]))}>
            <ForwardIcon className="h-4 w-4" /> Forward
          </button>
          {mine && !isMedia && (
            <button type="button" role="menuitem" className={item} onClick={run(onEdit)}>
              <EditIcon className="h-4 w-4" /> Edit
            </button>
          )}
        </>
      )}
      <button type="button" role="menuitem" className={item} onClick={run(onSelect)}>
        <SelectIcon className="h-4 w-4" /> Select
      </button>
      <button
        type="button"
        role="menuitem"
        className={`${item} !text-red-600 dark:!text-red-400`}
        onClick={run((m) => onDelete([m]))}
      >
        <TrashIcon className="h-4 w-4" /> Delete
      </button>
    </motion.div>,
    document.body,
  );
}
