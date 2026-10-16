import api from './api';
import { websocketService } from './websocketService';

const unwrap = (promise) => promise.then((res) => res.data);

export const messageApi = {
  edit: (messageId, content) => unwrap(api.put(`/messages/${messageId}`, { content })),
  react: (messageId, reaction) => unwrap(api.post(`/messages/${messageId}/reactions`, { reaction })),
  unreact: (messageId) => unwrap(api.delete(`/messages/${messageId}/reactions`)),
  deleteForMe: (messageId) => unwrap(api.delete(`/messages/${messageId}/for-me`)),
  deleteForEveryone: (messageId) => unwrap(api.delete(`/messages/${messageId}/for-everyone`)),
  forward: (messageId, conversationId) => unwrap(api.post(`/messages/${messageId}/forward`, { conversationId })),
};

const viaSocketOrRest = async (destination, payload, restCall) =>
  websocketService.publish(destination, payload) ? null : restCall();

export const messageActions = {
  send: (payload) => websocketService.publish('/app/chat.send', payload),

  react: (messageId, reaction) =>
    viaSocketOrRest('/app/message.react', { messageId, reaction }, () => messageApi.react(messageId, reaction)),

  unreact: (messageId) =>
    viaSocketOrRest('/app/message.unreact', { messageId }, () => messageApi.unreact(messageId)),

  edit: (messageId, content) =>
    viaSocketOrRest('/app/message.edit', { messageId, content }, () => messageApi.edit(messageId, content)),

  remove: (messageId, scope) =>
    viaSocketOrRest('/app/message.delete', { messageId, scope }, () =>
      scope === 'EVERYONE' ? messageApi.deleteForEveryone(messageId) : messageApi.deleteForMe(messageId),
    ),

  forward: messageApi.forward,
};
