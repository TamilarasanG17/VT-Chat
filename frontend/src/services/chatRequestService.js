import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const chatRequestService = {
  send: (receiverId) => unwrap(api.post(`/chat-requests/${receiverId}`)),
  received: () => unwrap(api.get('/chat-requests/received')),
  sent: () => unwrap(api.get('/chat-requests/sent')),
  receivedCount: () => unwrap(api.get('/chat-requests/received/count')),
  status: (userId) => unwrap(api.get(`/chat-requests/status/${userId}`)),
  accept: (requestId) => unwrap(api.put(`/chat-requests/${requestId}/accept`)),
  decline: (requestId) => unwrap(api.put(`/chat-requests/${requestId}/decline`)),
  unfriend: (userId) => unwrap(api.delete(`/friends/${userId}`)),
};

export const REL = {
  NONE: 'NONE',
  OUTGOING: 'OUTGOING_PENDING',
  INCOMING: 'INCOMING_PENDING',
  CONNECTED: 'CONNECTED',
  DECLINED: 'DECLINED',
  ENDED: 'ENDED',
};

export const CHAT_REQUEST_EVENTS = ['CHAT_REQUEST_RECEIVED', 'CHAT_REQUEST_ACCEPTED', 'CHAT_REQUEST_DECLINED', 'FRIEND_REMOVED'];
