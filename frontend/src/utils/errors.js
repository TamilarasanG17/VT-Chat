export function getErrorMessage(err, fallback = 'Something went wrong. Please try again.') {
  if (err?.response?.data?.message) return err.response.data.message;
  if (err?.response) return fallback;
  if (err?.code === 'ECONNABORTED') return 'The request timed out. Please try again.';
  return 'Cannot reach the server. Check your connection and try again.';
}

export const getErrorCode = (err) => err?.response?.data?.code;

export const getRetryAfter = (err) => {
  const n = Number(err?.response?.data?.retryAfterSeconds ?? err?.response?.data?.retryAfter);
  return Number.isFinite(n) && n > 0 ? n : 0;
};

export const isRateLimited = (err) => err?.response?.status === 429 && err?.response?.data?.code === 'RATE_LIMITED';

export function rateLimitMessage(seconds) {
  const n = Number(seconds);
  if (!Number.isFinite(n) || n <= 0) return 'Too many requests. Please try again shortly.';
  if (n < 60) return `Too many requests. Please try again in ${Math.ceil(n)} second${Math.ceil(n) === 1 ? '' : 's'}.`;
  const minutes = Math.ceil(n / 60);
  return `Too many requests. Please try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`;
}

export const getFieldErrors = (err) => err?.response?.data?.errors || {};

export function getChatErrorMessage(err, fallback = 'Something went wrong. Please try again.') {
  const status = err?.response?.status;
  if (!status) return getErrorMessage(err, fallback);
  const serverMessage = err.response.data?.message;
  switch (status) {
    case 401:
      return 'Your session has expired. Please log in again.';
    case 403:
      return serverMessage || 'You do not have access to this conversation';
    case 404:
      return serverMessage || 'Not found';
    case 409:
      return 'Conversation already exists';
    case 422:
      return serverMessage || 'That request was not valid';
    case 400:
      return serverMessage || fallback;
    default:
      return fallback;
  }
}
