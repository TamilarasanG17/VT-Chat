import Spinner from './Spinner.jsx';

const VARIANTS = {
  primary: 'bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 focus-visible:ring-indigo-500/40',
  secondary:
    'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus-visible:ring-slate-400/40 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700',
  danger:
    'border border-red-200 bg-white text-red-600 hover:bg-red-50 focus-visible:ring-red-400/40 dark:border-red-500/40 dark:bg-transparent dark:text-red-400 dark:hover:bg-red-500/10',
};

export default function Button({
  children,
  loading = false,
  variant = 'primary',
  type = 'button',
  full = true,
  className = '',
  disabled = false,
  ...rest
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 min-h-11 rounded-xl px-4 py-2.5 text-base font-semibold transition-[background-color,box-shadow,transform,opacity] duration-150 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-4 disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100 ${
        full ? 'w-full' : ''
      } ${VARIANTS[variant]} ${className}`}
      {...rest}
    >
      {loading && <Spinner className="h-5 w-5" />}
      {children}
    </button>
  );
}
