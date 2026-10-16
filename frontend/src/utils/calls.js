const clock = (seconds) => {
  const s = Math.max(0, Math.round(Number(seconds) || 0));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rest = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${rest}` : `${m}:${rest}`;
};

export const isCallType = (type) => type === 'VOICE_CALL' || type === 'VIDEO_CALL';

const STATUS_LABEL = {
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
  RINGING: 'Ringing',
  CONNECTED: 'Ongoing',
};

function describe({ video, outgoing, status, duration }) {
  const kind = video ? 'Video call' : 'Voice call';
  const seconds = duration ?? 0;
  const missed = !outgoing && (status === 'MISSED' || status === 'CANCELLED');
  const statusLabel = status === 'MISSED' ? (outgoing ? 'No answer' : 'Missed') : STATUS_LABEL[status] ?? '';
  return {
    icon: video ? '🎥' : '📞',
    title: missed ? `Missed ${kind.toLowerCase()}` : kind,
    direction: outgoing ? 'Outgoing' : 'Incoming',
    statusLabel,
    duration: status === 'COMPLETED' && seconds > 0 ? clock(seconds) : '',
    missed,
    video: !!video,
    canCallBack: status !== 'COMPLETED' && status !== 'RINGING' && status !== 'CONNECTED',
  };
}

export function describeCall(message, viewerId) {
  return describe({
    video: message?.messageType === 'VIDEO_CALL',
    outgoing: message?.senderId === viewerId,
    status: message?.callStatus,
    duration: message?.duration,
  });
}

export function describeCallItem(item) {
  return describe({ video: item?.callType === 'VIDEO', outgoing: item?.direction === 'OUTGOING', status: item?.status, duration: item?.duration });
}

export function callSummary(message, viewerId) {
  const d = describeCall(message, viewerId);
  const tail = d.duration || (d.missed ? '' : d.statusLabel);
  return `${d.icon} ${d.title}${tail ? ` · ${tail}` : ''}`;
}

export function formatCallTime(iso, now = new Date()) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const time = d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const day = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((day(now) - day(d)) / 86_400_000);
  if (diff === 0) return `Today, ${time}`;
  if (diff === 1) return `Yesterday, ${time}`;
  return `${d.toLocaleDateString([], { day: 'numeric', month: 'short', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' })}, ${time}`;
}

const CALL_ERROR_CODES = new Set(['ALREADY_IN_CALL', 'CALL_EXISTS', 'CALL_NOT_FOUND', 'CALL_NOT_RINGING', 'INVALID_CALL', 'INVALID_CALL_TYPE', 'CALL_UNAVAILABLE']);
export const isCallError = (error) => CALL_ERROR_CODES.has(error?.code);

export function newCallId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID().toLowerCase();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
