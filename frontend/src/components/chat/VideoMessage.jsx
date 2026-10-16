import { useRef } from 'react';
import { useMediaUrls, useNearViewport } from '../../hooks/useMediaUrls';
import MediaStatus from './MediaStatus.jsx';

export default function VideoMessage({ message, disabled }) {
  const [ref, near] = useNearViewport();
  const media = useMediaUrls(message.id, near);
  const resumeAt = useRef(0);

  return (
    <div ref={ref}>
      {media.status === 'ready' ? (
        <video
          src={media.url}
          controls
          preload="metadata"
          playsInline
          onTimeUpdate={(e) => {
            resumeAt.current = e.currentTarget.currentTime;
          }}
          onLoadedMetadata={(e) => {
            if (resumeAt.current > 0) e.currentTarget.currentTime = resumeAt.current;
          }}
          onError={media.reload}
          onClick={(e) => e.stopPropagation()}
          aria-label={message.mediaName || 'Video'}
          className={`max-h-72 w-full max-w-full rounded-lg bg-black ${disabled ? 'pointer-events-none' : ''}`}
        >
          Your browser cannot play this video.
        </video>
      ) : (
        <MediaStatus status={media.status} error={media.error} onRetry={media.retry} />
      )}
    </div>
  );
}
