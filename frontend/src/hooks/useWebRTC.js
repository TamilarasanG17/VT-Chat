import { useEffect, useMemo, useRef, useState } from 'react';

const AUDIO_CONSTRAINTS = { echoCancellation: true, noiseSuppression: true, autoGainControl: true };
const VIDEO_CONSTRAINTS = { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } };

function noMediaSupport() {
  const err = new Error('Media devices are not available');
  err.code = 'NO_MEDIA_SUPPORT';
  return err;
}

export function useWebRTC(handlers) {
  const handlersRef = useRef(handlers);
  useEffect(() => {
    handlersRef.current = handlers;
  });

  const [canSwitchCamera, setCanSwitchCamera] = useState(false);

  const api = useMemo(() => {
    const r = {
      pc: null,
      local: null,
      remote: null,
      channel: null,
      pending: [],
      generation: 0,
      localEl: null,
      remoteVideoEl: null,
      remoteAudioEl: null,
    };

    const attach = (el, stream) => {
      if (el && el.srcObject !== stream) el.srcObject = stream;
    };
    const applyStreams = () => {
      attach(r.localEl, r.local);
      attach(r.remoteVideoEl, r.remote);
      attach(r.remoteAudioEl, r.remote);
      [r.remoteVideoEl, r.remoteAudioEl].forEach((el) => el?.play?.().catch(() => {}));
    };

    const trackEnabled = (kind) => {
      const tracks = r.local?.getTracks().filter((t) => t.kind === kind) ?? [];
      return tracks.length > 0 && tracks.every((t) => t.enabled);
    };

    const sendMediaState = () => {
      if (r.channel?.readyState !== 'open') return;
      try {
        r.channel.send(JSON.stringify({ t: 'media', muted: !trackEnabled('audio'), cameraOff: r.local?.getVideoTracks().length ? !trackEnabled('video') : false }));
      } catch {
      }
    };

    const flushPending = async () => {
      const queued = r.pending;
      r.pending = [];
      for (const c of queued) {
        try {
          await r.pc?.addIceCandidate(c);
        } catch {
        }
      }
    };

    const requirePc = () => {
      if (!r.pc) throw new Error('No peer connection');
      return r.pc;
    };

    const cleanup = () => {
      r.generation += 1;
      r.pending = [];
      if (r.channel) {
        r.channel.onopen = null;
        r.channel.onmessage = null;
        try {
          r.channel.close();
        } catch {
        }
        r.channel = null;
      }
      if (r.pc) {
        const pc = r.pc;
        r.pc = null;
        pc.onicecandidate = null;
        pc.ontrack = null;
        pc.onconnectionstatechange = null;
        pc.oniceconnectionstatechange = null;
        try {
          pc.getSenders().forEach((s) => s.track?.stop());
          pc.close();
        } catch {
        }
      }
      r.local?.getTracks().forEach((t) => t.stop());
      r.remote?.getTracks().forEach((t) => t.stop());
      r.local = null;
      r.remote = null;
      [r.localEl, r.remoteVideoEl, r.remoteAudioEl].forEach((el) => {
        if (el) el.srcObject = null;
      });
      setCanSwitchCamera(false);
    };

    return {
      initializePeerConnection(iceServers) {
        cleanup();
        const pc = new RTCPeerConnection({ iceServers, bundlePolicy: 'max-bundle' });
        r.pc = pc;

        pc.onicecandidate = (e) => {
          if (pc === r.pc && e.candidate) handlersRef.current.onIceCandidate?.(e.candidate.toJSON());
        };
        pc.ontrack = (e) => {
          if (pc !== r.pc) return;
          if (e.streams?.[0]) r.remote = e.streams[0];
          else {
            r.remote ??= new MediaStream();
            r.remote.addTrack(e.track);
          }
          applyStreams();
        };
        const report = () => {
          if (pc !== r.pc) return;
          const state = pc.connectionState ?? { connected: 'connected', completed: 'connected', disconnected: 'disconnected', failed: 'failed', closed: 'closed' }[pc.iceConnectionState] ?? 'connecting';
          handlersRef.current.onConnectionState?.(state);
        };
        pc.onconnectionstatechange = report;
        pc.oniceconnectionstatechange = report;

        const channel = pc.createDataChannel('vt-state', { negotiated: true, id: 0 });
        r.channel = channel;
        channel.onopen = sendMediaState;
        channel.onmessage = (e) => {
          try {
            const m = JSON.parse(e.data);
            if (m?.t === 'media') handlersRef.current.onRemoteMedia?.({ muted: !!m.muted, cameraOff: !!m.cameraOff });
          } catch {
          }
        };
        return pc;
      },

      async startLocalMedia(callType) {
        if (!navigator.mediaDevices?.getUserMedia) throw noMediaSupport();
        const generation = r.generation;
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: AUDIO_CONSTRAINTS,
          video: callType === 'VIDEO' ? VIDEO_CONSTRAINTS : false,
        });
        if (generation !== r.generation) {
          stream.getTracks().forEach((t) => t.stop());
          throw Object.assign(new Error('Call ended'), { code: 'CALL_GONE' });
        }
        r.local = stream;
        if (r.pc) stream.getTracks().forEach((t) => r.pc.addTrack(t, stream));
        applyStreams();
        if (callType === 'VIDEO') {
          try {
            const devices = await navigator.mediaDevices.enumerateDevices();
            if (generation === r.generation) setCanSwitchCamera(devices.filter((d) => d.kind === 'videoinput').length > 1);
          } catch {
          }
        }
      },

      async createOffer() {
        const pc = requirePc();
        await pc.setLocalDescription(await pc.createOffer());
        return pc.localDescription.sdp;
      },

      async handleOffer(sdp) {
        const pc = requirePc();
        await pc.setRemoteDescription({ type: 'offer', sdp });
        await flushPending();
      },

      async createAnswer() {
        const pc = requirePc();
        await pc.setLocalDescription(await pc.createAnswer());
        return pc.localDescription.sdp;
      },

      async handleAnswer(sdp) {
        const pc = requirePc();
        await pc.setRemoteDescription({ type: 'answer', sdp });
        await flushPending();
      },

      async handleIceCandidate(candidate) {
        if (!candidate?.candidate) return;
        const init = {
          candidate: candidate.candidate,
          sdpMid: candidate.sdpMid ?? null,
          sdpMLineIndex: candidate.sdpMLineIndex ?? null,
          usernameFragment: candidate.usernameFragment ?? undefined,
        };
        if (!r.pc || !r.pc.remoteDescription) {
          r.pending.push(init);
          return;
        }
        try {
          await r.pc.addIceCandidate(init);
        } catch {
        }
      },

      toggleMicrophone() {
        const tracks = r.local?.getAudioTracks() ?? [];
        const mute = trackEnabled('audio');
        tracks.forEach((t) => {
          t.enabled = !mute;
        });
        sendMediaState();
        return mute;
      },

      toggleCamera() {
        const tracks = r.local?.getVideoTracks() ?? [];
        const off = trackEnabled('video');
        tracks.forEach((t) => {
          t.enabled = !off;
        });
        sendMediaState();
        return off;
      },

      setSpeakerMuted(muted) {
        if (r.remoteAudioEl) r.remoteAudioEl.muted = muted;
      },

      async switchCamera() {
        const current = r.local?.getVideoTracks()[0];
        const sender = r.pc?.getSenders().find((s) => s.track?.kind === 'video');
        if (!current || !sender) return false;
        const generation = r.generation;
        const cameras = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === 'videoinput');
        if (cameras.length < 2) return false;
        const currentId = current.getSettings().deviceId;
        const next = cameras[(cameras.findIndex((d) => d.deviceId === currentId) + 1) % cameras.length];
        const fresh = await navigator.mediaDevices.getUserMedia({ video: { ...VIDEO_CONSTRAINTS, facingMode: undefined, deviceId: { exact: next.deviceId } } });
        const track = fresh.getVideoTracks()[0];
        if (generation !== r.generation) {
          track.stop();
          return false;
        }
        track.enabled = current.enabled;
        await sender.replaceTrack(track);
        r.local.removeTrack(current);
        current.stop();
        r.local.addTrack(track);
        applyStreams();
        return true;
      },

      registerLocalVideo(el) {
        r.localEl = el;
        applyStreams();
      },
      registerRemoteVideo(el) {
        r.remoteVideoEl = el;
        applyStreams();
      },
      registerRemoteAudio(el) {
        r.remoteAudioEl = el;
        applyStreams();
      },

      cleanup,
      endCall: cleanup,
    };
  }, []);

  useEffect(() => () => api.cleanup(), [api]);

  return { ...api, canSwitchCamera };
}
