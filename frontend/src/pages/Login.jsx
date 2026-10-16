import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import TextField from '../components/TextField.jsx';
import PasswordInput from '../components/PasswordInput.jsx';
import Button from '../components/Button.jsx';
import { authService } from '../services/authService';
import { pendingFlow } from '../utils/pendingFlow';
import { showError, toastInfo, toastSuccess } from '../utils/alerts';
import { getErrorCode, getErrorMessage, getFieldErrors, getRetryAfter } from '../utils/errors';
import { validateEmail } from '../utils/validators';

export default function Login() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }));
    if (errors[field]) setErrors((er) => ({ ...er, [field]: '' }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    const next = {
      email: validateEmail(form.email),
      password: form.password ? '' : 'Password is required',
    };
    setErrors(next);
    if (next.email || next.password) return;

    const email = form.email.trim().toLowerCase();
    setLoading(true);
    try {
      const res = await authService.login({ email, password: form.password });
      const { purpose, nextStep, resendCooldownSeconds } = res.data;
      pendingFlow.start({ email: res.data.email, purpose, cooldownSeconds: resendCooldownSeconds });
      toastSuccess('Verification code sent');
      navigate(nextStep === 'VERIFY_REGISTER_OTP' ? '/verify-otp' : '/verify-login-otp');
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'OTP_COOLDOWN') {
        pendingFlow.start({ email, purpose: 'LOGIN', cooldownSeconds: getRetryAfter(err) });
        toastInfo('A code was already sent. Check your email.');
        navigate('/verify-login-otp');
      } else if (code === 'VALIDATION_ERROR') {
        setErrors((er) => ({ ...er, ...getFieldErrors(err) }));
      } else {
        showError('Login failed', getErrorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Log in to continue your conversations."
      footer={
        <>
          New to VTChat?{' '}
          <Link to="/register" className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300">
            Create an account
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="space-y-4">
        <TextField
          label="Email"
          type="email"
          inputMode="email"
          value={form.email}
          onChange={update('email')}
          error={errors.email}
          autoComplete="email"
          autoCapitalize="none"
          placeholder="you@example.com"
        />
        <div>
          <PasswordInput
            label="Password"
            value={form.password}
            onChange={update('password')}
            error={errors.password}
            autoComplete="current-password"
            placeholder="Your password"
          />
          <div className="mt-2 text-right text-sm">
            <Link to="/forgot-password" className="font-medium text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300">
              Forgot password?
            </Link>
          </div>
        </div>
        <Button type="submit" loading={loading}>
          Log in
        </Button>
      </form>
    </AuthLayout>
  );
}
