import { useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import OtpInput from './OtpInput.jsx';
import Button from './Button.jsx';
import { authService } from '../services/authService';
import { useCountdown } from '../hooks/useCountdown';
import { pendingFlow } from '../utils/pendingFlow';
import { getErrorMessage, getRetryAfter } from '../utils/errors';
import { showError, toastSuccess } from '../utils/alerts';

const formatTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

export default function OtpForm({ email, purpose, onVerify, submitLabel = 'Verify', backTo, backLabel }) {
  const [otp, setOtp] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resetKey, setResetKey] = useState(0);
  const [seconds, startCountdown] = useCountdown(pendingFlow.remainingCooldown());
  const busy = useRef(false);

  const submit = async (code) => {
    if (busy.current) return;
    if (code.length !== 4) {
      setError('Enter the 4-digit code');
      return;
    }
    busy.current = true;
    setError('');
    setLoading(true);
    try {
      await onVerify(code);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not verify the code. Please try again.'));
      setOtp('');
      setResetKey((k) => k + 1);
    } finally {
      busy.current = false;
      setLoading(false);
    }
  };

  const resend = async () => {
    if (seconds > 0 || resending) return;
    setResending(true);
    try {
      const res = await authService.resendOtp(email, purpose);
      const cooldown = res.data.resendCooldownSeconds;
      pendingFlow.setCooldown(cooldown);
      startCountdown(cooldown);
      setError('');
      setOtp('');
      setResetKey((k) => k + 1);
      toastSuccess('If your request is valid, a new code is on its way');
    } catch (err) {
      const retry = getRetryAfter(err);
      if (retry) {
        pendingFlow.setCooldown(retry);
        startCountdown(retry);
      }
      showError('Could not resend the code', getErrorMessage(err));
    } finally {
      setResending(false);
    }
  };

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        submit(otp);
      }}
    >
      <p className="mb-5 text-center text-sm text-slate-600 dark:text-slate-400">
        We sent a 4-digit code to <span className="break-all font-semibold text-slate-900 dark:text-white">{email}</span>
      </p>

      <OtpInput key={resetKey} onChange={setOtp} onComplete={submit} disabled={loading} error={Boolean(error)} />

      <div className="min-h-[1.5rem]">
        <AnimatePresence>
          {error && (
            <motion.p
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              role="alert"
              className="mt-3 text-center text-sm text-red-600 dark:text-red-400"
            >
              {error}
            </motion.p>
          )}
        </AnimatePresence>
      </div>

      <Button type="submit" loading={loading} disabled={otp.length !== 4} className="mt-4">
        {submitLabel}
      </Button>

      <div className="mt-5 text-center text-sm text-slate-600 dark:text-slate-400">
        {seconds > 0 ? (
          <>
            Didn&apos;t get the code? Resend in{' '}
            <span className="font-semibold tabular-nums text-slate-900 dark:text-white">{formatTime(seconds)}</span>
          </>
        ) : (
          <button
            type="button"
            onClick={resend}
            disabled={resending}
            className="inline-flex min-h-11 items-center rounded-lg px-3 font-semibold text-indigo-600 transition hover:text-indigo-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/40 dark:text-indigo-400 dark:hover:text-indigo-300 disabled:opacity-60"
          >
            {resending ? 'Sending…' : 'Resend code'}
          </button>
        )}
      </div>

      {backTo && (
        <div className="mt-3 text-center text-sm">
          <Link to={backTo} className="inline-flex min-h-10 items-center rounded-lg px-3 font-medium text-slate-500 transition hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200">
            {backLabel}
          </Link>
        </div>
      )}
    </form>
  );
}
