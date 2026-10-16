import { useState } from 'react';
import Spinner from '../Spinner.jsx';
import { DownloadIcon, FileIcon } from '../Icons.jsx';
import { API_URL } from '../../services/api';
import { mediaService } from '../../services/mediaService';
import { showError, toastError } from '../../utils/alerts';
import { canShowDownload, isPdf, useDownloaded } from '../../utils/downloadState';
import { downloadFileMessage } from '../../utils/fileDownload';
import { extensionOf, formatBytes } from '../../utils/media';

const btn =
  'shrink-0 rounded-full p-2.5 transition hover:bg-black/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:opacity-60 dark:hover:bg-white/10';

export default function FileMessage({ message, mine, disabled, currentUserId }) {
  const [busy, setBusy] = useState(false);
  const [opening, setOpening] = useState(false);
  const downloadedLocally = useDownloaded(currentUserId, message.id);
  const ext = extensionOf(message.mediaName).toUpperCase();
  const pdf = isPdf(message);
  const showDownload = canShowDownload(message, currentUserId, downloadedLocally);

  const download = async (e) => {
    e.stopPropagation();
    if (busy) return;
    setBusy(true);
    try {
      await downloadFileMessage(message, currentUserId);
    } finally {
      setBusy(false);
    }
  };

  const view = async (e) => {
    e.stopPropagation();
    if (opening) return;
    setOpening(true);
    try {
      await mediaService.openInNewTab(message.id, API_URL);
    } catch (err) {
      if (err?.message === 'POPUP_BLOCKED') toastError('Allow pop-ups to open the PDF in a new tab');
      else showError('Unable to open file', 'This file could not be opened. Please try again.');
    } finally {
      setOpening(false);
    }
  };

  return (
    <div className={`flex w-64 max-w-[calc(100vw-5.5rem)] items-center gap-3 rounded-lg p-2 ${mine ? 'bg-indigo-700/60' : 'bg-slate-200/80 dark:bg-slate-700/60'}`}>
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${mine ? 'bg-indigo-500' : 'bg-white dark:bg-slate-900'}`}>
        <FileIcon className="h-6 w-6" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold" title={message.mediaName}>{message.mediaName}</span>
        <span className="block text-xs opacity-80">{[ext, formatBytes(message.mediaSize)].filter(Boolean).join(' · ')}</span>
        {pdf && (
          <button
            type="button"
            onClick={view}
            disabled={disabled || opening}
            aria-label={`Open ${message.mediaName} in a new tab`}
            className="mt-0.5 text-xs font-semibold underline underline-offset-2 disabled:opacity-60"
          >
            {opening ? 'Opening…' : 'View'}
          </button>
        )}
      </span>
      {showDownload && (
        <button type="button" onClick={download} disabled={disabled || busy} aria-label={`Download ${message.mediaName}`} className={btn}>
          {busy ? <Spinner className="h-5 w-5" /> : <DownloadIcon className="h-5 w-5" />}
        </button>
      )}
    </div>
  );
}
