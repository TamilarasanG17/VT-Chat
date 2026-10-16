import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'framer-motion';
import Avatar from '../profile/Avatar.jsx';
import CallStatus from './CallStatus.jsx';
import IncomingCall from './IncomingCall.jsx';
import OutgoingCall from './OutgoingCall.jsx';
import VideoCall from './VideoCall.jsx';
import VoiceCall from './VoiceCall.jsx';
import { useCall } from '../../hooks/useCall';
import { PHASE, isTerminal } from '../../utils/callState';

export default function CallOverlay() {
  const ctx = useCall();
  const { call, canSwitchCamera } = ctx;
  if (call.phase === PHASE.IDLE) return null;

  const incomingRinging = call.direction === 'incoming' && call.phase === PHASE.RINGING;
  const outgoingWaiting = call.direction === 'outgoing' && (call.phase === PHASE.CALLING || call.phase === PHASE.RINGING);
  const controls = {
    canSwitchCamera,
    onMute: ctx.toggleMute,
    onCamera: ctx.toggleCamera,
    onSwitchCamera: ctx.switchCamera,
    onSpeaker: ctx.toggleSpeaker,
    onHangUp: ctx.hangUp,
  };

  let screen;
  if (isTerminal(call.phase)) {
    screen = (
      <div className="flex h-full flex-col items-center justify-center gap-4 px-6 text-center text-white">
        <Avatar src={call.peer?.profilePictureUrl} name={call.peer?.username} size="lg" />
        <h2 className="max-w-full truncate text-2xl font-semibold">{call.peer?.username}</h2>
        <CallStatus call={call} className="text-slate-300" />
      </div>
    );
  } else if (incomingRinging) {
    screen = <IncomingCall call={call} onAccept={ctx.acceptCall} onDecline={ctx.rejectCall} />;
  } else if (outgoingWaiting) {
    screen = <OutgoingCall call={call} onCancel={ctx.hangUp} />;
  } else if (call.callType === 'VIDEO') {
    screen = <VideoCall call={call} controls={controls} />;
  } else {
    screen = <VoiceCall call={call} controls={controls} />;
  }

  return createPortal(
    <AnimatePresence>
      <motion.div
        key="call-overlay"
        role="dialog"
        aria-modal="true"
        aria-label={`${call.callType === 'VIDEO' ? 'Video' : 'Voice'} call with ${call.peer?.username ?? 'contact'}`}
        initial={{ opacity: 0, scale: 0.985 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.2 }}
        className="pt-safe pb-safe fixed inset-0 z-[100] overflow-y-auto bg-slate-950"
      >
        {screen}
        <audio ref={ctx.registerRemoteAudio} autoPlay />
      </motion.div>
    </AnimatePresence>,
    document.body,
  );
}
