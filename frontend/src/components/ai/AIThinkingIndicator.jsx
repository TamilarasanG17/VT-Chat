import { motion } from 'framer-motion';

export default function AIThinkingIndicator({ label = 'AI is thinking…', compact = false }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400" role="status">
      <span className="inline-flex items-end gap-1" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="h-1.5 w-1.5 rounded-full bg-indigo-500"
            animate={{ y: [0, -4, 0], opacity: [0.5, 1, 0.5] }}
            transition={{ duration: 0.9, repeat: Infinity, delay: i * 0.15 }}
          />
        ))}
      </span>
      {!compact && label}
      {compact && <span className="sr-only">{label}</span>}
    </span>
  );
}
