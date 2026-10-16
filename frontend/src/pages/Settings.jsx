import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { LogoutIcon } from '../components/Icons.jsx';
import SettingsSection from '../components/settings/SettingsSection.jsx';
import ThemeSelector from '../components/settings/ThemeSelector.jsx';
import AccountInfo from '../components/settings/AccountInfo.jsx';
import NotificationSettings from '../components/settings/NotificationSettings.jsx';
import PrivacySettings from '../components/settings/PrivacySettings.jsx';
import ChangePasswordForm from '../components/settings/ChangePasswordForm.jsx';
import { useAuth } from '../hooks/useAuth';
import { confirmLogout, toastSuccess } from '../utils/alerts';

export default function Settings() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (!(await confirmLogout())) return;
    setLoggingOut(true);
    await logout();
    navigate('/login', { replace: true });
    toastSuccess('You have been logged out');
  };

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Settings" description="Appearance, notifications, privacy and your account." />

      <div className="space-y-4 sm:space-y-6">
        <SettingsSection title="Appearance" description="Choose how VTChat looks. Your choice is saved on this device.">
          <ThemeSelector />
        </SettingsSection>

        <SettingsSection title="Notifications" description="Choose what you are notified about. These settings are applied by the server.">
          <NotificationSettings />
        </SettingsSection>

        <SettingsSection title="Privacy" description="Choose what the people you chat with can see. Hidden information is never sent to them.">
          <PrivacySettings />
        </SettingsSection>

        <SettingsSection title="Account" description="Your account details. Edit your name or picture on the Profile page.">
          <AccountInfo user={user} />
          <div className="mt-6">
            <Button variant="danger" onClick={handleLogout} loading={loggingOut} full={false}>
              {!loggingOut && <LogoutIcon className="h-5 w-5" />}
              Log out
            </Button>
          </div>
        </SettingsSection>

        <SettingsSection
          title="Change password"
          description="Changing your password signs you out of all your other devices."
        >
          <ChangePasswordForm />
        </SettingsSection>
      </div>
    </div>
  );
}
