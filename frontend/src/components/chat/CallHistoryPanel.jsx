import { useCallback } from 'react';
import { motion } from 'framer-motion';
import { fadeVariants } from '../../utils/motion';
import CallHistoryList from '../call/CallHistoryList.jsx';
import { CloseIcon } from '../Icons.jsx';
import { callService } from '../../services/callService';

export default function CallHistoryPanel({ conversationId, onClose }) {
  const loadPage = useCallback((page, size) => callService.getConversationCalls(conversationId, page, size), [conversationId]);
  return (
    <motion.section variants={fadeVariants} initial="initial" animate="animate" aria-label="Call history" className="max-h-72 overflow-y-auto border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center justify-between px-4 pt-3">
        <h2 className="text-sm font-semibold text-slate-900 dark:text-white">Call history</h2>
        <button type="button" onClick={onClose} aria-label="Close call history" className="flex h-9 w-9 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:hover:bg-slate-800">
          <CloseIcon className="h-4 w-4" />
        </button>
      </div>
      <CallHistoryList loadPage={loadPage} emptyText="No calls in this chat yet" />
    </motion.section>
  );
}
