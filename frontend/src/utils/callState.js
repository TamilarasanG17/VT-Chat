export const PHASE = {
  IDLE: 'IDLE',
  CALLING: 'CALLING',
  RINGING: 'RINGING',
  ACCEPTED: 'ACCEPTED',
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  ENDED: 'ENDED',
  REJECTED: 'REJECTED',
  MISSED: 'MISSED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  BUSY: 'BUSY',
};

const TERMINAL = new Set(['ENDED', 'REJECTED', 'MISSED', 'FAILED', 'CANCELLED', 'BUSY']);
const ORDER = ['CALLING', 'RINGING', 'ACCEPTED', 'CONNECTING', 'CONNECTED'];

export const isTerminal = (phase) => TERMINAL.has(phase);
export const isLive = (phase) => ORDER.includes(phase);

export const initialCallState = {
  phase: PHASE.IDLE,
  direction: null,
  callId: null,
  conversationId: null,
  callType: null,
  peer: null,
  offerSdp: null,
  connectedAt: null,
  duration: null,
  muted: false,
  cameraOff: false,
  speakerOff: false,
  remoteMuted: false,
  remoteCameraOff: false,
  reconnecting: false,
  message: '',
};

const keepMedia = (s) => ({ muted: s.muted, cameraOff: s.cameraOff, speakerOff: s.speakerOff });

export function callReducer(state, action) {
  const sameCall = action.callId === undefined || action.callId === state.callId;

  switch (action.type) {
    case 'START_OUTGOING':
      if (isLive(state.phase)) return state;
      return {
        ...initialCallState,
        phase: PHASE.CALLING,
        direction: 'outgoing',
        callId: action.callId,
        conversationId: action.conversationId,
        callType: action.callType,
        peer: action.peer,
      };

    case 'INCOMING':
      if (isLive(state.phase)) return state;
      return {
        ...initialCallState,
        phase: PHASE.RINGING,
        direction: 'incoming',
        callId: action.callId,
        conversationId: action.conversationId,
        callType: action.callType,
        peer: action.peer,
        offerSdp: action.offerSdp,
      };

    case 'SERVER_RINGING':
      if (!sameCall || state.phase !== PHASE.CALLING) return state;
      return { ...state, phase: PHASE.RINGING };

    case 'ACCEPT_LOCAL':
      if (!sameCall || state.direction !== 'incoming' || state.phase !== PHASE.RINGING) return state;
      return { ...state, phase: PHASE.ACCEPTED, offerSdp: null };

    case 'ANSWER_RECEIVED':
      if (!sameCall || state.direction !== 'outgoing' || ORDER.indexOf(state.phase) > ORDER.indexOf(PHASE.ACCEPTED)) return state;
      return { ...state, phase: PHASE.CONNECTING };

    case 'NEGOTIATING':
      if (!sameCall || state.phase !== PHASE.ACCEPTED) return state;
      return { ...state, phase: PHASE.CONNECTING };

    case 'CONNECTED':
      if (!sameCall || !isLive(state.phase) || state.phase === PHASE.CALLING || state.phase === PHASE.RINGING) return state;
      return { ...state, phase: PHASE.CONNECTED, connectedAt: state.connectedAt ?? action.at, reconnecting: false };

    case 'RECONNECTING':
      if (!sameCall || !isLive(state.phase)) return state;
      return { ...state, reconnecting: action.value };

    case 'TERMINATE':
      if (!sameCall || !isLive(state.phase)) return state;
      return {
        ...state,
        phase: action.phase,
        message: action.message ?? '',
        duration: action.duration ?? state.duration,
        reconnecting: false,
        offerSdp: null,
      };

    case 'RESET':
      if (action.callId !== undefined && action.callId !== state.callId) return state;
      return initialCallState;

    case 'TOGGLE_MUTE':
      return isLive(state.phase) ? { ...state, muted: action.value ?? !state.muted } : state;
    case 'TOGGLE_CAMERA':
      return isLive(state.phase) ? { ...state, cameraOff: action.value ?? !state.cameraOff } : state;
    case 'TOGGLE_SPEAKER':
      return isLive(state.phase) ? { ...state, speakerOff: action.value ?? !state.speakerOff } : state;
    case 'REMOTE_MEDIA':
      if (!isLive(state.phase)) return state;
      return { ...state, remoteMuted: !!action.muted, remoteCameraOff: !!action.cameraOff };

    default:
      return state;
  }
}

export { keepMedia };
