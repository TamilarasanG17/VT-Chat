import CallControls from './CallControls.jsx';
import CallStatus from './CallStatus.jsx';
import LocalVideo from './LocalVideo.jsx';
import RemoteVideo from './RemoteVideo.jsx';

export default function VideoCall({ call, controls }) {
  const connected = call.phase === 'CONNECTED';
  return (
    <div className="relative h-full w-full text-white">
      <RemoteVideo peer={call.peer} cameraOff={call.remoteCameraOff} connected={connected} />

      <div className="absolute inset-x-0 top-0 bg-gradient-to-b from-black/60 to-transparent px-4 pb-8 pt-5 text-center">
        <h2 className="truncate text-lg font-semibold">{call.peer?.username}</h2>
        <CallStatus call={call} className="text-slate-200" />
      </div>

      <LocalVideo cameraOff={call.cameraOff} className="absolute right-3 top-20 h-28 w-20 min-[360px]:h-36 min-[360px]:w-24 shadow-lg ring-1 ring-white/20 sm:h-44 sm:w-32" />

      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-3 pb-8 pt-12">
        <CallControls call={call} {...controls} />
      </div>
    </div>
  );
}
