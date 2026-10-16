import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { SendIcon, StopIcon } from '../Icons.jsx';
import { AI_MAX_MESSAGE_LENGTH } from '../../utils/ai';

export default function AIInput({ onSend, onStop, generating, blockedSeconds, restoreText, prefill, maxLength = AI_MAX_MESSAGE_LENGTH, placeholder = 'Ask VTChat AI…', disabledReason = '' }) {
  const [value, setValue] = useState('');
  const ref = useRef(null);

  useEffect(() => {
    if (restoreText) setValue((v) => v || restoreText.text);
  }, [restoreText]);

  useEffect(() => {
    if (prefill) {
      setValue(prefill.text);
      ref.current?.focus();
    }
  }, [prefill]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 176)}px`;
  }, [value]);

  const trimmed = value.trim();
  const tooLong = value.length > maxLength;
  const blocked = blockedSeconds > 0;
  const canSend = !!trimmed && !tooLong && !generating && !blocked && !disabledReason;

  const submit = async () => {
    if (!canSend) return;
    const text = value;
    setValue('');
    const accepted = await onSend(text);
    if (accepted === false) setValue((v) => v || text);
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className="pb-safe shrink-0 border-t border-slate-200 bg-white p-2 min-[400px]:p-3 dark:border-slate-800 dark:bg-slate-900">
      {blocked && (
        <p className="mb-2 text-center text-xs font-medium text-amber-600 dark:text-amber-400" role="status">
          Too many AI requests. You can ask again in {blockedSeconds}s.
        </p>
      )}
      {disabledReason && (
        <p className="mb-2 text-center text-xs font-medium text-amber-600 dark:text-amber-400" role="status">
          {disabledReason}
        </p>
      )}
      <div className="flex items-end gap-1.5 min-[400px]:gap-2">
        <textarea
          ref={ref}
          rows={1}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={onKeyDown}
          readOnly={generating}
          aria-label={placeholder}
          placeholder={placeholder}
          className="max-h-44 min-h-11 min-w-0 flex-1 resize-none rounded-3xl border border-slate-200 bg-slate-100 px-4 py-2.5 text-base leading-6 text-slate-900 transition-colors placeholder:text-slate-500 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-4 focus:ring-indigo-500/20 read-only:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:placeholder:text-slate-400 dark:read-only:bg-slate-800/60 dark:focus:bg-slate-900"
        />
        {generating ? (
          <button
            type="button"
            onClick={onStop}
            className="inline-flex h-11 shrink-0 items-center gap-2 rounded-full border border-slate-300 bg-white px-3.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-400/30 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
          >
            <StopIcon className="h-4 w-4" />
            Stop
          </button>
        ) : (
          <motion.button
            type="button"
            onClick={submit}
            disabled={!canSend}
            aria-label="Send"
            whileTap={canSend ? { scale: 0.92 } : undefined}
            transition={{ duration: 0.1 }}
            className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white transition-colors hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <SendIcon className="h-5 w-5" />
          </motion.button>
        )}
      </div>
      {(value.length > maxLength * 0.8 || tooLong) && (
        <p className={`mt-1 text-right text-xs ${tooLong ? 'font-semibold text-red-600 dark:text-red-400' : 'text-slate-400'}`}>
          {value.length}/{maxLength}
        </p>
      )}
    </div>
  );
}
