import { useEffect, useState } from 'react';
import { PHASE } from '../../utils/callState';

const clock = (s) => {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
};

function useElapsed(since) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!since) return undefined;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [since]);
  return since ? Math.max(0, Math.floor((now - since) / 1000)) : null;
}

export function statusText(call) {
  switch (call.phase) {
    case PHASE.CALLING:
      return 'Calling…';
    case PHASE.RINGING:
      return call.direction === 'incoming' ? `${call.peer?.username ?? 'Someone'} is calling...` : 'Ringing…';
    case PHASE.ACCEPTED:
    case PHASE.CONNECTING:
      return 'Connecting…';
    case PHASE.CONNECTED:
      return call.reconnecting ? 'Reconnecting…' : '';
    default:
      return call.message || 'Call ended';
  }
}

export default function CallStatus({ call, className = '' }) {
  const elapsed = useElapsed(call.phase === PHASE.CONNECTED ? call.connectedAt : null);
  const text = statusText(call);
  const showClock = call.phase === PHASE.CONNECTED && elapsed !== null;
  const ended = call.phase === PHASE.ENDED && call.duration > 0;

  return (
    <p role="status" aria-live="polite" className={`text-sm font-medium ${className}`}>
      {showClock && <span className="tabular-nums">{clock(elapsed)}</span>}
      {showClock && text && <span aria-hidden="true"> · </span>}
      {text}
      {ended && <span className="tabular-nums"> · {clock(call.duration)}</span>}
    </p>
  );
}
