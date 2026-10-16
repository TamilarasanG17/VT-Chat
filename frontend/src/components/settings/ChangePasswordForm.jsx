import { useState } from 'react';
import PasswordInput from '../PasswordInput.jsx';
import PasswordRules from '../PasswordRules.jsx';
import Button from '../Button.jsx';
import { userService } from '../../services/userService';
import { showError, showSuccess } from '../../utils/alerts';
import { getErrorCode, getErrorMessage, getFieldErrors } from '../../utils/errors';
import { validateConfirm, validatePassword } from '../../utils/validators';

const EMPTY = { current: '', password: '', confirm: '' };

export default function ChangePasswordForm() {
  const [form, setForm] = useState(EMPTY);
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
      current: form.current ? '' : 'Enter your current password',
      password: validatePassword(form.password),
      confirm: validateConfirm(form.password, form.confirm),
    };
    setErrors(next);
    if (Object.values(next).some(Boolean)) return;

    setLoading(true);
    try {
      await userService.changePassword({
        currentPassword: form.current,
        newPassword: form.password,
        confirmPassword: form.confirm,
      });
      setForm(EMPTY);
      setErrors({});
      await showSuccess('Password updated', 'Your password was changed and your other devices were signed out.');
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'INVALID_CURRENT_PASSWORD') {
        setErrors((er) => ({ ...er, current: getErrorMessage(err) }));
      } else if (code === 'PASSWORD_UNCHANGED') {
        setErrors((er) => ({ ...er, password: getErrorMessage(err) }));
      } else if (code === 'VALIDATION_ERROR') {
        const fields = getFieldErrors(err);
        setErrors((er) => ({
          ...er,
          current: fields.currentPassword || er.current,
          password: fields.newPassword || er.password,
          confirm: fields.confirmPassword || fields.passwordConfirmed || er.confirm,
        }));
      } else {
        showError('Could not change password', getErrorMessage(err));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit} className="max-w-md space-y-4">
      <PasswordInput
        label="Current password"
        value={form.current}
        onChange={update('current')}
        error={errors.current}
        autoComplete="current-password"
        disabled={loading}
      />
      <div>
        <PasswordInput
          label="New password"
          value={form.password}
          onChange={update('password')}
          error={errors.password}
          autoComplete="new-password"
          disabled={loading}
        />
        <PasswordRules password={form.password} />
      </div>
      <PasswordInput
        label="Confirm new password"
        value={form.confirm}
        onChange={update('confirm')}
        error={errors.confirm}
        autoComplete="new-password"
        disabled={loading}
      />
      <Button type="submit" loading={loading} full={false}>
        {loading ? 'Updating…' : 'Update password'}
      </Button>
    </form>
  );
}
