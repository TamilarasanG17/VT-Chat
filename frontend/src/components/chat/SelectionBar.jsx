import { CloseIcon, CopyIcon, ForwardIcon, TrashIcon } from '../Icons.jsx';

const btn =
  'inline-flex min-h-10 min-w-10 items-center justify-center gap-1.5 rounded-lg px-2.5 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-40';

export default function SelectionBar({ count, canCopyOrForward, onCopy, onForward, onDelete, onCancel }) {
  return (
    <header className="flex min-h-14 shrink-0 items-center gap-0.5 border-b border-slate-200 bg-indigo-50 px-1.5 py-2 min-[400px]:gap-1 sm:px-4 dark:border-slate-800 dark:bg-indigo-500/10">
      <button type="button" onClick={onCancel} aria-label="Cancel selection" className={`${btn} text-slate-700 hover:bg-white/70 dark:text-slate-200 dark:hover:bg-slate-800`}>
        <CloseIcon className="h-5 w-5" />
      </button>
      <p className="mr-auto text-sm font-semibold text-slate-900 dark:text-white" role="status">
        {count} selected
      </p>
      <button type="button" onClick={onCopy} disabled={!canCopyOrForward} className={`${btn} text-slate-700 hover:bg-white/70 dark:text-slate-200 dark:hover:bg-slate-800`}>
        <CopyIcon className="h-4 w-4" /> <span className="hidden sm:inline">Copy</span>
      </button>
      <button type="button" onClick={onForward} disabled={!canCopyOrForward} className={`${btn} text-slate-700 hover:bg-white/70 dark:text-slate-200 dark:hover:bg-slate-800`}>
        <ForwardIcon className="h-4 w-4" /> <span className="hidden sm:inline">Forward</span>
      </button>
      <button type="button" onClick={onDelete} className={`${btn} text-red-600 hover:bg-white/70 dark:text-red-400 dark:hover:bg-slate-800`}>
        <TrashIcon className="h-4 w-4" /> <span className="hidden sm:inline">Delete</span>
      </button>
      <button type="button" onClick={onCancel} className={`${btn} hidden text-slate-700 hover:bg-white/70 sm:inline-flex dark:text-slate-200 dark:hover:bg-slate-800`}>
        Cancel
      </button>
    </header>
  );
}
