import NotificationPanel from '../components/notifications/NotificationPanel.jsx';

export default function Notifications() {
  return (
    <>
      <div className="mx-auto max-w-2xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <NotificationPanel />
      </div>
    </>
  );
}
