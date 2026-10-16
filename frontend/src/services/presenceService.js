import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const presenceService = {
  getPresence: (userId) => unwrap(api.get(`/users/${userId}/presence`)),

  getPartnersPresence: () => unwrap(api.get('/presence')),

  updatePrivacy: ({ showOnlineStatus, showLastSeen }) => unwrap(api.put('/users/me/privacy', { showOnlineStatus, showLastSeen })),
};
