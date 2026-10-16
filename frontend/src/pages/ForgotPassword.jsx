import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import TextField from '../components/TextField.jsx';
import Button from '../components/Button.jsx';
import { authService } from '../services/authService';
import { pendingFlow } from '../utils/pendingFlow';
import { showError, toastSuccess } from '../utils/alerts';
import { getErrorMessage } from '../utils/errors';
import { validateEmail } from '../utils/validators';

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    const problem = validateEmail(email);
    setError(problem);
    if (problem) return;

    setLoading(true);
    try {
      const normalized = email.trim().toLowerCase();
      const res = await authService.forgotPassword(normalized);
      pendingFlow.start({ email: normalized, purpose: 'RESET_PASSWORD', cooldownSeconds: res.data.resendCooldownSeconds });
      toastSuccess('If an account exists, a code is on its way');
      navigate('/verify-reset-otp');
    } catch (err) {
      showError('Something went wrong', getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Forgot your password?"
      subtitle="Enter your email and we'll send you a 4-digit code to reset it."
      footer={
        <Link to="/login" className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300">
          Back to login
        </Link>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="space-y-4">
        <TextField
          label="Email"
          type="email"
          inputMode="email"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (error) setError('');
          }}
          error={error}
          autoComplete="email"
          autoCapitalize="none"
          placeholder="you@example.com"
        />
        <Button type="submit" loading={loading}>
          Send code
        </Button>
      </form>
    </AuthLayout>
  );
}
