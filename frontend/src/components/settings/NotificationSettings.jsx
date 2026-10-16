import { useState } from 'react';
import Spinner from '../Spinner.jsx';
import Switch from './Switch.jsx';
import { useNotifications } from '../../hooks/useNotifications';
import { toastInfo } from '../../utils/alerts';

const ROWS = [
  { key: 'messageNotifications', label: 'Messages', description: 'Notify me about new messages.' },
  { key: 'missedCallNotifications', label: 'Missed calls', description: 'Notify me about calls I missed.' },
  { key: 'incomingCallNotifications', label: 'Incoming calls', description: 'Keep a record of calls that rang for me.' },
  { key: 'messagePreview', label: 'Message preview', description: 'Show the message text in notifications. When off, only "You have a new message" is shown.' },
  { key: 'browserNotifications', label: 'Browser notifications', description: 'Show pop-up notifications while VTChat is in the background.' },
];

export default function NotificationSettings() {
  const { settings, updateSettings, permission, requestBrowserPermission } = useNotifications();
  const [saving, setSaving] = useState(false);

  if (!settings) {
    return (
      <p className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400" role="status">
        <Spinner className="h-4 w-4" /> Loading notification settings…
      </p>
    );
  }

  const change = async (key, value) => {
    setSaving(true);
    try {
      await updateSettings({ [key]: value });
      if (key === 'browserNotifications' && value) {
        const result = await requestBrowserPermission();
        if (result === 'denied') toastInfo('Browser notifications are blocked. Allow them in your browser settings.');
        if (result === 'unsupported') toastInfo('This browser does not support notifications.');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="divide-y divide-slate-100 dark:divide-slate-800">
      {ROWS.map((row) => (
        <Switch
          key={row.key}
          id={`notif-${row.key}`}
          label={row.label}
          description={row.description}
          checked={!!settings[row.key]}
          disabled={saving}
          onChange={(value) => change(row.key, value)}
        />
      ))}
      <p className="pt-3 text-xs text-slate-500 dark:text-slate-400">
        Browser permission: <strong>{permission}</strong>
        {permission === 'denied' && ' - allow notifications for this site in your browser settings to use pop-ups.'}
      </p>
    </div>
  );
}
