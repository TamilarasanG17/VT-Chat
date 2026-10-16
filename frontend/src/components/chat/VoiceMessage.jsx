import { useEffect, useRef, useState } from 'react';
import Spinner from '../Spinner.jsx';
import { PauseIcon, PlayIcon } from '../Icons.jsx';
import { useMediaUrls } from '../../hooks/useMediaUrls';
import { formatDuration } from '../../utils/media';

let currentlyPlaying = null;

export default function VoiceMessage({ message, mine, disabled }) {
  const audioRef = useRef(null);
  const [wanted, setWanted] = useState(false);
  const media = useMediaUrls(message.id, wanted);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const total = message.duration || 0;

  const play = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (currentlyPlaying && currentlyPlaying !== audio) currentlyPlaying.pause();
    currentlyPlaying = audio;
    audio.play().catch(() => setPlaying(false));
  };

  useEffect(() => {
    if (wanted && media.status === 'ready') play();
  }, [wanted, media.status]);

  useEffect(
    () => () => {
      if (currentlyPlaying === audioRef.current) currentlyPlaying = null;
    },
    [],
  );

  const toggle = (e) => {
    e.stopPropagation();
    if (!wanted) {
      setWanted(true);
      return;
    }
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) play();
    else audio.pause();
  };

  const seek = (e) => {
    const audio = audioRef.current;
    const value = Number(e.target.value);
    setTime(value);
    if (audio) audio.currentTime = value;
  };

  const loading = wanted && media.status === 'loading';
  const failed = media.status === 'error';

  return (
    <div className="flex w-60 max-w-[calc(100vw-5.5rem)] items-center gap-2" onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        onClick={toggle}
        disabled={disabled || loading}
        aria-label={playing ? 'Pause voice message' : 'Play voice message'}
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
          mine ? 'bg-white text-indigo-700' : 'bg-indigo-600 text-white'
        }`}
      >
        {loading ? <Spinner className="h-5 w-5" /> : playing ? <PauseIcon className="h-5 w-5" /> : <PlayIcon className="h-5 w-5" />}
      </button>
      <div className="min-w-0 flex-1">
        <input
          type="range"
          min={0}
          max={total || 1}
          step="0.1"
          value={Math.min(time, total || 1)}
          onChange={seek}
          disabled={disabled || !wanted || media.status !== 'ready'}
          aria-label="Seek"
          className="h-1.5 w-full cursor-pointer accent-current"
        />
        <p className="mt-0.5 text-xs opacity-80">
          {failed ? media.error : `${formatDuration(playing || time ? time : total)}${playing || time ? ` / ${formatDuration(total)}` : ''}`}
        </p>
      </div>
      {media.status === 'ready' && (
        <audio
          ref={audioRef}
          src={media.url}
          preload="metadata"
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => {
            setPlaying(false);
            setTime(0);
          }}
          onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
          onError={media.reload}
        />
      )}
    </div>
  );
}
