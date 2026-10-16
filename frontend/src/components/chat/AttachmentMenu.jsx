import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { dropdownVariants } from '../../utils/motion';
import { FileIcon, PaperclipIcon } from '../Icons.jsx';
import { ACCEPT } from '../../utils/media';

const OPTIONS = [
  { kind: 'IMAGE', label: 'Photo', emoji: '📷' },
  { kind: 'VIDEO', label: 'Video', emoji: '🎥' },
  { kind: 'FILE', label: 'Document', emoji: null },
];

export default function AttachmentMenu({ disabled, onPick }) {
  const [open, setOpen] = useState(false);
  const root = useRef(null);
  const inputs = useRef({});

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => root.current && !root.current.contains(e.target) && setOpen(false);
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('pointerdown', onDown, true);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown, true);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const choose = (kind) => {
    setOpen(false);
    inputs.current[kind]?.click();
  };

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        aria-label="Attach a file"
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-11 w-11 items-center justify-center rounded-full text-slate-600 transition-[background-color,transform] active:scale-90 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:text-slate-300 dark:hover:bg-slate-800"
      >
        <PaperclipIcon className="h-6 w-6" />
      </button>

      {OPTIONS.map(({ kind }) => (
        <input
          key={kind}
          ref={(el) => {
            inputs.current[kind] = el;
          }}
          type="file"
          accept={ACCEPT[kind]}
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = '';
            if (file) onPick(file);
          }}
        />
      ))}

      <AnimatePresence>
      {open && (
        <motion.div role="menu" variants={dropdownVariants} initial="initial" animate="animate" exit="exit" style={{ transformOrigin: 'bottom left' }} className="absolute bottom-12 left-0 z-30 w-44 max-w-[calc(100vw-1.5rem)] rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl dark:border-slate-700 dark:bg-slate-900">
          {OPTIONS.map(({ kind, label, emoji }) => (
            <button
              key={kind}
              type="button"
              role="menuitem"
              onClick={() => choose(kind)}
              className="flex min-h-11 w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-700 hover:bg-slate-100 focus-visible:bg-slate-100 focus-visible:outline-none dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {emoji ? <span className="w-5 text-center">{emoji}</span> : <FileIcon className="h-5 w-5" />} {label}
            </button>
          ))}
        </motion.div>
      )}
      </AnimatePresence>
    </div>
  );
}
