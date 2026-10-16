import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const conversationService = {
  getConversations: () => unwrap(api.get('/conversations')),

  getMessages: (conversationId, aroundMessageId) =>
    unwrap(
      api.get(`/conversations/${conversationId}/messages`, {
        params: aroundMessageId ? { aroundId: aroundMessageId } : undefined,
      }),
    ),

  searchMessages: (conversationId, query) =>
    unwrap(api.get(`/conversations/${conversationId}/messages/search`, { params: { query } })),

  getConversation: (conversationId) => unwrap(api.get(`/conversations/${conversationId}`)),

  markRead: (conversationId, lastReadMessageId) =>
    unwrap(api.put(`/conversations/${conversationId}/read`, lastReadMessageId ? { lastReadMessageId } : undefined)),
  pin: (conversationId, enabled = true) => unwrap(api.put(`/conversations/${conversationId}/pin`, { enabled })),
  mute: (conversationId, enabled = true) => unwrap(api.put(`/conversations/${conversationId}/mute`, { enabled })),
  archive: (conversationId) => unwrap(api.put(`/conversations/${conversationId}/archive`)),
  unarchive: (conversationId) => unwrap(api.put(`/conversations/${conversationId}/unarchive`)),
  clearChat: (conversationId) => unwrap(api.delete(`/conversations/${conversationId}/messages`)),
  deleteConversation: (conversationId) => unwrap(api.delete(`/conversations/${conversationId}`)),

  createConversation: (userId) => unwrap(api.post('/conversations', { userId })),
};
