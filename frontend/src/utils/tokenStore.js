let accessToken = null;

export const tokenStore = {
  get: () => accessToken,
  set: (token) => {
    accessToken = token;
  },
  clear: () => {
    accessToken = null;
  },
};

const HINT_KEY = 'vtchat.session_hint';

export const sessionHint = {
  has: () => {
    try {
      return localStorage.getItem(HINT_KEY) === '1';
    } catch {
      return false;
    }
  },
  set: () => {
    try {
      localStorage.setItem(HINT_KEY, '1');
    } catch {
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(HINT_KEY);
    } catch {
    }
  },
};
