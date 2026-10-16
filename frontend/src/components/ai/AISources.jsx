import { useState } from 'react';
import { FileIcon } from '../Icons.jsx';

export default function AISources({ sources, onOpen }) {
  const [problem, setProblem] = useState('');
  if (!sources?.length) return null;

  const open = async (s) => {
    setProblem('');
    const message = await onOpen?.(s);
    if (message) setProblem(message);
  };

  return (
    <div className="mt-2" aria-label="Sources">
      <p className="mb-1 text-xs font-semibold text-slate-500 dark:text-slate-400">Sources</p>
      <ul className="flex flex-wrap gap-1.5">
        {sources.map((s) => (
          <li key={`${s.documentId}:${s.pageNumber ?? 'x'}`} className="min-w-0 max-w-full">
            <button
              type="button"
              onClick={() => open(s)}
              title={`Open ${s.fileName}${s.pageNumber ? `, page ${s.pageNumber}` : ''}`}
              className="inline-flex max-w-full items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-medium text-slate-700 transition hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:border-indigo-500/50 dark:hover:bg-indigo-500/10 dark:hover:text-indigo-300"
            >
              <FileIcon className="h-3.5 w-3.5 shrink-0" />
              <span className="truncate">{s.fileName}</span>
              {s.pageNumber != null && <span className="shrink-0 text-slate-400">p. {s.pageNumber}</span>}
            </button>
          </li>
        ))}
      </ul>
      {problem && (
        <p className="mt-1 text-xs text-red-600 dark:text-red-400" role="alert">
          {problem}
        </p>
      )}
    </div>
  );
}
