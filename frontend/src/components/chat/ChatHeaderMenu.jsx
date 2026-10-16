import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ClockIcon, DotsIcon, SearchIcon } from '../Icons.jsx';
import { dropdownVariants } from '../../utils/motion';

export default function ChatHeaderMenu({ onCalls, onSearch, callsOpen, searchOpen }) {
  const [open, setOpen] = useState(false);
  const box = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => box.current && !box.current.contains(e.target) && setOpen(false);
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('pointerdown', onDown, true);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown, true);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const item =
    'flex min-h-11 w-full items-center gap-3 px-3 text-left text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:bg-slate-100 focus-visible:outline-none dark:text-slate-200 dark:hover:bg-slate-700/60 dark:focus-visible:bg-slate-700';

  return (
    <div ref={box} className="relative min-[380px]:hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="More conversation actions"
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-10 w-10 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-slate-300 dark:hover:bg-slate-800"
      >
        <DotsIcon className="h-5 w-5" />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            variants={dropdownVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            style={{ transformOrigin: 'top right' }}
            className="absolute right-0 top-full z-40 mt-1 w-48 max-w-[calc(100vw-1rem)] overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-xl dark:border-slate-700 dark:bg-slate-800"
          >
            <button
              type="button"
              role="menuitem"
              aria-pressed={callsOpen}
              className={item}
              onClick={() => {
                setOpen(false);
                onCalls();
              }}
            >
              <ClockIcon className="h-5 w-5" /> Call history
            </button>
            <button
              type="button"
              role="menuitem"
              aria-pressed={searchOpen}
              className={item}
              onClick={() => {
                setOpen(false);
                onSearch();
              }}
            >
              <SearchIcon className="h-5 w-5" /> Search messages
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
