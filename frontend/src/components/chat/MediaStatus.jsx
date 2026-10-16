import Spinner from '../Spinner.jsx';

export default function MediaStatus({ status, error, onRetry, className = 'h-40 w-56 max-w-full' }) {
  return (
    <div className={`flex ${className} max-w-full flex-col items-center justify-center gap-2 rounded-lg bg-black/10 p-3 text-center text-xs dark:bg-white/10`}>
      {status === 'error' ? (
        <>
          <span role="alert">{error || 'Unable to load this file'}</span>
          {onRetry && (
            <button type="button" onClick={onRetry} className="rounded-md px-2 py-1 font-semibold underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500">
              Try again
            </button>
          )}
        </>
      ) : (
        <Spinner className="h-5 w-5" />
      )}
    </div>
  );
}
