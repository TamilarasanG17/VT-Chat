import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const notificationService = {
  list: (page = 0, size = 20) => unwrap(api.get('/notifications', { params: { page, size } })),
  unreadCount: () => unwrap(api.get('/notifications/unread-count')),
  markRead: (id) => unwrap(api.put(`/notifications/${id}/read`)),
  markAllRead: () => unwrap(api.put('/notifications/read-all')),
  remove: (id) => unwrap(api.delete(`/notifications/${id}`)),

  getSettings: () => unwrap(api.get('/users/me/notification-settings')),
  updateSettings: (patch) => unwrap(api.put('/users/me/notification-settings', patch)),
};
