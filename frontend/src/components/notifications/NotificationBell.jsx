import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import NotificationBadge from './NotificationBadge.jsx';
import NotificationPanel from './NotificationPanel.jsx';
import { BellIcon } from '../Icons.jsx';
import { useNotifications } from '../../hooks/useNotifications';
import { dropdownVariants } from '../../utils/motion';

export default function NotificationBell() {
  const { unread } = useNotifications();
  const [open, setOpen] = useState(false);
  const box = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (box.current && !box.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        aria-haspopup="dialog"
        className="relative flex h-10 w-10 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-slate-300 dark:hover:bg-slate-800"
      >
        <BellIcon className="h-5 w-5" />
        <NotificationBadge count={unread} className="absolute -right-0.5 -top-0.5" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            role="dialog"
            aria-label="Notifications"
            variants={dropdownVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="fixed inset-x-2 top-[3.75rem] z-40 flex max-h-[70dvh] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-96 dark:border-slate-700 dark:bg-slate-900"
          >
            <NotificationPanel onNavigate={() => setOpen(false)} className="max-h-[70dvh]" />
            <Link
              to="/notifications"
              onClick={() => setOpen(false)}
              className="border-t border-slate-200 px-4 py-2.5 text-center text-sm font-semibold text-indigo-600 hover:bg-slate-50 dark:border-slate-800 dark:text-indigo-300 dark:hover:bg-slate-800"
            >
              Open notification center
            </Link>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
