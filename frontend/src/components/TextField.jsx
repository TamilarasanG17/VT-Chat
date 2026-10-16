import { useId } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

export default function TextField({ label, error, hint, rightSlot, className = '', ...inputProps }) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  return (
    <div className={`min-w-0 ${className}`}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-invalid={error ? 'true' : 'false'}
          aria-describedby={error ? errorId : hint ? hintId : undefined}
          className={`block w-full min-w-0 rounded-xl border bg-white px-3.5 py-2.5 text-base text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:ring-2 disabled:bg-slate-100 disabled:text-slate-500 dark:bg-slate-800 dark:text-white dark:placeholder:text-slate-500 dark:disabled:bg-slate-800/60 dark:disabled:text-slate-400 ${
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/25'
              : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/25 dark:border-slate-600'
          } ${rightSlot ? 'pr-11' : ''}`}
          {...inputProps}
        />
        {rightSlot && <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{rightSlot}</div>}
      </div>
      <AnimatePresence initial={false} mode="wait">
        {error ? (
          <motion.p
            key="error"
            id={errorId}
            role="alert"
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="mt-1.5 text-sm text-red-600 dark:text-red-400"
          >
            {error}
          </motion.p>
        ) : (
          hint && (
            <p key="hint" id={hintId} className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">
              {hint}
            </p>
          )
        )}
      </AnimatePresence>
    </div>
  );
}
