import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const userService = {
  getMe: () => unwrap(api.get('/users/me')),

  searchUsers: (query) => unwrap(api.get('/users/search', { params: { query } })),

  updateProfile: ({ username, bio }) => unwrap(api.put('/users/me', { username, bio })),

  uploadProfilePicture: (file, onProgress) => {
    const form = new FormData();
    form.append('file', file);
    return unwrap(
      api.post('/users/me/profile-picture', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000,
        onUploadProgress: (event) => {
          if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total));
        },
      }),
    );
  },

  removeProfilePicture: () => unwrap(api.delete('/users/me/profile-picture')),

  changePassword: ({ currentPassword, newPassword, confirmPassword }) =>
    unwrap(api.put('/users/me/password', { currentPassword, newPassword, confirmPassword })),
};
