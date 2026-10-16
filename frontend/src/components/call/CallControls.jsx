import { HangUpIcon, MicIcon, MicOffIcon, SpeakerIcon, SpeakerOffIcon, SwitchCameraIcon, VideoIcon, VideoOffIcon } from '../Icons.jsx';

const base =
  'inline-flex h-12 w-12 min-[360px]:h-14 min-[360px]:w-14 items-center justify-center rounded-full transition-[background-color,transform] duration-150 active:scale-90 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/40 disabled:opacity-50';

function RoundButton({ label, active = false, onClick, children, danger = false }) {
  const tone = danger
    ? 'bg-red-600 text-white hover:bg-red-500'
    : active
      ? 'bg-white text-slate-900 hover:bg-slate-200'
      : 'bg-white/15 text-white hover:bg-white/25';
  return (
    <button type="button" onClick={onClick} aria-label={label} title={label} aria-pressed={danger ? undefined : active} className={`${base} ${tone}`}>
      {children}
    </button>
  );
}

export default function CallControls({ call, canSwitchCamera, onMute, onCamera, onSwitchCamera, onSpeaker, onHangUp }) {
  const video = call.callType === 'VIDEO';
  return (
    <div className="flex flex-wrap items-center justify-center gap-3" role="group" aria-label="Call controls">
      <RoundButton label={call.muted ? 'Unmute microphone' : 'Mute microphone'} active={call.muted} onClick={onMute}>
        {call.muted ? <MicOffIcon className="h-6 w-6" /> : <MicIcon className="h-6 w-6" />}
      </RoundButton>
      {video && (
        <RoundButton label={call.cameraOff ? 'Turn camera on' : 'Turn camera off'} active={call.cameraOff} onClick={onCamera}>
          {call.cameraOff ? <VideoOffIcon className="h-6 w-6" /> : <VideoIcon className="h-6 w-6" />}
        </RoundButton>
      )}
      {video && canSwitchCamera && (
        <RoundButton label="Switch camera" onClick={onSwitchCamera}>
          <SwitchCameraIcon className="h-6 w-6" />
        </RoundButton>
      )}
      <RoundButton label={call.speakerOff ? 'Turn speaker on' : 'Turn speaker off'} active={call.speakerOff} onClick={onSpeaker}>
        {call.speakerOff ? <SpeakerOffIcon className="h-6 w-6" /> : <SpeakerIcon className="h-6 w-6" />}
      </RoundButton>
      <RoundButton label="End call" danger onClick={onHangUp}>
        <HangUpIcon className="h-6 w-6 rotate-[135deg]" />
      </RoundButton>
    </div>
  );
}
