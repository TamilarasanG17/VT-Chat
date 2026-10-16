import { motion } from 'framer-motion';
import { previewText } from '../../utils/media';
import { CloseIcon, EditIcon, ReplyIcon } from '../Icons.jsx';

export default function ComposerBanner({ composer, nameOf, onCancel }) {
  if (!composer) return null;
  const { mode, message } = composer;
  const isReply = mode === 'reply';
  const Icon = isReply ? ReplyIcon : EditIcon;

  return (
    <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.16 }} className="mb-2 flex items-start gap-2 rounded-xl border-l-4 border-indigo-500 bg-slate-100 px-3 py-2 dark:bg-slate-800">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
      <div className="min-w-0 flex-1 text-sm">
        <p className="font-semibold text-slate-800 dark:text-slate-100">
          {isReply ? `Replying to ${nameOf(message.senderId)}` : 'Editing message'}
        </p>
        <p className="truncate break-all text-slate-600 dark:text-slate-300">{message.messageType && message.messageType !== 'TEXT' ? previewText(message) : <>&ldquo;{message.content}&rdquo;</>}</p>
      </div>
      <button
        type="button"
        onClick={onCancel}
        aria-label={isReply ? 'Cancel reply' : 'Cancel edit'}
        className="flex min-h-8 min-w-8 shrink-0 items-center justify-center rounded-md px-1.5 py-1 text-xs font-semibold text-slate-600 hover:bg-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:text-slate-300 dark:hover:bg-slate-700"
      >
        <span className="hidden sm:inline">{isReply ? 'Cancel Reply' : 'Cancel'}</span>
        <CloseIcon className="h-4 w-4 sm:hidden" />
      </button>
    </motion.div>
  );
}
