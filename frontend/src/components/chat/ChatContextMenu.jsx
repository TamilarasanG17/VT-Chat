import { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { dropdownVariants } from '../../utils/motion';
import { ArchiveIcon, BellIcon, ChatIcon, CheckIcon, PinIcon, SpeakerOffIcon, TrashIcon } from '../Icons.jsx';

export function chatActions(c) {
  return [
    { key: 'open', label: 'Open', Icon: ChatIcon },
    c.unreadCount > 0 && { key: 'read', label: 'Mark as read', Icon: CheckIcon },
    { key: c.muted ? 'unmute' : 'mute', label: c.muted ? 'Unmute' : 'Mute', Icon: c.muted ? BellIcon : SpeakerOffIcon },
    { key: c.pinned ? 'unpin' : 'pin', label: c.pinned ? 'Unpin' : 'Pin', Icon: PinIcon },
    { key: c.archived ? 'unarchive' : 'archive', label: c.archived ? 'Unarchive' : 'Archive', Icon: ArchiveIcon },
    { key: 'clear', label: 'Clear chat', Icon: TrashIcon, danger: true },
    { key: 'delete', label: 'Delete', Icon: TrashIcon, danger: true },
  ].filter(Boolean);
}

export default function ChatContextMenu({ conversation, onSelect, onClose }) {
  const ref = useRef(null);

  useEffect(() => {
    const onDown = (e) => ref.current && !ref.current.contains(e.target) && onClose();
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const items = [...ref.current.querySelectorAll('[role="menuitem"]')];
        const i = items.indexOf(document.activeElement);
        items[(i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus();
      }
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    ref.current?.querySelector('[role="menuitem"]')?.focus();
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  return (
    <motion.div
      ref={ref}
      variants={dropdownVariants}
      initial="initial"
      animate="animate"
      exit="exit"
      style={{ transformOrigin: 'top right' }}
      role="menu"
      aria-label={`Actions for ${conversation.otherUser.username}`}
      className="absolute right-3 top-12 z-30 w-48 max-w-[calc(100%-1.5rem)] overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-xl dark:border-slate-700 dark:bg-slate-800"
    >
      {chatActions(conversation).map(({ key, label, Icon, danger }) => (
        <button
          key={key}
          type="button"
          role="menuitem"
          onClick={() => onSelect(key)}
          className={`flex min-h-10 w-full items-center gap-2 px-3 py-2 text-left text-sm focus-visible:bg-slate-100 focus-visible:outline-none dark:focus-visible:bg-slate-700 ${
            danger ? 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10' : 'text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700/60'
          }`}
        >
          <Icon className="h-4 w-4" /> {label}
        </button>
      ))}
    </motion.div>
  );
}
