import { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import Spinner from '../Spinner.jsx';
import { MicIcon, SendIcon } from '../Icons.jsx';
import AttachmentMenu from './AttachmentMenu.jsx';
import { MAX_MESSAGE_LENGTH, messageLength } from '../../utils/messages';

export default function MessageInput({ value, onChange, onSend, connected, sending, banner = null, focusKey, onEscape, sendLabel = 'Send', onAttach, onRecord, mediaEnabled = true }) {
  const ref = useRef(null);
  const length = messageLength(value);
  const tooLong = length > MAX_MESSAGE_LENGTH;
  const canSend = connected && !sending && value.trim().length > 0 && !tooLong;

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 144)}px`;
  }, [value]);

  useEffect(() => {
    if (focusKey) ref.current?.focus();
  }, [focusKey]);

  const submit = () => {
    if (canSend) onSend();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape' && onEscape) {
      onEscape();
      return;
    }
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      className="pb-safe shrink-0 border-t border-slate-200 bg-white px-1.5 pt-2 min-[400px]:px-2 sm:px-3 dark:border-slate-800 dark:bg-slate-900"
    >
      {banner}
      <div className="flex items-end gap-0.5 pb-2 min-[400px]:gap-1">
        {mediaEnabled && onAttach && <AttachmentMenu disabled={!connected || sending} onPick={onAttach} />}
        <textarea
          ref={ref}
          rows={1}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={!connected}
          aria-label="Message"
          placeholder={connected ? 'Type a message...' : 'Waiting for connection…'}
          className="max-h-36 min-h-11 min-w-0 flex-1 resize-none rounded-3xl border border-slate-200 bg-slate-100 px-4 py-2.5 text-base leading-6 text-slate-900 transition-colors placeholder:text-slate-500 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-4 focus:ring-indigo-500/20 disabled:cursor-not-allowed disabled:opacity-70 dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:placeholder:text-slate-400 dark:focus:bg-slate-900"
        />
        {mediaEnabled && onRecord && (
          <button
            type="button"
            onClick={onRecord}
            disabled={!connected || sending}
            aria-label="Record a voice message"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-slate-600 transition-[background-color,transform] hover:bg-slate-100 active:scale-90 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <MicIcon className="h-6 w-6" />
          </button>
        )}
        <motion.button
          type="submit"
          disabled={!canSend}
          aria-label={sendLabel}
          aria-busy={sending || undefined}
          whileTap={canSend ? { scale: 0.92 } : undefined}
          transition={{ duration: 0.1 }}
          className="inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-2 rounded-full bg-indigo-600 px-2.5 font-semibold text-white transition-colors hover:bg-indigo-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/40 disabled:cursor-not-allowed disabled:opacity-50 sm:px-4"
        >
          {sending ? <Spinner className="h-5 w-5" /> : <SendIcon className="h-5 w-5" />}
          <span className="hidden sm:inline">{sendLabel}</span>
        </motion.button>
      </div>
      {length > MAX_MESSAGE_LENGTH * 0.9 && (
        <p className={`-mt-1 pb-1 text-right text-xs ${tooLong ? 'text-red-600 dark:text-red-400' : 'text-slate-500 dark:text-slate-400'}`}>
          {length}/{MAX_MESSAGE_LENGTH}
        </p>
      )}
    </form>
  );
}
