export default function Switch({ id, label, description, checked, disabled, onChange }) {
  return (
    <div className="flex min-h-12 items-start justify-between gap-3 py-3 min-[400px]:gap-4">
      <div className="min-w-0">
        <label htmlFor={id} className="block text-sm font-semibold text-slate-900 dark:text-white">{label}</label>
        <p className="text-xs text-slate-500 dark:text-slate-400">{description}</p>
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors duration-200 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:opacity-60 ${
          checked ? 'bg-indigo-600' : 'bg-slate-300 dark:bg-slate-600'
        }`}
      >
        <span className="sr-only">{checked ? 'On' : 'Off'}</span>
        <span className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform duration-200 ${checked ? 'translate-x-6' : 'translate-x-1'}`} />
      </button>
    </div>
  );
}
