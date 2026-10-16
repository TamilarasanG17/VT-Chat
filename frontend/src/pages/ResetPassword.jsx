import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import PasswordInput from '../components/PasswordInput.jsx';
import PasswordRules from '../components/PasswordRules.jsx';
import Button from '../components/Button.jsx';
import { authService } from '../services/authService';
import { pendingFlow } from '../utils/pendingFlow';
import { showError, showSuccess } from '../utils/alerts';
import { getErrorCode, getErrorMessage, getFieldErrors } from '../utils/errors';
import { validateConfirm, validatePassword } from '../utils/validators';

export default function ResetPassword() {
  const navigate = useNavigate();
  const location = useLocation();
  const flow = pendingFlow.get();
  const resetToken = location.state?.resetToken;

  const [form, setForm] = useState({ password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  if (!flow?.email || flow.purpose !== 'RESET_PASSWORD' || !resetToken) {
    return <Navigate to="/forgot-password" replace />;
  }

  const update = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }));
    if (errors[field]) setErrors((er) => ({ ...er, [field]: '' }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    const next = {
      password: validatePassword(form.password),
      confirm: validateConfirm(form.password, form.confirm),
    };
    setErrors(next);
    if (next.password || next.confirm) return;

    setLoading(true);
    try {
      await authService.resetPassword({
        email: flow.email,
        resetToken,
        newPassword: form.password,
        confirmPassword: form.confirm,
      });
      pendingFlow.clear();
      await showSuccess('Password reset', 'Your password has been changed. Log in with your new password.');
      navigate('/login', { replace: true });
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'INVALID_RESET_TOKEN') {
        pendingFlow.clear();
        await showError('Reset session expired', getErrorMessage(err));
        navigate('/forgot-password', { replace: true });
      } else if (code === 'VALIDATION_ERROR') {
        const fields = getFieldErrors(err);
        setErrors((er) => ({
          ...er,
          password: fields.newPassword || er.password,
          confirm: fields.confirmPassword || fields.passwordConfirmed || er.confirm,
        }));
      } else {
        showError('Could not reset password', getErrorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout title="Set a new password" subtitle="Choose a strong password you haven't used before.">
      <form noValidate onSubmit={handleSubmit} className="space-y-4">
        <div>
          <PasswordInput
            label="New password"
            value={form.password}
            onChange={update('password')}
            error={errors.password}
            autoComplete="new-password"
            placeholder="Create a password"
          />
          <PasswordRules password={form.password} />
        </div>
        <PasswordInput
          label="Confirm new password"
          value={form.confirm}
          onChange={update('confirm')}
          error={errors.confirm}
          autoComplete="new-password"
          placeholder="Re-enter password"
        />
        <Button type="submit" loading={loading} className="mt-2">
          Reset password
        </Button>
      </form>
    </AuthLayout>
  );
}
