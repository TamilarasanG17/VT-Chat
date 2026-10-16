import Avatar from '../profile/Avatar.jsx';
import CallStatus from './CallStatus.jsx';
import LocalVideo from './LocalVideo.jsx';
import { HangUpIcon } from '../Icons.jsx';

export default function OutgoingCall({ call, onCancel }) {
  const video = call.callType === 'VIDEO';
  return (
    <div className="flex h-full flex-col items-center justify-between px-6 py-10 text-white sm:py-16">
      <div className="flex flex-col items-center gap-4 text-center">
        <Avatar src={call.peer?.profilePictureUrl} name={call.peer?.username} size="lg" />
        <h2 className="max-w-full truncate text-xl font-semibold min-[360px]:text-2xl">Calling {call.peer?.username}...</h2>
        <CallStatus call={call} className="text-slate-300" />
        {video && <LocalVideo cameraOff={call.cameraOff} className="mt-2 h-44 w-32 sm:h-56 sm:w-40" />}
      </div>
      <div className="flex flex-col items-center gap-2">
        <button
          type="button"
          onClick={onCancel}
          autoFocus
          aria-label="Cancel call"
          className="inline-flex h-16 w-16 items-center justify-center rounded-full bg-red-600 transition-[background-color,transform] active:scale-95 hover:bg-red-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/40"
        >
          <HangUpIcon className="h-7 w-7 rotate-[135deg]" />
        </button>
        <span className="text-sm text-slate-300">Cancel</span>
      </div>
    </div>
  );
}
