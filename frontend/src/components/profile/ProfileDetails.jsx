import { useRef, useState } from 'react';
import TextField from '../TextField.jsx';
import Button from '../Button.jsx';
import VerifiedBadge from './VerifiedBadge.jsx';
import { userService } from '../../services/userService';
import { useAuth } from '../../hooks/useAuth';
import { showError, toastSuccess } from '../../utils/alerts';
import { getErrorCode, getErrorMessage, getFieldErrors } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { BIO_MAX, validateBio, validateUsername } from '../../utils/validators';

function Row({ label, children }) {
  return (
    <div className="min-w-0 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
      <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-1 min-w-0 break-words text-sm text-slate-900 sm:col-span-2 sm:mt-0 dark:text-white">{children}</dd>
    </div>
  );
}

const norm = (v) => (v || '').trim();

export default function ProfileDetails({ user, onUserChange }) {
  const { updateUser } = useAuth();
  const savingRef = useRef(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ username: user.username, bio: user.bio || '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const changed = norm(form.username) !== user.username || norm(form.bio) !== norm(user.bio);

  const startEditing = () => {
    setForm({ username: user.username, bio: user.bio || '' });
    setErrors({});
    setEditing(true);
  };

  const cancel = () => {
    if (savingRef.current) return;
    setEditing(false);
    setForm({ username: user.username, bio: user.bio || '' });
    setErrors({});
  };

  const setField = (name) => (e) => {
    setForm((f) => ({ ...f, [name]: e.target.value }));
    if (errors[name]) setErrors((cur) => ({ ...cur, [name]: '' }));
  };

  const save = async (e) => {
    e.preventDefault();
    if (savingRef.current || !changed) return;

    const problems = { username: validateUsername(norm(form.username)), bio: validateBio(form.bio) };
    if (problems.username || problems.bio) {
      setErrors(problems);
      return;
    }

    savingRef.current = true;
    setSaving(true);
    try {
      const res = await userService.updateProfile({ username: norm(form.username), bio: norm(form.bio) });
      onUserChange(res.data);
      updateUser((current) => ({ ...current, ...res.data }));
      setEditing(false);
      toastSuccess('Profile updated');
    } catch (err) {
      const code = getErrorCode(err);
      if (code === 'VALIDATION_ERROR') {
        const fields = getFieldErrors(err);
        setErrors({ username: fields.username || '', bio: fields.bio || '' });
        if (!fields.username && !fields.bio) showError('Could not update profile', getErrorMessage(err));
      } else if (code === 'USERNAME_TAKEN') {
        setErrors({ username: getErrorMessage(err) });
      } else {
        showError('Could not update profile', getErrorMessage(err));
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <form noValidate onSubmit={save}>
      <dl className="divide-y divide-slate-200 dark:divide-slate-800">
        <Row label="Username">
          {editing ? (
            <TextField
              label="Username"
              value={form.username}
              onChange={setField('username')}
              error={errors.username}
              disabled={saving}
              autoComplete="username"
              autoCapitalize="none"
              maxLength={60}
              autoFocus
              hint="3-30 characters: letters, numbers, dots, dashes, underscores."
            />
          ) : (
            <span className="font-medium">{user.username}</span>
          )}
        </Row>
        <Row label="Bio">
          {editing ? (
            <div className="min-w-0">
              <label htmlFor="profile-bio" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Bio
              </label>
              <textarea
                id="profile-bio"
                rows={3}
                value={form.bio}
                onChange={setField('bio')}
                disabled={saving}
                maxLength={BIO_MAX * 2}
                aria-invalid={errors.bio ? 'true' : 'false'}
                aria-describedby="profile-bio-help"
                className={`block w-full min-w-0 resize-none rounded-xl border bg-white px-3.5 py-2.5 text-base text-slate-900 shadow-sm outline-none transition focus:ring-2 disabled:bg-slate-100 disabled:text-slate-500 dark:bg-slate-800 dark:text-white dark:disabled:bg-slate-800/60 ${
                  errors.bio ? 'border-red-400 focus:border-red-500 focus:ring-red-500/25' : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/25 dark:border-slate-600'
                }`}
              />
              <p id="profile-bio-help" role={errors.bio ? 'alert' : undefined} className={`mt-1.5 text-sm ${errors.bio ? 'text-red-600 dark:text-red-400' : 'text-slate-500 dark:text-slate-400'}`}>
                {errors.bio || `Optional. ${norm(form.bio).length}/${BIO_MAX}`}
              </p>
            </div>
          ) : user.bio ? (
            <span className="whitespace-pre-line break-words">{user.bio}</span>
          ) : (
            <span className="text-slate-400 dark:text-slate-500">No bio yet</span>
          )}
        </Row>
        <Row label="Email">
          <span>{user.email}</span>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Email can&apos;t be changed here.</p>
        </Row>
        <Row label="Email status">
          <VerifiedBadge verified={user.emailVerified} />
        </Row>
        <Row label="Member since">{formatDate(user.createdAt)}</Row>
      </dl>

      <div className="mt-6 flex flex-col gap-2 min-[425px]:flex-row">
        {editing ? (
          <>
            <Button type="submit" loading={saving} disabled={!changed} full={false} className="min-[425px]:min-w-36">
              {saving ? 'Saving…' : 'Save changes'}
            </Button>
            <Button variant="secondary" onClick={cancel} disabled={saving} full={false}>
              Cancel
            </Button>
          </>
        ) : (
          <Button variant="secondary" onClick={startEditing} full={false}>
            Edit profile
          </Button>
        )}
      </div>
    </form>
  );
}
