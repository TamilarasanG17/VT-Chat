export default function Logo({ className = '', compact = false }) {
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <span
        className={`flex shrink-0 items-center justify-center rounded-xl bg-indigo-600 shadow-md shadow-indigo-600/30 ${
          compact ? 'h-9 w-9' : 'h-10 w-10'
        }`}
      >
        <svg viewBox="0 0 24 24" className={compact ? 'h-5 w-5 text-white' : 'h-6 w-6 text-white'} fill="currentColor" aria-hidden="true">
          <path d="M6 5h12a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-6l-4 3.5V17H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Z" />
        </svg>
      </span>
      <span className={`font-bold tracking-tight text-slate-900 dark:text-white ${compact ? 'text-xl max-[339px]:sr-only' : 'text-2xl'}`}>VTChat</span>
    </div>
  );
}
