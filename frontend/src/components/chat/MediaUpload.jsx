import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { modalVariants } from '../../utils/motion';
import Button from '../Button.jsx';
import { FileIcon } from '../Icons.jsx';
import { mediaService } from '../../services/mediaService';
import { getChatErrorMessage } from '../../utils/errors';
import { formatBytes, kindOfFile } from '../../utils/media';
import { MAX_MESSAGE_LENGTH, messageLength } from '../../utils/messages';

export default function MediaUpload({ file, conversationId, replyToMessageId, onSent, onClose }) {
  const kind = kindOfFile(file);
  const [caption, setCaption] = useState('');
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const abort = useRef(null);
  const previewUrl = useMemo(() => (kind === 'IMAGE' || kind === 'VIDEO' ? URL.createObjectURL(file) : null), [file, kind]);
  const tooLong = messageLength(caption) > MAX_MESSAGE_LENGTH;

  useEffect(() => () => previewUrl && URL.revokeObjectURL(previewUrl), [previewUrl]);
  useEffect(() => () => abort.current?.abort(), []);

  const cancel = () => {
    abort.current?.abort();
    onClose();
  };

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && cancel();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const send = async () => {
    if (uploading || tooLong) return;
    setError('');
    setUploading(true);
    setProgress(0);
    abort.current = new AbortController();
    try {
      const message = await mediaService.uploadMedia(conversationId, {
        file,
        caption: caption.trim(),
        replyToMessageId,
        onProgress: setProgress,
        signal: abort.current.signal,
      });
      onSent(message);
      onClose();
    } catch (err) {
      if (err?.code === 'ERR_CANCELED') return;
      setUploading(false);
      setError(getChatErrorMessage(err, 'Unable to send this file'));
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-3 min-[400px]:p-4 sm:items-center" role="dialog" aria-modal="true" aria-label="Send attachment">
      <motion.div variants={modalVariants} initial="initial" animate="animate" className="flex max-h-[calc(100dvh-1.5rem)] w-full max-w-md flex-col gap-3 overflow-y-auto rounded-2xl bg-white p-4 shadow-2xl dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white">Send {kind === 'IMAGE' ? 'photo' : kind === 'VIDEO' ? 'video' : 'document'}</h2>

        <div className="flex items-center justify-center overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800">
          {kind === 'IMAGE' && <img src={previewUrl} alt="Selected" className="max-h-64 w-full object-contain" />}
          {kind === 'VIDEO' && <video src={previewUrl} controls preload="metadata" className="max-h-64 w-full bg-black" />}
          {kind === 'FILE' && (
            <div className="flex items-center gap-3 p-4">
              <FileIcon className="h-10 w-10 shrink-0 text-indigo-600" />
              <span className="min-w-0">
                <span className="block truncate font-medium text-slate-900 dark:text-white">{file.name}</span>
                <span className="text-sm text-slate-500 dark:text-slate-400">{formatBytes(file.size)}</span>
              </span>
            </div>
          )}
        </div>
        {kind !== 'FILE' && (
          <p className="truncate text-xs text-slate-500 dark:text-slate-400">{file.name} · {formatBytes(file.size)}</p>
        )}

        <textarea
          rows={2}
          value={caption}
          onChange={(e) => setCaption(e.target.value)}
          disabled={uploading}
          aria-label="Caption"
          placeholder="Add a caption (optional)"
          className="resize-none rounded-xl border border-slate-300 bg-white px-3 py-2 text-base text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/20 dark:border-slate-600 dark:bg-slate-800 dark:text-white"
        />
        {tooLong && <p className="text-xs text-red-600 dark:text-red-400">Caption is too long (maximum {MAX_MESSAGE_LENGTH} characters)</p>}

        {uploading && (
          <div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress} aria-label="Upload progress">
              <div className="h-full bg-indigo-600 transition-[width] duration-150" style={{ width: `${progress}%` }} />
            </div>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{progress < 100 ? `Uploading… ${progress}%` : 'Processing…'}</p>
          </div>
        )}
        {error && <p className="text-sm text-red-600 dark:text-red-400" role="alert">{error}</p>}

        <div className="flex flex-col-reverse gap-2 min-[400px]:flex-row min-[400px]:justify-end">
          <Button full={false} variant="secondary" onClick={cancel} className="max-[399px]:w-full">Cancel</Button>
          <Button full={false} onClick={send} loading={uploading} disabled={tooLong} className="max-[399px]:w-full">{error ? 'Retry' : 'Send'}</Button>
        </div>
      </motion.div>
    </div>,
    document.body,
  );
}
