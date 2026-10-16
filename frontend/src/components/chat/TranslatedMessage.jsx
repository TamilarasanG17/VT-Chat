import { AnimatePresence, motion } from 'framer-motion';
import Spinner from '../Spinner.jsx';
import { describeSource, languageName } from '../../utils/translation';

export default function TranslatedMessage({ entry, mine, languages, onToggle, onRetry, onDismiss }) {
  const muted = mine ? 'text-indigo-100' : 'text-slate-500 dark:text-slate-400';
  const link = `rounded px-1 text-xs font-semibold underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
    mine ? 'text-white' : 'text-indigo-700 dark:text-indigo-300'
  }`;
  const target = languageName(entry.language, languages);

  let body;
  if (entry.status === 'loading') {
    body = (
      <p className={`flex items-center gap-2 text-sm ${muted}`} role="status">
        <Spinner className="h-4 w-4" /> Translating…
      </p>
    );
  } else if (entry.status === 'error') {
    body = (
      <div role="alert" className="text-sm">
        <p>{entry.error}</p>
        <p className="mt-1 flex gap-3">
          <button type="button" onClick={onRetry} className={link}>Try again</button>
          <button type="button" onClick={onDismiss} className={link}>Dismiss</button>
        </p>
      </div>
    );
  } else if (entry.hidden) {
    body = (
      <button type="button" onClick={onToggle} className={link} aria-label={`Show ${target} translation`}>
        Show translation
      </button>
    );
  } else {
    const source = describeSource(entry, languages);
    body = (
      <>
        <p className={`text-xs font-semibold ${muted}`}>
          Translation · {target}
          {source ? ` · from ${source}` : ''}
        </p>
        <p lang={entry.language} className="mt-0.5 whitespace-pre-wrap break-words text-[15px] leading-snug">{entry.text}</p>
        <p className="mt-1">
          <button type="button" onClick={onToggle} className={link}>Hide translation</button>
        </p>
      </>
    );
  }

  return (
    <AnimatePresence initial>
      <motion.div
        key={entry.status + String(entry.hidden)}
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.18 }}
        aria-live="polite"
        className={`mt-2 border-t pt-2 ${mine ? 'border-indigo-300/50' : 'border-slate-300 dark:border-slate-600'}`}
        onClick={(e) => e.stopPropagation()}
      >
        {body}
      </motion.div>
    </AnimatePresence>
  );
}
