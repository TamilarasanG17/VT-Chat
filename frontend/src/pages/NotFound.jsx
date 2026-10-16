import { Link } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';

export default function NotFound() {
  return (
    <AuthLayout title="Page not found" subtitle="The page you're looking for doesn't exist.">
      <Link
        to="/home"
        className="inline-flex w-full items-center justify-center rounded-xl bg-indigo-600 px-4 py-2.5 text-base font-semibold text-white shadow-sm transition hover:bg-indigo-700"
      >
        Go to Home
      </Link>
    </AuthLayout>
  );
}
