import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import TextField from '../components/TextField.jsx';
import PasswordInput from '../components/PasswordInput.jsx';
import PasswordRules from '../components/PasswordRules.jsx';
import Button from '../components/Button.jsx';
import { authService } from '../services/authService';
import { pendingFlow } from '../utils/pendingFlow';
import { showError, toastInfo, toastSuccess } from '../utils/alerts';
import { getErrorCode, getErrorMessage, getFieldErrors, getRetryAfter } from '../utils/errors';
import { validateConfirm, validateEmail, validatePassword, validateUsername } from '../utils/validators';

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }));
    if (errors[field]) setErrors((er) => ({ ...er, [field]: '' }));
  };

  const validate = () => {
    const next = {
      username: validateUsername(form.username),
      email: validateEmail(form.email),
      password: validatePassword(form.password),
      confirm: validateConfirm(form.password, form.confirm),
    };
    setErrors(next);
    return !Object.values(next).some(Boolean);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading || !validate()) return;

    const email = form.email.trim().toLowerCase();
    setLoading(true);
    try {
      const res = await authService.register({ username: form.username.trim(), email, password: form.password });
      pendingFlow.start({ email: res.data.email, purpose: 'REGISTER', cooldownSeconds: res.data.resendCooldownSeconds });
      toastSuccess('Verification code sent');
      navigate('/verify-otp');
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'EMAIL_ALREADY_EXISTS') {
        setErrors((er) => ({ ...er, email: getErrorMessage(err) }));
      } else if (code === 'VALIDATION_ERROR') {
        setErrors((er) => ({ ...er, ...getFieldErrors(err) }));
      } else if (code === 'OTP_COOLDOWN') {
        pendingFlow.start({ email, purpose: 'REGISTER', cooldownSeconds: getRetryAfter(err) });
        toastInfo('A code was already sent. Check your email.');
        navigate('/verify-otp');
      } else {
        showError('Registration failed', getErrorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Sign up to start private conversations."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300">
            Log in
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="space-y-4">
        <TextField
          label="Username"
          value={form.username}
          onChange={update('username')}
          error={errors.username}
          autoComplete="username"
          autoCapitalize="none"
          placeholder="e.g. alex_21"
        />
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
            autoComplete="new-password"
            placeholder="Create a password"
          />
          <PasswordRules password={form.password} />
        </div>
        <PasswordInput
          label="Confirm password"
          value={form.confirm}
          onChange={update('confirm')}
          error={errors.confirm}
          autoComplete="new-password"
          placeholder="Re-enter password"
        />
        <Button type="submit" loading={loading} className="mt-2">
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
