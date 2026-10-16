import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { CloseIcon, DownloadIcon } from '../Icons.jsx';
import { API_URL } from '../../services/api';
import { mediaService } from '../../services/mediaService';
import { toastError } from '../../utils/alerts';

export default function MediaPreview({ message, url, onClose, onError }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previous;
    };
  }, [onClose]);

  const download = async () => {
    try {
      await mediaService.download(message.id, API_URL);
    } catch {
      toastError('Unable to download this file');
    }
  };

  const btn =
    'rounded-full bg-white/10 p-2.5 text-white transition hover:bg-white/25 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/40';

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={message.mediaName || 'Image preview'}
      className="fixed inset-0 z-[60] flex flex-col bg-black/90"
      onClick={onClose}
    >
      <div className="flex items-center justify-between gap-3 p-3 text-white" onClick={(e) => e.stopPropagation()}>
        <p className="min-w-0 truncate text-sm font-medium">{message.mediaName}</p>
        <div className="flex shrink-0 gap-2">
          <button type="button" onClick={download} aria-label="Download" className={btn}>
            <DownloadIcon className="h-5 w-5" />
          </button>
          <button type="button" onClick={onClose} aria-label="Close preview" autoFocus className={btn}>
            <CloseIcon className="h-5 w-5" />
          </button>
        </div>
      </div>
      <div className="flex min-h-0 flex-1 items-center justify-center p-3">
        <img
          src={url}
          alt={message.mediaName || 'Image'}
          onClick={(e) => e.stopPropagation()}
          onError={onError}
          className="max-h-full max-w-full rounded-lg object-contain"
        />
      </div>
      {message.content && (
        <p className="p-3 text-center text-sm text-white" onClick={(e) => e.stopPropagation()}>{message.content}</p>
      )}
    </div>,
    document.body,
  );
}
