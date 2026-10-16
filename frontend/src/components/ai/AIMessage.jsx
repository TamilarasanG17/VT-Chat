import { memo, useState } from 'react';
import { motion } from 'framer-motion';
import { CheckIcon, CopyIcon, SparklesIcon } from '../Icons.jsx';
import { copyText } from '../../utils/clipboard';
import { formatMessageTime } from '../../utils/format';
import AISources from './AISources.jsx';
import AIThinkingIndicator from './AIThinkingIndicator.jsx';
import MarkdownView from './MarkdownView.jsx';

function AIMessage({ message, onOpenSource }) {
  const [copied, setCopied] = useState(false);

  if (message.role === 'USER') {
    return (
      <motion.div initial={message.pending ? { opacity: 0, y: 6 } : false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18 }} className="flex justify-end">
        <div className={`min-w-0 max-w-[88%] rounded-2xl rounded-br-md bg-indigo-600 px-3.5 py-2.5 text-white sm:max-w-[80%] ${message.pending ? 'opacity-80' : ''}`}>
          <p className="whitespace-pre-wrap break-anywhere text-[0.95rem] leading-relaxed">{message.content}</p>
        </div>
      </motion.div>
    );
  }

  const waiting = message.streaming && !message.content;
  const copyAll = async () => {
    if (await copyText(message.content)) {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  };

  return (
    <motion.div initial={message.streaming ? { opacity: 0, y: 6 } : false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18 }} className="flex items-start gap-2 min-[400px]:gap-3">
      <span className="mt-0.5 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 text-white" aria-hidden="true">
        <SparklesIcon className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="mb-1 flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
          VTChat AI
          {message.mode === 'RAG' && (
            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[0.65rem] font-semibold text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">From My Knowledge</span>
          )}
        </p>
        {waiting ? (
          <AIThinkingIndicator />
        ) : (
          <div className="text-slate-800 dark:text-slate-100">
            <MarkdownView source={message.content} />
            {message.streaming && <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse rounded-sm bg-indigo-500 align-middle" aria-hidden="true" />}
          </div>
        )}
        {!message.streaming && <AISources sources={message.sources} onOpen={onOpenSource} />}
        {!message.streaming && message.content && (
          <div className="mt-1.5 flex items-center gap-3 text-xs text-slate-400">
            {message.createdAt && <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>}
            <button
              type="button"
              onClick={copyAll}
              className="inline-flex min-h-8 items-center gap-1 rounded px-1.5 py-0.5 font-semibold hover:text-slate-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:hover:text-slate-200"
            >
              {copied ? <CheckIcon className="h-3 w-3" /> : <CopyIcon className="h-3 w-3" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        )}
      </div>
    </motion.div>
  );
}

export default memo(AIMessage);
