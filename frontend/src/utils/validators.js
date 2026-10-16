const EMAIL_REGEX = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const USERNAME_REGEX = /^[A-Za-z0-9_.-]+$/;
const PASSWORD_CHARS = /^[\x21-\x7E]+$/;

export function validateUsername(value) {
  const v = value.trim();
  if (!v) return 'Username is required';
  if (v.length < 3 || v.length > 30) return 'Username must be 3-30 characters';
  if (!USERNAME_REGEX.test(v)) return 'Use only letters, numbers, dots, dashes and underscores';
  return '';
}

export const BIO_MAX = 160;

export function validateBio(value) {
  return (value || '').trim().length > BIO_MAX ? `Bio must be ${BIO_MAX} characters or fewer` : '';
}

export function validateEmail(value) {
  const v = value.trim();
  if (!v) return 'Email is required';
  if (v.length > 254 || !EMAIL_REGEX.test(v)) return 'Enter a valid email address';
  return '';
}

export function getPasswordRules(password) {
  return [
    { id: 'length', label: '8-64 characters', met: password.length >= 8 && password.length <= 64 },
    { id: 'upper', label: 'One uppercase letter', met: /[A-Z]/.test(password) },
    { id: 'lower', label: 'One lowercase letter', met: /[a-z]/.test(password) },
    { id: 'digit', label: 'One number', met: /\d/.test(password) },
    { id: 'special', label: 'One special character', met: /[^A-Za-z0-9]/.test(password) },
  ];
}

export function validatePassword(password) {
  if (!password) return 'Password is required';
  if (!PASSWORD_CHARS.test(password)) return 'Password cannot contain spaces or non-standard characters';
  if (getPasswordRules(password).some((r) => !r.met)) return 'Password does not meet all the requirements';
  return '';
}

export function validateConfirm(password, confirm) {
  if (!confirm) return 'Please confirm your password';
  if (password !== confirm) return 'Passwords do not match';
  return '';
}
