import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Spinner from '../Spinner.jsx';
import { SendIcon, StopIcon, TrashIcon } from '../Icons.jsx';
import { mediaService } from '../../services/mediaService';
import { showError, toastError } from '../../utils/alerts';
import { getChatErrorMessage } from '../../utils/errors';
import { LIMITS_MB, MAX_VOICE_SECONDS, formatDuration } from '../../utils/media';

const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

function microphoneError(err) {
  switch (err?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'Microphone access was blocked. Allow the microphone for this site in your browser settings, then try again.';
    case 'NotFoundError':
    case 'OverconstrainedError':
      return 'No microphone was found. Connect one and try again.';
    case 'NotReadableError':
    case 'AbortError':
      return 'Your microphone is being used by another app. Close it and try again.';
    default:
      return 'Unable to start recording. Please try again.';
  }
}

export const voiceSupported = () =>
  typeof window !== 'undefined' && !!navigator.mediaDevices?.getUserMedia && typeof window.MediaRecorder !== 'undefined';

export default function VoiceRecorder({ conversationId, replyToMessageId, banner = null, onSent, onClose }) {
  const [phase, setPhase] = useState('starting');
  const [seconds, setSeconds] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const recorder = useRef(null);
  const stream = useRef(null);
  const chunks = useRef([]);
  const startedAt = useRef(0);
  const timer = useRef(null);
  const abort = useRef(null);
  const cancelled = useRef(false);
  const audioUrl = useMemo(() => (result ? URL.createObjectURL(result.blob) : null), [result]);

  useEffect(() => () => audioUrl && URL.revokeObjectURL(audioUrl), [audioUrl]);

  const releaseMic = useCallback(() => {
    clearInterval(timer.current);
    stream.current?.getTracks().forEach((t) => t.stop());
    stream.current = null;
  }, []);

  const stop = useCallback(() => {
    if (recorder.current?.state === 'recording') recorder.current.stop();
  }, []);

  useEffect(() => {
    let alive = true;
    cancelled.current = false;
    (async () => {
      if (!voiceSupported()) {
        await showError('Voice messages unavailable', 'Your browser does not support voice recording, or the page is not secure (HTTPS).');
        onClose();
        return;
      }
      try {
        const media = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (!alive) {
          media.getTracks().forEach((t) => t.stop());
          return;
        }
        stream.current = media;
        const mimeType = MIME_CANDIDATES.find((m) => MediaRecorder.isTypeSupported?.(m));
        const rec = new MediaRecorder(media, mimeType ? { mimeType } : undefined);
        recorder.current = rec;
        chunks.current = [];
        rec.ondataavailable = (e) => e.data.size > 0 && chunks.current.push(e.data);
        rec.onstop = () => {
          const duration = (Date.now() - startedAt.current) / 1000;
          const blob = new Blob(chunks.current, { type: rec.mimeType || mimeType || 'audio/webm' });
          releaseMic();
          if (cancelled.current) return;
          if (blob.size === 0 || duration < 0.5) {
            toastError('Recording was too short');
            onClose();
            return;
          }
          if (blob.size > LIMITS_MB.VOICE * 1024 * 1024) {
            setError(`That recording is too large (maximum ${LIMITS_MB.VOICE} MB)`);
          }
          setResult({ blob, duration });
          setPhase('review');
        };
        rec.onerror = () => {
          releaseMic();
          setError('Recording failed. Please try again.');
          setPhase('review');
        };
        startedAt.current = Date.now();
        rec.start(250);
        setPhase('recording');
        timer.current = setInterval(() => {
          const s = Math.floor((Date.now() - startedAt.current) / 1000);
          setSeconds(s);
          if (s >= MAX_VOICE_SECONDS) stop();
        }, 250);
      } catch (err) {
        if (!alive) return;
        await showError('Cannot record', microphoneError(err));
        onClose();
      }
    })();
    return () => {
      alive = false;
      cancelled.current = true;
      abort.current?.abort();
      if (recorder.current?.state === 'recording') recorder.current.stop();
      releaseMic();
    };
  }, []);

  const discard = () => {
    cancelled.current = true;
    abort.current?.abort();
    if (recorder.current?.state === 'recording') recorder.current.stop();
    releaseMic();
    onClose();
  };

  const send = async () => {
    if (!result || phase === 'uploading') return;
    if (result.blob.size > LIMITS_MB.VOICE * 1024 * 1024) return;
    setError('');
    setPhase('uploading');
    abort.current = new AbortController();
    try {
      const message = await mediaService.uploadVoice(conversationId, {
        blob: result.blob,
        duration: Math.round(result.duration * 10) / 10,
        replyToMessageId,
        signal: abort.current.signal,
      });
      onSent(message);
      onClose();
    } catch (err) {
      if (err?.code === 'ERR_CANCELED') return;
      setError(getChatErrorMessage(err, 'Unable to send the voice message'));
      setPhase('review');
    }
  };

  const round = 'flex h-11 w-11 shrink-0 items-center justify-center rounded-full focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/40 disabled:opacity-50';

  return (
    <div className="pb-safe shrink-0 border-t border-slate-200 bg-white p-2 min-[400px]:p-3 dark:border-slate-800 dark:bg-slate-900" role="group" aria-label="Voice message recorder">
      {banner}
      <div className="flex items-center gap-1 min-[400px]:gap-2">
        <button type="button" onClick={discard} aria-label="Discard recording" className={`${round} text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800`}>
          <TrashIcon className="h-5 w-5" />
        </button>

        <div className="flex min-w-0 flex-1 items-center gap-3 rounded-full border border-slate-300 px-3 py-1.5 dark:border-slate-600">
          {phase === 'starting' && <span className="text-sm text-slate-500 dark:text-slate-400">Waiting for microphone…</span>}
          {phase === 'recording' && (
            <>
              <span className="h-3 w-3 shrink-0 animate-pulse rounded-full bg-red-500" aria-hidden="true" />
              <span className="font-mono text-sm tabular-nums text-slate-800 dark:text-slate-100" role="timer" aria-label="Recording time">{formatDuration(seconds)}</span>
              <span className="truncate text-xs text-slate-500 dark:text-slate-400">Recording…</span>
            </>
          )}
          {(phase === 'review' || phase === 'uploading') && audioUrl && (
            <audio src={audioUrl} controls preload="metadata" className="h-9 w-full min-w-0" aria-label="Preview your recording" />
          )}
        </div>

        {phase === 'recording' ? (
          <button type="button" onClick={stop} aria-label="Stop recording" className={`${round} bg-red-600 text-white hover:bg-red-700`}>
            <StopIcon className="h-5 w-5" />
          </button>
        ) : (
          <button
            type="button"
            onClick={send}
            disabled={phase !== 'review' || !result || result.blob.size > LIMITS_MB.VOICE * 1024 * 1024}
            aria-label="Send voice message"
            className={`${round} bg-indigo-600 text-white hover:bg-indigo-700`}
          >
            {phase === 'uploading' ? <Spinner className="h-5 w-5" /> : <SendIcon className="h-5 w-5" />}
          </button>
        )}
      </div>
      {error && <p className="mt-2 text-sm text-red-600 dark:text-red-400" role="alert">{error}</p>}
    </div>
  );
}
