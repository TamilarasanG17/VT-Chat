import { motion } from 'framer-motion';
import Avatar from '../profile/Avatar.jsx';
import Button from '../Button.jsx';
import { messageVariants } from '../../utils/motion';
import { formatRelativeTime } from '../../utils/format';

export default function ChatRequestCard({ request, busy, onAccept, onDecline }) {
  const who = request.sender;
  const working = !!busy;
  return (
    <motion.li variants={messageVariants} initial="initial" animate="animate" layout="position" className="min-w-0 border-b border-slate-100 px-3 py-3 dark:border-slate-800">
      <div className="flex min-w-0 items-center gap-3">
        <Avatar src={who.profilePictureUrl} name={who.username} size="md" />
        <div className="min-w-0 flex-1">
          <p className="break-anywhere truncate text-sm font-semibold text-slate-900 dark:text-white">{who.username}</p>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            <span className="mr-1 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">Pending</span>
            <time dateTime={request.createdAt}>{formatRelativeTime(request.createdAt)}</time>
          </p>
        </div>
      </div>
      <div className="mt-2.5 flex gap-2">
        <Button onClick={() => onAccept(request)} loading={busy === 'accept'} disabled={working} full className="flex-1 !min-h-10 !py-1.5 !text-sm">
          Accept
        </Button>
        <Button variant="secondary" onClick={() => onDecline(request)} loading={busy === 'decline'} disabled={working} full className="flex-1 !min-h-10 !py-1.5 !text-sm">
          Decline
        </Button>
      </div>
    </motion.li>
  );
}
