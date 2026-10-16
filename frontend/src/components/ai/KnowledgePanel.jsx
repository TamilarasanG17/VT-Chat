import { useRef, useState } from 'react';
import Spinner from '../Spinner.jsx';
import { ExternalIcon, FileIcon, RefreshIcon, TrashIcon, UploadIcon } from '../Icons.jsx';
import { KNOWLEDGE_ACCEPT, RAG_MAX_DOCUMENT_MB, STATUS_LABEL, isBusyStatus } from '../../utils/ai';
import { formatBytes } from '../../utils/media';

const note = 'px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400';

const badge = {
  UPLOADED: 'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200',
  PROCESSING: 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  READY: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  FAILED: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300',
};

const iconBtn =
  'inline-flex h-10 w-10 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100';

export default function KnowledgePanel({ knowledge, onConfirmDelete, onOpenError }) {
  const { documents, status, error, upload, notice, busyIds } = knowledge;
  const input = useRef(null);
  const [dragging, setDragging] = useState(false);

  const pick = async (file) => {
    if (file) await knowledge.uploadFile(file);
    if (input.current) input.current.value = '';
  };

  const open = async (d) => {
    const message = await knowledge.open(d.id, d.fileName);
    if (message) onOpenError?.(message);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="border-b border-slate-100 px-4 py-3 dark:border-slate-800">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-base font-bold text-slate-900 dark:text-white">My Knowledge</h2>
          <button
            type="button"
            onClick={() => input.current?.click()}
            disabled={!!upload}
            className="inline-flex min-h-10 items-center gap-1.5 rounded-xl bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white transition-[background-color,transform] active:scale-95 hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <UploadIcon className="h-4 w-4" />
            Upload
          </button>
          <input ref={input} type="file" accept={KNOWLEDGE_ACCEPT} className="sr-only" tabIndex={-1} aria-label="Choose a document to upload" onChange={(e) => pick(e.target.files?.[0])} />
        </div>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">PDF, TXT, MD, DOC or DOCX, up to {RAG_MAX_DOCUMENT_MB} MB. Only you can see your documents.</p>
      </div>

      <div
        className={`min-h-0 flex-1 overflow-y-auto ${dragging ? 'bg-indigo-50/60 dark:bg-indigo-500/10' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          pick(e.dataTransfer.files?.[0]);
        }}
      >
        {upload && (
          <div className="border-b border-slate-100 px-4 py-3 dark:border-slate-800" role="status">
            <p className="flex items-center gap-2 truncate text-sm font-medium text-slate-700 dark:text-slate-200">
              <Spinner className="h-4 w-4 shrink-0" />
              <span className="truncate">Uploading {upload.name}…</span>
            </p>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
              <div className="h-full bg-indigo-600 transition-all" style={{ width: `${upload.progress}%` }} />
            </div>
          </div>
        )}

        {notice && (
          <div
            className={`mx-3 mt-3 flex items-start gap-3 rounded-xl border px-3 py-2 text-sm ${
              notice.type === 'error'
                ? 'border-red-200 bg-red-50 text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300'
                : 'border-indigo-200 bg-indigo-50 text-indigo-700 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300'
            }`}
            role={notice.type === 'error' ? 'alert' : 'status'}
          >
            <p className="min-w-0 flex-1">{notice.text}</p>
            <button type="button" onClick={knowledge.dismissNotice} className="shrink-0 font-semibold underline-offset-2 hover:underline">
              Dismiss
            </button>
          </div>
        )}

        {status === 'loading' && (
          <p className={`${note} flex items-center justify-center gap-2`} role="status">
            <Spinner className="h-4 w-4" /> Loading documents…
          </p>
        )}

        {status === 'error' && (
          <div className={note} role="alert">
            <p className="text-red-600 dark:text-red-400">{error}</p>
            <button type="button" onClick={() => knowledge.reload()} className="mt-3 rounded-lg px-3 py-1.5 font-semibold text-indigo-600 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-indigo-400 dark:hover:bg-indigo-500/10">
              Try again
            </button>
          </div>
        )}

        {status === 'ready' && documents.length === 0 && !upload && (
          <div className={note}>
            <p className="font-semibold text-slate-700 dark:text-slate-200">No documents yet</p>
            <p className="mt-1">Upload a document, then switch to My Knowledge mode and ask about it.</p>
          </div>
        )}

        {status === 'ready' && documents.length > 0 && (
          <>
            {knowledge.readyCount > 0 && (
              <p className="px-4 pt-3 text-xs text-slate-500 dark:text-slate-400">
                {knowledge.selected.length === 0
                  ? 'Questions search all Ready documents. Tick documents to narrow the search.'
                  : `Questions search ${knowledge.selected.length} selected document${knowledge.selected.length === 1 ? '' : 's'}.`}
                {knowledge.selected.length > 0 && (
                  <button type="button" onClick={knowledge.clearSelection} className="ml-1 font-semibold text-indigo-600 hover:underline dark:text-indigo-400">
                    Clear
                  </button>
                )}
              </p>
            )}
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {documents.map((d) => {
                const busy = busyIds.includes(d.id);
                const ready = d.status === 'READY';
                return (
                  <li key={d.id} className="flex items-start gap-2 px-3 py-3">
                    <input
                      type="checkbox"
                      checked={knowledge.selected.includes(d.id)}
                      disabled={!ready}
                      onChange={() => knowledge.toggleSelected(d.id)}
                      aria-label={`Search only in ${d.fileName}`}
                      title={ready ? 'Include in My Knowledge questions' : 'Only Ready documents can be searched'}
                      className="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 disabled:opacity-40"
                    />
                    <FileIcon className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100" title={d.fileName}>
                        {d.fileName}
                      </p>
                      <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
                        <span>{formatBytes(d.fileSize)}</span>
                        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-semibold ${badge[d.status] || badge.UPLOADED}`}>
                          {isBusyStatus(d.status) && <Spinner className="h-3 w-3" />}
                          {STATUS_LABEL[d.status] || d.status}
                        </span>
                        {ready && d.chunkCount > 0 && <span>{d.chunkCount} passages</span>}
                      </p>
                      {d.status === 'FAILED' && d.errorMessage && (
                        <p className="mt-1 text-xs text-red-600 dark:text-red-400" role="alert">
                          {d.errorMessage}
                        </p>
                      )}
                    </div>
                    <div className="flex shrink-0 items-center">
                      <button type="button" onClick={() => open(d)} aria-label={`Open ${d.fileName}`} title="Open" className={iconBtn}>
                        <ExternalIcon className="h-4 w-4" />
                      </button>
                      {(ready || d.status === 'FAILED') && (
                        <button type="button" onClick={() => knowledge.reprocess(d)} disabled={busy} aria-label={`Reprocess ${d.fileName}`} title="Reprocess" className={iconBtn}>
                          <RefreshIcon className="h-4 w-4" />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={async () => {
                          if (await onConfirmDelete(d)) knowledge.remove(d);
                        }}
                        disabled={busy}
                        aria-label={`Delete ${d.fileName}`}
                        title="Delete"
                        className={`${iconBtn} hover:!bg-red-50 hover:!text-red-600 dark:hover:!bg-red-500/10`}
                      >
                        <TrashIcon className="h-4 w-4" />
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
