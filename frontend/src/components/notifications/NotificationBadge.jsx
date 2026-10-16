import { motion } from 'framer-motion';
import { badgeText } from '../../utils/conversationList';

export default function NotificationBadge({ count, className = '', label }) {
  if (!count || count < 1) return null;
  return (
    <motion.span
      key={count}
      initial={{ scale: 0.7, opacity: 0.6 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 500, damping: 22 }}
      role="status"
      aria-label={label ?? `${count} unread`}
      className={`inline-flex min-w-[1.25rem] items-center justify-center rounded-full bg-indigo-600 px-1.5 text-[11px] font-bold leading-5 text-white ${className}`}
    >
      {badgeText(count)}
    </motion.span>
  );
}
