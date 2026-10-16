import Avatar from '../profile/Avatar.jsx';
import CallStatus from './CallStatus.jsx';
import { HangUpIcon, PhoneIcon, VideoIcon } from '../Icons.jsx';

export default function IncomingCall({ call, onAccept, onDecline }) {
  const video = call.callType === 'VIDEO';
  return (
    <div className="flex h-full flex-col items-center justify-between px-6 py-10 text-white sm:py-16">
      <div className="flex flex-col items-center gap-4 text-center">
        <div className="relative">
          <span aria-hidden="true" className="absolute inset-0 animate-ping rounded-full bg-white/20 [animation-duration:2s]" />
          <Avatar src={call.peer?.profilePictureUrl} name={call.peer?.username} size="lg" className="relative" />
        </div>
        <h2 className="max-w-full text-xl font-semibold min-[360px]:text-2xl">Incoming {video ? 'Video' : 'Voice'} Call</h2>
        <p className="max-w-full truncate text-lg text-slate-200">{call.peer?.username}</p>
        <CallStatus call={call} className="text-slate-300" />
      </div>

      <div className="flex items-center gap-12 min-[360px]:gap-16">
        <div className="flex flex-col items-center gap-2">
          <button
            type="button"
            onClick={onDecline}
            aria-label="Reject call"
            className="inline-flex h-16 w-16 items-center justify-center rounded-full bg-red-600 transition-[background-color,transform] active:scale-95 hover:bg-red-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/40"
          >
            <HangUpIcon className="h-7 w-7 rotate-[135deg]" />
          </button>
          <span className="text-sm text-slate-300">Reject</span>
        </div>
        <div className="flex flex-col items-center gap-2">
          <button
            type="button"
            onClick={onAccept}
            autoFocus
            aria-label={video ? 'Accept video call' : 'Accept call'}
            className="inline-flex h-16 w-16 items-center justify-center rounded-full bg-emerald-600 transition-[background-color,transform] active:scale-95 hover:bg-emerald-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/40"
          >
            {video ? <VideoIcon className="h-7 w-7" /> : <PhoneIcon className="h-7 w-7" />}
          </button>
          <span className="text-sm text-slate-300">Accept</span>
        </div>
      </div>
    </div>
  );
}
