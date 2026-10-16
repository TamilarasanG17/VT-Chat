import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';

const LENGTH = 4;

export default function OtpInput({ onChange, onComplete, disabled = false, error = false }) {
  const [digits, setDigits] = useState(() => Array(LENGTH).fill(''));
  const refs = useRef([]);

  useEffect(() => {
    refs.current[0]?.focus();
  }, []);

  const commit = (next, focusIndex) => {
    setDigits(next);
    const code = next.join('');
    onChange?.(code);
    if (focusIndex !== undefined) refs.current[focusIndex]?.focus();
    if (next.every(Boolean)) onComplete?.(code);
  };

  const fill = (start, text) => {
    const next = [...digits];
    let index = start;
    for (const ch of text) {
      if (index >= LENGTH) break;
      next[index] = ch;
      index += 1;
    }
    commit(next, Math.min(index, LENGTH - 1));
  };

  const handleChange = (i, e) => {
    const value = e.target.value.replace(/\D/g, '');
    if (!value) {
      const next = [...digits];
      next[i] = '';
      commit(next);
      return;
    }
    if (value.length > 1) {
      fill(i, value);
      return;
    }
    const next = [...digits];
    next[i] = value;
    commit(next, Math.min(i + 1, LENGTH - 1));
  };

  const handleKeyDown = (i, e) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      const next = [...digits];
      if (digits[i]) {
        next[i] = '';
        commit(next);
      } else if (i > 0) {
        next[i - 1] = '';
        commit(next, i - 1);
      }
    } else if (e.key === 'ArrowLeft' && i > 0) {
      e.preventDefault();
      refs.current[i - 1]?.focus();
    } else if (e.key === 'ArrowRight' && i < LENGTH - 1) {
      e.preventDefault();
      refs.current[i + 1]?.focus();
    }
  };

  const handlePaste = (i, e) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, LENGTH);
    if (!text) return;
    fill(text.length === LENGTH ? 0 : i, text);
  };

  return (
    <motion.div
      animate={error ? { x: [0, -8, 8, -6, 6, 0] } : { x: 0 }}
      transition={{ duration: 0.4 }}
      role="group"
      aria-label="4-digit verification code"
      className="mx-auto flex w-full max-w-[18rem] gap-1.5 min-[360px]:gap-2 sm:gap-3"
    >
      {digits.map((digit, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          aria-label={`Digit ${i + 1} of ${LENGTH}`}
          value={digit}
          disabled={disabled}
          onChange={(e) => handleChange(i, e)}
          onKeyDown={(e) => handleKeyDown(i, e)}
          onPaste={(e) => handlePaste(i, e)}
          onFocus={(e) => e.target.select()}
          className={`h-12 min-w-0 flex-1 min-[360px]:h-14 rounded-xl border bg-white text-center font-semibold text-slate-900 shadow-sm outline-none transition focus:ring-2 disabled:bg-slate-50 dark:bg-slate-800 dark:text-white dark:disabled:bg-slate-800/60 text-xl min-[360px]:text-2xl sm:h-16 sm:text-3xl ${
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/25'
              : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/25 dark:border-slate-600'
          }`}
        />
      ))}
    </motion.div>
  );
}
