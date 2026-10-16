import { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { presenceService } from '../../services/presenceService';
import { toastError } from '../../utils/alerts';
import Switch from './Switch.jsx';

export default function PrivacySettings() {
  const { user, updateUser } = useAuth();
  const [saving, setSaving] = useState(false);
  const online = user?.showOnlineStatus ?? true;
  const lastSeen = user?.showLastSeen ?? true;

  const save = async (next) => {
    setSaving(true);
    try {
      const res = await presenceService.updatePrivacy(next);
      updateUser(res.data);
    } catch {
      toastError('Could not save your privacy settings. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="divide-y divide-slate-100 dark:divide-slate-800">
      <Switch
        id="privacy-online"
        label="Show online status"
        description="Let people you chat with see when you are online."
        checked={online}
        disabled={saving}
        onChange={(value) => save({ showOnlineStatus: value, showLastSeen: lastSeen })}
      />
      <Switch
        id="privacy-last-seen"
        label="Show last seen"
        description="Let people you chat with see when you were last online."
        checked={lastSeen}
        disabled={saving}
        onChange={(value) => save({ showOnlineStatus: online, showLastSeen: value })}
      />
    </div>
  );
}
