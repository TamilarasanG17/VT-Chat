import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { useWebRTC } from '../hooks/useWebRTC';
import { callService } from '../services/callService';
import { websocketService } from '../services/websocketService';
import { PHASE, callReducer, initialCallState, isLive } from '../utils/callState';
import { callServerErrorMessage, mediaErrorMessage } from '../utils/callErrors';
import { isCallError, newCallId } from '../utils/calls';
import { buildIceServers } from '../utils/iceServers';
import { startRingtone, stopRingtone } from '../utils/ringtone';
import { showError, toastError, toastInfo } from '../utils/alerts';

export const CallContext = createContext(null);

const RESET_DELAY_MS = 2200;
const SIGNAL_TIMEOUT_MS = 12_000;
const CONNECT_TIMEOUT_MS = 50_000;
const RECONNECT_GRACE_MS = 15_000;

const firstName = (peer) => peer?.username ?? 'The other person';

export function CallProvider({ children }) {
  const { user, isAuthenticated } = useAuth();
  const userRef = useRef(user);
  userRef.current = user;

  const [call, setCall] = useState(initialCallState);
  const callRef = useRef(call);
  const dispatch = useCallback((action) => {
    const next = callReducer(callRef.current, action);
    if (next !== callRef.current) {
      callRef.current = next;
      setCall(next);
    }
  }, []);

  const timers = useRef({});
  const outbox = useRef({ ready: false, queue: [] });
  const earlyIce = useRef([]);
  const pcReady = useRef(false);
  const offerSent = useRef(false);
  const abandoned = useRef(new Set());

  const clearTimers = () => {
    Object.values(timers.current).forEach(clearTimeout);
    timers.current = {};
  };

  const rtcRef = useRef(null);
  const rtc = useWebRTC({
    onIceCandidate: (candidate) => {
      const cur = callRef.current;
      if (!cur.callId || !isLive(cur.phase)) return;
      if (cur.direction === 'outgoing' && !outbox.current.ready) {
        outbox.current.queue.push(candidate);
        return;
      }
      callService.sendIce(cur.callId, candidate);
    },
    onConnectionState: (state) => handleConnectionState(state),
    onRemoteMedia: ({ muted, cameraOff }) => dispatch({ type: 'REMOTE_MEDIA', muted, cameraOff }),
  });
  rtcRef.current = rtc;

  const finishLocal = (phase, message = '', { duration, silent = false } = {}) => {
    const cur = callRef.current;
    if (!isLive(cur.phase)) return;
    clearTimers();
    stopRingtone();
    rtcRef.current.cleanup();
    pcReady.current = false;
    earlyIce.current = [];
    outbox.current = { ready: false, queue: [] };
    offerSent.current = false;

    if (silent) {
      dispatch({ type: 'RESET', callId: cur.callId });
      return;
    }
    const seconds = duration ?? (cur.connectedAt ? Math.round((Date.now() - cur.connectedAt) / 1000) : undefined);
    dispatch({ type: 'TERMINATE', callId: cur.callId, phase, message, duration: seconds });
    timers.current.reset = setTimeout(() => dispatch({ type: 'RESET', callId: cur.callId }), RESET_DELAY_MS);
  };

  const failCall = (message, { modal = false, notify = true } = {}) => {
    const cur = callRef.current;
    if (!isLive(cur.phase)) return;
    if (notify && cur.callId) {
      if (cur.direction === 'incoming' && cur.phase === PHASE.RINGING) callService.sendReject(cur.callId);
      else if (cur.direction === 'incoming' && cur.phase === PHASE.ACCEPTED && !pcReady.current) callService.sendReject(cur.callId);
      else if (cur.direction === 'outgoing' && !offerSent.current) {
      } else {
        if (cur.direction === 'outgoing' && cur.phase === PHASE.CALLING) abandoned.current.add(cur.callId);
        callService.sendEnd(cur.callId, 'FAILED');
      }
    }
    finishLocal(PHASE.FAILED, message, { silent: modal });
    if (modal) showError('Call could not start', message);
  };

  const handleConnectionState = (state) => {
    const cur = callRef.current;
    if (!isLive(cur.phase) || cur.phase === PHASE.CALLING || cur.phase === PHASE.RINGING) return;
    if (state === 'connected') {
      clearTimeout(timers.current.reconnect);
      clearTimeout(timers.current.connect);
      timers.current.reconnect = undefined;
      dispatch({ type: 'RECONNECTING', callId: cur.callId, value: false });
      if (cur.phase !== PHASE.CONNECTED) {
        stopRingtone();
        dispatch({ type: 'CONNECTED', callId: cur.callId, at: Date.now() });
        callService.sendConnected(cur.callId);
      }
    } else if (state === 'disconnected') {
      dispatch({ type: 'RECONNECTING', callId: cur.callId, value: true });
      if (!timers.current.reconnect) {
        timers.current.reconnect = setTimeout(() => failCall('The connection was lost.'), RECONNECT_GRACE_MS);
      }
    } else if (state === 'failed') {
      failCall('The connection failed. Check your network and try again.');
    }
  };

  const startCall = useCallback(async (conversationId, peer, callType) => {
    const cur = callRef.current;
    if (isLive(cur.phase)) {
      toastInfo('You are already in a call');
      return;
    }
    if (!websocketService.isConnected()) {
      toastError('You are offline. Reconnecting…');
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      showError('Calls are not supported here', mediaErrorMessage({ code: 'NO_MEDIA_SUPPORT' }));
      return;
    }

    clearTimers();
    const callId = newCallId();
    outbox.current = { ready: false, queue: [] };
    earlyIce.current = [];
    pcReady.current = false;
    offerSent.current = false;
    dispatch({ type: 'START_OUTGOING', callId, conversationId, callType, peer });
    const current = () => callRef.current.callId === callId && isLive(callRef.current.phase);

    let stage = 'setup';
    try {
      const ice = await callService.getIceServers().catch(() => null);
      if (!current()) return;
      rtcRef.current.initializePeerConnection(buildIceServers(ice));
      pcReady.current = true;
      stage = 'media';
      await rtcRef.current.startLocalMedia(callType);
      if (!current()) return;
      stage = 'offer';
      const sdp = await rtcRef.current.createOffer();
      if (!current()) return;
      if (!callService.sendOffer({ callId, conversationId, callType, sdp })) throw new Error('offline');
      offerSent.current = true;
      timers.current.signal = setTimeout(() => {
        if (callRef.current.callId === callId && callRef.current.phase === PHASE.CALLING) {
          failCall('Could not reach the server. Please try again.', { modal: true });
        }
      }, SIGNAL_TIMEOUT_MS);
    } catch (err) {
      if (err?.code === 'CALL_GONE' || !current()) return;
      failCall(stage === 'media' ? mediaErrorMessage(err, callType) : 'The call could not be started. Please try again.', { modal: true });
    }
  }, []);

  const acceptCall = useCallback(async () => {
    const cur = callRef.current;
    if (cur.direction !== 'incoming' || cur.phase !== PHASE.RINGING) return;
    const { callId, callType, offerSdp } = cur;
    stopRingtone();
    dispatch({ type: 'ACCEPT_LOCAL', callId });
    const current = () => callRef.current.callId === callId && isLive(callRef.current.phase);

    let stage = 'setup';
    try {
      const ice = await callService.getIceServers().catch(() => null);
      if (!current()) return;
      rtcRef.current.initializePeerConnection(buildIceServers(ice));
      stage = 'media';
      await rtcRef.current.startLocalMedia(callType);
      if (!current()) return;
      stage = 'answer';
      await rtcRef.current.handleOffer(offerSdp);
      const early = earlyIce.current;
      earlyIce.current = [];
      pcReady.current = true;
      early.forEach((c) => rtcRef.current.handleIceCandidate(c));
      const sdp = await rtcRef.current.createAnswer();
      if (!current()) return;
      if (!callService.sendAnswer({ callId, sdp })) throw new Error('offline');
      dispatch({ type: 'NEGOTIATING', callId });
      timers.current.connect = setTimeout(() => failCall('The call could not be connected.'), CONNECT_TIMEOUT_MS);
    } catch (err) {
      if (err?.code === 'CALL_GONE' || !current()) return;
      failCall(stage === 'media' ? mediaErrorMessage(err, callType) : 'The call could not be connected. Please try again.', { modal: true });
    }
  }, []);

  const hangUp = useCallback(() => {
    const cur = callRef.current;
    if (!isLive(cur.phase)) {
      clearTimeout(timers.current.reset);
      dispatch({ type: 'RESET' });
      return;
    }
    const { callId } = cur;
    if (cur.direction === 'outgoing' && (cur.phase === PHASE.CALLING || cur.phase === PHASE.RINGING)) {
      if (offerSent.current) {
        if (cur.phase === PHASE.CALLING) abandoned.current.add(callId);
        callService.sendCancel(callId);
      }
      finishLocal(PHASE.CANCELLED, '', { silent: true });
    } else if (cur.direction === 'incoming' && cur.phase === PHASE.RINGING) {
      callService.sendReject(callId);
      finishLocal(PHASE.REJECTED, '', { silent: true });
    } else {
      callService.sendEnd(callId);
      finishLocal(PHASE.ENDED, 'Call ended');
    }
  }, []);

  const toggleMute = useCallback(() => {
    if (!isLive(callRef.current.phase)) return;
    dispatch({ type: 'TOGGLE_MUTE', value: rtcRef.current.toggleMicrophone() });
  }, [dispatch]);

  const toggleCamera = useCallback(() => {
    if (!isLive(callRef.current.phase) || callRef.current.callType !== 'VIDEO') return;
    dispatch({ type: 'TOGGLE_CAMERA', value: rtcRef.current.toggleCamera() });
  }, [dispatch]);

  const toggleSpeaker = useCallback(() => {
    if (!isLive(callRef.current.phase)) return;
    const next = !callRef.current.speakerOff;
    rtcRef.current.setSpeakerMuted(next);
    dispatch({ type: 'TOGGLE_SPEAKER', value: next });
  }, [dispatch]);

  const switchCamera = useCallback(async () => {
    try {
      await rtcRef.current.switchCamera();
    } catch {
      toastError('Could not switch the camera');
    }
  }, []);

  const handleEvent = (e) => {
    const me = userRef.current?.id;
    if (!me || !e?.type) return;
    const cur = callRef.current;

    if (e.type === 'CALL_OFFER') {
      if (e.receiverId !== me || isLive(cur.phase) || !e.sdp) return;
      clearTimeout(timers.current.reset);
      dispatch({
        type: 'INCOMING',
        callId: e.callId,
        conversationId: e.conversationId,
        callType: e.callType,
        peer: e.caller ?? { id: e.callerId, username: 'Unknown', profilePictureUrl: null },
        offerSdp: e.sdp,
      });
      startRingtone('incoming');
      return;
    }

    if (e.type === 'CALL_RINGING' && abandoned.current.has(e.callId)) {
      abandoned.current.delete(e.callId);
      callService.sendCancel(e.callId);
      return;
    }
    if (!isLive(cur.phase) || e.callId !== cur.callId) return;

    const name = firstName(cur.peer);
    switch (e.type) {
      case 'CALL_RINGING':
        clearTimeout(timers.current.signal);
        dispatch({ type: 'SERVER_RINGING', callId: e.callId });
        outbox.current.ready = true;
        outbox.current.queue.splice(0).forEach((c) => callService.sendIce(e.callId, c));
        startRingtone('outgoing');
        break;

      case 'CALL_ANSWER':
        clearTimeout(timers.current.signal);
        stopRingtone();
        rtcRef.current
          .handleAnswer(e.sdp)
          .then(() => {
            if (callRef.current.callId !== e.callId) return;
            outbox.current.ready = true;
            outbox.current.queue.splice(0).forEach((c) => callService.sendIce(e.callId, c));
            dispatch({ type: 'ANSWER_RECEIVED', callId: e.callId });
            timers.current.connect = setTimeout(() => failCall('The call could not be connected.'), CONNECT_TIMEOUT_MS);
          })
          .catch(() => failCall('The call could not be connected. Please try again.'));
        break;

      case 'CALL_ICE':
        if (!pcReady.current) earlyIce.current.push(e.candidate);
        else rtcRef.current.handleIceCandidate(e.candidate);
        break;

      case 'CALL_REJECT':
        finishLocal(PHASE.REJECTED, 'Call rejected');
        break;
      case 'CALL_CANCEL':
        finishLocal(PHASE.CANCELLED, 'Call cancelled');
        break;
      case 'CALL_END':
        finishLocal(PHASE.ENDED, 'Call ended', { duration: e.duration ?? undefined });
        break;
      case 'CALL_BUSY':
        finishLocal(PHASE.BUSY, `${name} is on another call`);
        break;
      case 'CALL_MISSED':
        finishLocal(PHASE.MISSED, cur.direction === 'outgoing' ? `${name} didn't answer` : 'Missed call');
        break;
      case 'CALL_FAILED':
        finishLocal(PHASE.FAILED, 'The call could not be connected', { duration: e.duration ?? undefined });
        break;
      case 'CALL_HANDLED':
        if (cur.direction === 'incoming' && cur.phase === PHASE.RINGING) finishLocal(PHASE.CANCELLED, '', { silent: true });
        break;
      default:
        break;
    }
  };

  const handleServerError = (error) => {
    if (!isCallError(error)) return;
    const cur = callRef.current;
    if (!isLive(cur.phase)) return;
    toastError(callServerErrorMessage(error));
    finishLocal(PHASE.FAILED, '', { silent: true });
  };

  const handleReconnect = () => {
    const cur = callRef.current;
    if (!isLive(cur.phase) || !cur.callId) return;
    const { callId } = cur;
    callService
      .getCall(callId)
      .then((res) => {
        const status = res?.data?.status;
        if (callRef.current.callId !== callId || !isLive(callRef.current.phase)) return;
        if (status && !['RINGING', 'ACCEPTED', 'CONNECTED'].includes(status)) {
          const phase = status === 'COMPLETED' ? PHASE.ENDED : PHASE[status] ?? PHASE.ENDED;
          finishLocal(phase, phase === PHASE.ENDED ? 'Call ended' : 'The call is over', { duration: res.data.duration ?? undefined });
        }
      })
      .catch((err) => {
        if (callRef.current.callId !== callId) return;
        if (err?.response?.status === 404 || err?.response?.status === 403) finishLocal(PHASE.FAILED, 'The call ended while you were offline');
      });
  };

  const handlersRef = useRef({});
  handlersRef.current = { handleEvent, handleServerError, handleReconnect };

  useEffect(() => {
    if (!isAuthenticated) return undefined;
    websocketService.retain();
    const offs = [
      websocketService.onCallEvent((e) => handlersRef.current.handleEvent(e)),
      websocketService.onError((e) => handlersRef.current.handleServerError(e)),
      websocketService.onReconnect(() => handlersRef.current.handleReconnect()),
    ];
    return () => {
      offs.forEach((off) => off());
      websocketService.release();
    };
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) {
      clearTimers();
      stopRingtone();
      rtcRef.current?.cleanup();
      pcReady.current = false;
      callRef.current = initialCallState;
      setCall(initialCallState);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    const leave = () => {
      if (isLive(callRef.current.phase)) hangUp();
      stopRingtone();
      rtcRef.current?.cleanup();
    };
    window.addEventListener('pagehide', leave);
    window.addEventListener('beforeunload', leave);
    return () => {
      window.removeEventListener('pagehide', leave);
      window.removeEventListener('beforeunload', leave);
      leave();
      clearTimers();
    };
  }, [hangUp]);

  const value = useMemo(
    () => ({
      call,
      inCall: isLive(call.phase),
      canSwitchCamera: rtc.canSwitchCamera,
      startCall,
      acceptCall,
      rejectCall: hangUp,
      hangUp,
      toggleMute,
      toggleCamera,
      toggleSpeaker,
      switchCamera,
      registerLocalVideo: rtc.registerLocalVideo,
      registerRemoteVideo: rtc.registerRemoteVideo,
      registerRemoteAudio: rtc.registerRemoteAudio,
    }),
    [call, rtc.canSwitchCamera, startCall, acceptCall, hangUp, toggleMute, toggleCamera, toggleSpeaker, switchCamera],
  );

  return <CallContext.Provider value={value}>{children}</CallContext.Provider>;
}
