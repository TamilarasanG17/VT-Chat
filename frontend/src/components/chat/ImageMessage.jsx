import { useState } from 'react';
import { useMediaUrls, useNearViewport } from '../../hooks/useMediaUrls';
import MediaPreview from './MediaPreview.jsx';
import MediaStatus from './MediaStatus.jsx';

export default function ImageMessage({ message, disabled }) {
  const [ref, near] = useNearViewport();
  const media = useMediaUrls(message.id, near);
  const [open, setOpen] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [thumbFailed, setThumbFailed] = useState(false);
  const thumbSrc = !thumbFailed && media.thumbnailUrl ? media.thumbnailUrl : null;

  return (
    <div ref={ref}>
      {media.status === 'ready' ? (
        <button
          type="button"
          disabled={disabled}
          onClick={(e) => {
            e.stopPropagation();
            setOpen(true);
          }}
          aria-label={`Open image ${message.mediaName ?? ''}`}
          className="block overflow-hidden rounded-lg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/40"
        >
          <img
            src={thumbSrc ?? media.url}
            alt={message.mediaName || 'Image'}
            loading="lazy"
            decoding="async"
            draggable={false}
            onLoad={() => setLoaded(true)}
            onError={() => (thumbSrc ? setThumbFailed(true) : media.reload())}
            className={`max-h-72 w-full max-w-full object-cover transition-opacity ${loaded ? 'opacity-100' : 'min-h-32 min-w-48 opacity-0'}`}
          />
        </button>
      ) : (
        <MediaStatus status={media.status} error={media.error} onRetry={media.retry} />
      )}
      {open && <MediaPreview message={message} url={media.url} onClose={() => setOpen(false)} onError={media.reload} />}
    </div>
  );
}
