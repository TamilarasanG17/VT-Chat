import Spinner from './Spinner.jsx';

export default function FullScreenLoader() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-slate-50 dark:bg-slate-950" role="status" aria-label="Loading">
      <Spinner className="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
    </div>
  );
}
