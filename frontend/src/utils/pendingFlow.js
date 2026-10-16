const KEY = 'vtchat.pending_flow';

function read() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY)) || null;
  } catch {
    return null;
  }
}

function write(value) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(value));
  } catch {
  }
}

export const pendingFlow = {
  start({ email, purpose, cooldownSeconds = 0 }) {
    write({ email, purpose, resendAt: Date.now() + cooldownSeconds * 1000 });
  },
  get: read,
  setCooldown(seconds) {
    const flow = read();
    if (flow) write({ ...flow, resendAt: Date.now() + seconds * 1000 });
  },
  remainingCooldown() {
    const flow = read();
    if (!flow?.resendAt) return 0;
    return Math.max(0, Math.ceil((flow.resendAt - Date.now()) / 1000));
  },
  clear() {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
    }
  },
};
