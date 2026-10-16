import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import Button from '../Button.jsx';
import { translationService } from '../../services/translationService';
import { FALLBACK_LANGUAGES, getLastTargetLanguage } from '../../utils/translation';

export default function TranslateDialog({ message, onCancel, onTranslate }) {
  const [languages, setLanguages] = useState(FALLBACK_LANGUAGES);
  const [target, setTarget] = useState(getLastTargetLanguage);
  const selectRef = useRef(null);
  const titleId = useId();
  const textId = useId();

  useEffect(() => {
    let active = true;
    translationService
      .getLanguages()
      .then((list) => active && Array.isArray(list) && list.length > 0 && setLanguages(list))
      .catch(() => {
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!languages.some((l) => l.code === target)) setTarget(languages[0].code);
  }, [languages, target]);

  useEffect(() => {
    selectRef.current?.focus();
    const onKey = (e) => e.key === 'Escape' && onCancel();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-3 min-[400px]:p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCancel()}
    >
      <motion.form
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={textId}
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.12 }}
        onSubmit={(e) => {
          e.preventDefault();
          onTranslate(target);
        }}
        className="max-h-[calc(100dvh-2rem)] w-full max-w-sm overflow-y-auto rounded-2xl bg-white p-4 shadow-2xl min-[400px]:p-5 dark:bg-slate-900"
      >
        <h2 id={titleId} className="text-lg font-semibold text-slate-900 dark:text-white">Translate message</h2>

        <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Original</p>
        <p
          id={textId}
          className="mt-1 max-h-32 overflow-y-auto whitespace-pre-wrap break-words rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-800 dark:bg-slate-800 dark:text-slate-100"
        >
          {message.content}
        </p>

        <label htmlFor="translate-target" className="mt-4 block text-sm font-medium text-slate-700 dark:text-slate-200">
          Translate to
        </label>
        <select
          id="translate-target"
          ref={selectRef}
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-base text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white"
        >
          {languages.map((l) => (
            <option key={l.code} value={l.code}>{l.name}</option>
          ))}
        </select>

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button full={false} variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button full={false} type="submit">Translate</Button>
        </div>
      </motion.form>
    </div>,
    document.body,
  );
}
