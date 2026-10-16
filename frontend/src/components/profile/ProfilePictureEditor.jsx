import { useRef, useState } from 'react';
import Avatar from './Avatar.jsx';
import Button from '../Button.jsx';
import ProfilePhotoCropModal from './ProfilePhotoCropModal.jsx';
import { CameraIcon, TrashIcon } from '../Icons.jsx';
import { userService } from '../../services/userService';
import { useAuth } from '../../hooks/useAuth';
import { confirmAction, showError, toastSuccess } from '../../utils/alerts';
import { getErrorMessage } from '../../utils/errors';
import { ALLOWED_AVATAR_TYPES, MAX_AVATAR_MB, validateImageFile } from '../../utils/image';

export default function ProfilePictureEditor({ user, onUserChange }) {
  const { updateUser } = useAuth();
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);
  const [file, setFile] = useState(null);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [error, setError] = useState('');
  const [dialogError, setDialogError] = useState('');

  const busy = uploading || removing;

  const handleChoose = (e) => {
    const chosen = e.target.files?.[0];
    e.target.value = '';
    if (!chosen) return;

    const problem = validateImageFile(chosen);
    if (problem) {
      setFile(null);
      setError(problem);
      showError('Invalid image', problem);
      return;
    }
    setError('');
    setDialogError('');
    setProgress(0);
    setFile(chosen);
  };

  const closeCropper = () => {
    if (uploadingRef.current) return;
    setFile(null);
    setDialogError('');
    setProgress(0);
  };

  const uploadCropped = async (cropped) => {
    if (uploadingRef.current) return;
    uploadingRef.current = true;
    setUploading(true);
    setProgress(0);
    setDialogError('');
    try {
      const res = await userService.uploadProfilePicture(cropped, setProgress);
      onUserChange(res.data);
      updateUser(res.data);
      setFile(null);
      toastSuccess('Profile picture updated');
    } catch (err) {
      setDialogError(getErrorMessage(err, 'Could not upload the picture. Please try again.'));
    } finally {
      uploadingRef.current = false;
      setUploading(false);
    }
  };

  const handleRemove = async () => {
    if (busy) return;
    const ok = await confirmAction({
      title: 'Remove profile picture?',
      text: 'Your default avatar will be shown instead.',
      confirmText: 'Remove',
      icon: 'warning',
    });
    if (!ok) return;

    setRemoving(true);
    try {
      const res = await userService.removeProfilePicture();
      onUserChange(res.data);
      updateUser(res.data);
      toastSuccess('Profile picture removed');
    } catch (err) {
      showError('Could not remove picture', getErrorMessage(err));
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="flex flex-col items-center text-center">
      <Avatar src={user.profilePictureUrl} name={user.username} size="lg" />

      <input
        ref={inputRef}
        type="file"
        accept={ALLOWED_AVATAR_TYPES.join(',')}
        onChange={handleChoose}
        className="hidden"
        aria-label="Choose a profile picture"
      />

      <div className="mt-5 flex w-full flex-wrap justify-center gap-2">
        <Button
          variant="secondary"
          onClick={() => inputRef.current?.click()}
          disabled={busy}
          full={false}
          className="min-w-0 grow basis-auto whitespace-nowrap px-3! text-sm!"
        >
          <CameraIcon className="h-5 w-5 shrink-0" />
          Change Profile Photo
        </Button>
        {user.profilePictureUrl && (
          <Button
            variant="danger"
            onClick={handleRemove}
            loading={removing}
            disabled={uploading}
            full={false}
            className="min-w-0 grow basis-auto whitespace-nowrap px-3! text-sm!"
          >
            {!removing && <TrashIcon className="h-5 w-5 shrink-0" />}
            Remove
          </Button>
        )}
      </div>

      {error && (
        <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
      <p className="mt-3 max-w-[16rem] text-xs text-slate-500 dark:text-slate-400">JPEG or PNG, up to {MAX_AVATAR_MB} MB. You can crop it before it is saved.</p>

      <ProfilePhotoCropModal file={file} busy={uploading} progress={progress} error={dialogError} onConfirm={uploadCropped} onCancel={closeCropper} />
    </div>
  );
}
