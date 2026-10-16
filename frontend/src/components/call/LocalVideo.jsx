import { useCall } from '../../hooks/useCall';

export default function LocalVideo({ cameraOff = false, className = '' }) {
  const { registerLocalVideo } = useCall();
  return (
    <div className={`relative overflow-hidden rounded-xl bg-slate-800 ${className}`}>
      <video
        ref={registerLocalVideo}
        autoPlay
        playsInline
        muted
        aria-label="Your camera"
        className={`h-full w-full -scale-x-100 object-cover ${cameraOff ? 'invisible' : ''}`}
      />
      {cameraOff && <span className="absolute inset-0 grid place-items-center text-xs font-medium text-slate-300">Camera off</span>}
    </div>
  );
}
