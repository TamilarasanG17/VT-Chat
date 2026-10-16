import Avatar from '../profile/Avatar.jsx';
import CallControls from './CallControls.jsx';
import CallStatus from './CallStatus.jsx';
import { MicOffIcon } from '../Icons.jsx';

export default function VoiceCall({ call, controls }) {
  return (
    <div className="flex h-full flex-col items-center justify-between px-6 py-10 text-white sm:py-16">
      <div className="flex flex-col items-center gap-4 text-center">
        <Avatar src={call.peer?.profilePictureUrl} name={call.peer?.username} size="lg" />
        <h2 className="max-w-full truncate text-xl font-semibold min-[360px]:text-2xl">{call.peer?.username}</h2>
        <CallStatus call={call} className="text-slate-300" />
        {call.remoteMuted && call.phase === 'CONNECTED' && (
          <p className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs text-slate-200">
            <MicOffIcon className="h-4 w-4" /> {call.peer?.username} is muted
          </p>
        )}
      </div>
      <CallControls call={call} {...controls} />
    </div>
  );
}
