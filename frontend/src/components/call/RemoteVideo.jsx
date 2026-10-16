import Avatar from '../profile/Avatar.jsx';
import { useCall } from '../../hooks/useCall';

export default function RemoteVideo({ peer, cameraOff = false, connected = false }) {
  const { registerRemoteVideo } = useCall();
  return (
    <div className="absolute inset-0 bg-slate-900">
      <video
        ref={registerRemoteVideo}
        autoPlay
        playsInline
        muted
        aria-label={`${peer?.username ?? 'Other person'}'s video`}
        className={`h-full w-full object-cover ${cameraOff || !connected ? 'invisible' : ''}`}
      />
      {(cameraOff || !connected) && (
        <div className="absolute inset-0 grid place-items-center">
          <Avatar src={peer?.profilePictureUrl} name={peer?.username} size="lg" />
        </div>
      )}
    </div>
  );
}
