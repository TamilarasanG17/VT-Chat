import { useEffect, useRef, useState } from 'react';
import { ArrowLeftIcon, SparklesIcon } from '../Icons.jsx';
import AIModeSwitch from './AIModeSwitch.jsx';
import Spinner from '../Spinner.jsx';
import AIInput from './AIInput.jsx';
import AIMessage from './AIMessage.jsx';
import AIThinkingIndicator from './AIThinkingIndicator.jsx';
import { AI_MAX_MESSAGE_LENGTH, AI_SUGGESTIONS, MODE_RAG, RAG_MAX_QUERY_LENGTH } from '../../utils/ai';

export default function AIChatWindow({
  title, messages, messagesStatus, generating, error, blockedUntil, onSend, onStop, onDismissError, onBack, onReload,
  mode, onModeChange, readyCount = 0, selectedCount = 0, onOpenKnowledge, onOpenSource,
}) {
  const rag = mode === MODE_RAG;
  const scroller = useRef(null);
  const stick = useRef(true);
  const [prefill, setPrefill] = useState(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const el = scroller.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [messages, generating]);

  const onScroll = () => {
    const el = scroller.current;
    if (el) stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  };

  useEffect(() => {
    if (blockedUntil <= Date.now()) return undefined;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(id);
  }, [blockedUntil]);
  const blockedSeconds = Math.max(0, Math.ceil((blockedUntil - now) / 1000));

  const last = messages[messages.length - 1];
  const typing = generating && last?.role === 'ASSISTANT' && !!last.content;
  const showWelcome = messages.length === 0 && messagesStatus === 'ready' && !generating;

  const handleSend = (text) => {
    stick.current = true;
    return onSend(text);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex min-h-14 shrink-0 items-center gap-1.5 border-b border-slate-200 px-1.5 py-2 min-[400px]:gap-2 min-[400px]:px-3 dark:border-slate-800">
        <button
          type="button"
          onClick={onBack}
          aria-label="Back to AI chats"
          className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 md:hidden dark:text-slate-300 dark:hover:bg-slate-800"
        >
          <ArrowLeftIcon className="h-5 w-5" />
        </button>
        <h2 className="min-w-0 flex-1 truncate text-sm font-bold text-slate-900 dark:text-white">{title || 'New chat'}</h2>
        {generating && <AIThinkingIndicator compact label={typing ? 'AI is typing…' : 'AI is thinking…'} />}
      </header>

      <div className="flex shrink-0 flex-wrap items-center gap-x-3 gap-y-1 border-b border-slate-100 px-2 py-2 min-[400px]:px-3 dark:border-slate-800">
        <AIModeSwitch mode={mode} onChange={onModeChange} disabled={generating} />
        {rag && (
          <p className="min-w-0 text-xs text-slate-500 dark:text-slate-400">
            {readyCount === 0
              ? 'No Ready documents yet.'
              : selectedCount > 0
                ? `Searching ${selectedCount} selected document${selectedCount === 1 ? '' : 's'}.`
                : `Searching all ${readyCount} Ready document${readyCount === 1 ? '' : 's'}.`}{' '}
            <button type="button" onClick={onOpenKnowledge} className="font-semibold text-indigo-600 hover:underline dark:text-indigo-400">
              Manage documents
            </button>
          </p>
        )}
      </div>

      <div ref={scroller} onScroll={onScroll} className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain px-3 py-4 min-[400px]:px-4 sm:py-5" aria-live="polite" aria-busy={generating}>
        {messagesStatus === 'loading' && (
          <p className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500 dark:text-slate-400" role="status">
            <Spinner className="h-4 w-4" /> Loading messages…
          </p>
        )}

        {messagesStatus === 'error' && (
          <div className="py-10 text-center text-sm" role="alert">
            <p className="text-red-600 dark:text-red-400">{error?.message || 'Could not load this chat.'}</p>
            <button type="button" onClick={onReload} className="mt-3 rounded-lg px-3 py-1.5 font-semibold text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-500/10">
              Try again
            </button>
          </div>
        )}

        {showWelcome && (
          <div className="mx-auto flex max-w-md flex-col items-center py-6 text-center sm:py-10">
            <span className="inline-flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 text-white shadow-lg shadow-indigo-500/25">
              <SparklesIcon className="h-7 w-7" />
            </span>
            <h3 className="mt-4 text-lg font-bold text-slate-900 dark:text-white">{rag ? 'Ask your documents' : 'How can I help?'}</h3>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {rag
                ? 'Answers come only from the documents you uploaded, with the sources shown below each answer. If the documents do not contain the answer, I will say so.'
                : 'Ask about code, learning, writing or anything else. VTChat AI does not see your private chats.'}
            </p>
            <div className="mt-5 flex flex-wrap justify-center gap-2">
              {!rag && AI_SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setPrefill({ text: s, n: Date.now() })}
                  className="min-h-9 rounded-full border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 transition-[background-color,transform] active:scale-95 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  {s.trim()}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m) => (
          <AIMessage key={m.key} message={m} onOpenSource={onOpenSource} />
        ))}
      </div>

      {error && error.code !== 'LOAD_FAILED' && messagesStatus !== 'error' && (
        <div className="mx-3 mb-2 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300" role="alert">
          <p className="min-w-0 flex-1">{error.message}</p>
          <button type="button" onClick={onDismissError} className="shrink-0 font-semibold underline-offset-2 hover:underline">
            Dismiss
          </button>
        </div>
      )}

      <AIInput
        onSend={handleSend}
        onStop={onStop}
        generating={generating}
        blockedSeconds={blockedSeconds}
        prefill={prefill}
        maxLength={rag ? Math.min(AI_MAX_MESSAGE_LENGTH, RAG_MAX_QUERY_LENGTH) : AI_MAX_MESSAGE_LENGTH}
        placeholder={rag ? 'Ask about your documents…' : 'Ask VTChat AI…'}
        disabledReason={rag && readyCount === 0 ? 'Upload a document and wait until it is Ready to ask about it.' : ''}
      />
    </div>
  );
}
