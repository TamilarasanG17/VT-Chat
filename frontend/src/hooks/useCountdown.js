import { useCallback, useEffect, useState } from 'react';

export function useCountdown(initialSeconds = 0) {
  const [deadline, setDeadline] = useState(() => Date.now() + initialSeconds * 1000);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (deadline <= Date.now()) return undefined;
    setNow(Date.now());
    const id = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= deadline) clearInterval(id);
    }, 250);
    return () => clearInterval(id);
  }, [deadline]);

  const start = useCallback((seconds) => {
    setDeadline(Date.now() + seconds * 1000);
    setNow(Date.now());
  }, []);

  return [Math.max(0, Math.ceil((deadline - now) / 1000)), start];
}
