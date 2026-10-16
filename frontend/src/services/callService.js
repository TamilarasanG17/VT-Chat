import api from './api';
import { websocketService } from './websocketService';

const unwrap = (promise) => promise.then((res) => res.data);

const send = (destination, payload) => websocketService.publish(destination, payload);

const toCandidate = (c) => ({
  candidate: c.candidate,
  sdpMid: c.sdpMid ?? null,
  sdpMLineIndex: c.sdpMLineIndex ?? null,
  usernameFragment: c.usernameFragment ?? null,
});

export const callService = {
  sendOffer: ({ callId, conversationId, callType, sdp }) => send('/app/call.offer', { callId, conversationId, callType, sdp }),
  sendAnswer: ({ callId, sdp }) => send('/app/call.answer', { callId, sdp }),
  sendIce: (callId, candidate) => send('/app/call.ice', { callId, candidate: toCandidate(candidate) }),
  sendReject: (callId) => send('/app/call.reject', { callId }),
  sendCancel: (callId) => send('/app/call.cancel', { callId }),
  sendEnd: (callId, reason) => send('/app/call.end', { callId, reason: reason ?? null }),
  sendConnected: (callId) => send('/app/call.connected', { callId }),

  getIceServers: () => unwrap(api.get('/calls/ice-servers')),
  getCall: (callId) => unwrap(api.get(`/calls/${callId}`)),
  getConversationCalls: (conversationId, page = 0, size = 20) =>
    unwrap(api.get(`/conversations/${conversationId}/calls`, { params: { page, size } })),
  getMyCalls: (page = 0, size = 20) => unwrap(api.get('/calls', { params: { page, size } })),
};
