import { useContext } from 'react';
import { PhoneIcon, VideoIcon } from '../Icons.jsx';
import { ChatPeerContext } from '../../context/ChatPeerContext';
import { useCall } from '../../hooks/useCall';
import { describeCall } from '../../utils/calls';

export default function CallMessage({ message, currentUserId, disabled = false }) {
  const d = describeCall(message, currentUserId);
  const peer = useContext(ChatPeerContext);
  const { startCall, inCall } = useCall();
  const line = [d.direction, d.duration || d.statusLabel].filter(Boolean).join(' · ');
  const canCallBack = d.canCallBack && peer && peer.conversationId === message.conversationId;

  return (
    <div className="flex min-w-[10rem] flex-col gap-1.5">
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className={`grid h-9 w-9 shrink-0 place-items-center rounded-full text-lg ${d.missed ? 'bg-red-100 dark:bg-red-500/20' : 'bg-black/10 dark:bg-white/10'}`}
        >
          {d.icon}
        </span>
        <div className="min-w-0">
          <p className={`font-semibold leading-tight ${d.missed ? 'text-red-600 dark:text-red-400' : ''}`}>{d.title}</p>
          <p className="text-sm opacity-80">{line}</p>
        </div>
      </div>
      {canCallBack && (
        <button
          type="button"
          disabled={disabled || inCall}
          onClick={() => startCall(peer.conversationId, peer.user, d.video ? 'VIDEO' : 'VOICE')}
          className="inline-flex items-center justify-center gap-2 rounded-lg border border-current/30 px-3 py-1.5 text-sm font-semibold transition hover:bg-black/5 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-white/10"
        >
          {d.video ? <VideoIcon className="h-4 w-4" /> : <PhoneIcon className="h-4 w-4" />}
          {d.video ? 'Video call' : 'Call back'}
        </button>
      )}
    </div>
  );
}
