export default function EmptyState({ Icon, title, hint, action, className = '' }) {
  return (
    <div className={`flex flex-col items-center justify-center px-6 py-10 text-center ${className}`}>
      {Icon && (
        <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-indigo-50 text-indigo-500 dark:bg-indigo-500/10 dark:text-indigo-300">
          <Icon className="h-7 w-7" />
        </span>
      )}
      <p className="font-semibold text-slate-800 dark:text-slate-100">{title}</p>
      {hint && <p className="mt-1 max-w-xs text-sm text-slate-500 dark:text-slate-400">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
