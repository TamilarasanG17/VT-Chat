const STATES = {
  connected: { label: 'Connected', dot: 'bg-emerald-500', text: 'text-emerald-700 dark:text-emerald-400' },
  connecting: { label: 'Connecting…', dot: 'bg-amber-500 animate-pulse', text: 'text-amber-700 dark:text-amber-400' },
  reconnecting: { label: 'Reconnecting…', dot: 'bg-amber-500 animate-pulse', text: 'text-amber-700 dark:text-amber-400' },
  disconnected: { label: 'Disconnected', dot: 'bg-red-500', text: 'text-red-700 dark:text-red-400' },
};

export default function ConnectionStatus({ status, className = '' }) {
  const state = STATES[status] ?? STATES.disconnected;
  return (
    <span role="status" className={`inline-flex items-center gap-1.5 text-xs font-medium ${state.text} ${className}`}>
      <span className={`h-2 w-2 rounded-full ${state.dot}`} aria-hidden="true" />
      {state.label}
    </span>
  );
}
