let ctx = null;
let timer = null;

function context() {
  if (!ctx) {
    const Ctor = typeof window !== 'undefined' ? window.AudioContext || window.webkitAudioContext : null;
    if (!Ctor) return null;
    try {
      ctx = new Ctor();
    } catch {
      return null;
    }
  }
  if (ctx.state === 'suspended') void ctx.resume().catch(() => {});
  return ctx;
}

function beep(c, freq, start, length, volume) {
  const osc = c.createOscillator();
  const gain = c.createGain();
  osc.type = 'sine';
  osc.frequency.value = freq;
  gain.gain.setValueAtTime(0, start);
  gain.gain.linearRampToValueAtTime(volume, start + 0.03);
  gain.gain.linearRampToValueAtTime(0, start + length);
  osc.connect(gain).connect(c.destination);
  osc.start(start);
  osc.stop(start + length + 0.05);
}

export function startRingtone(kind) {
  stopRingtone();
  const play = () => {
    const c = context();
    if (!c) return;
    const t = c.currentTime;
    if (kind === 'incoming') {
      beep(c, 880, t, 0.35, 0.18);
      beep(c, 660, t + 0.4, 0.35, 0.18);
      try {
        navigator.vibrate?.([300, 150, 300]);
      } catch {
      }
    } else {
      beep(c, 440, t, 1.0, 0.07);
    }
  };
  play();
  timer = setInterval(play, kind === 'incoming' ? 2200 : 3000);
}

export function stopRingtone() {
  clearInterval(timer);
  timer = null;
  try {
    navigator.vibrate?.(0);
  } catch {
  }
}
