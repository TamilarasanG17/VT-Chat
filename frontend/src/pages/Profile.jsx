import { useCallback, useEffect, useState } from 'react';
import Button from '../components/Button.jsx';
import Card from '../components/Card.jsx';
import PageHeader from '../components/PageHeader.jsx';
import ProfilePictureEditor from '../components/profile/ProfilePictureEditor.jsx';
import ProfileDetails from '../components/profile/ProfileDetails.jsx';
import { userService } from '../services/userService';
import { getErrorMessage } from '../utils/errors';

const card = 'p-4 min-[480px]:p-5 sm:p-6';

function ProfileSkeleton() {
  return (
    <div className="grid animate-pulse gap-6 lg:grid-cols-[minmax(18rem,24rem)_minmax(0,1fr)]" role="status" aria-label="Loading profile">
      <Card className={`${card} flex flex-col items-center`}>
        <div className="h-32 w-32 rounded-full bg-slate-200 dark:bg-slate-700" />
        <div className="mt-6 h-10 w-full max-w-xs rounded-xl bg-slate-200 dark:bg-slate-700" />
      </Card>
      <Card className={`${card} space-y-5`}>
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-5 rounded bg-slate-200 dark:bg-slate-700" />
        ))}
      </Card>
    </div>
  );
}

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await userService.getMe();
      setProfile(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'We could not load your profile.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Your profile" description="Your picture and username are visible to people you chat with." />

      {loading && <ProfileSkeleton />}

      {!loading && error && (
        <Card className={`${card} text-center`} role="alert">
          <p className="text-slate-700 dark:text-slate-300">{error}</p>
          <Button onClick={load} full={false} className="mt-4">
            Try again
          </Button>
        </Card>
      )}

      {!loading && profile && (
        <div className="grid items-start gap-4 sm:gap-6 lg:grid-cols-[minmax(18rem,24rem)_minmax(0,1fr)]">
          <Card className={`${card} min-w-0`} aria-label="Profile picture">
            <ProfilePictureEditor user={profile} onUserChange={setProfile} />
          </Card>
          <Card className={`${card} min-w-0`} aria-label="Profile details">
            <ProfileDetails user={profile} onUserChange={setProfile} />
          </Card>
        </div>
      )}
    </div>
  );
}
