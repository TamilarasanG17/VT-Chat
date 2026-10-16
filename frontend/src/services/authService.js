import api from './api';

const unwrap = (promise) => promise.then((res) => res.data);

export const authService = {
  register: ({ username, email, password }) => unwrap(api.post('/auth/register', { username, email, password })),

  verifyRegisterOtp: (email, otp) => unwrap(api.post('/auth/verify-register-otp', { email, otp })),

  login: ({ email, password }) => unwrap(api.post('/auth/login', { email, password })),

  verifyLoginOtp: (email, otp) => unwrap(api.post('/auth/verify-login-otp', { email, otp })),

  resendOtp: (email, purpose) => unwrap(api.post('/auth/resend-otp', { email, purpose })),

  forgotPassword: (email) => unwrap(api.post('/auth/forgot-password', { email })),

  verifyResetOtp: (email, otp) => unwrap(api.post('/auth/verify-reset-otp', { email, otp })),

  resetPassword: ({ email, resetToken, newPassword, confirmPassword }) =>
    unwrap(api.post('/auth/reset-password', { email, resetToken, newPassword, confirmPassword })),

  logout: () => unwrap(api.post('/auth/logout')),

  me: () => unwrap(api.get('/auth/me')),
};
