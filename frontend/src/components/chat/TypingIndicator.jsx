export default function TypingIndicator({ name }) {
  return (
    <p role="status" aria-live="polite" className="flex items-center gap-1.5 px-4 pb-1 text-xs text-slate-500 dark:text-slate-400">
      <span>{name ?? 'They'} is typing</span>
      <span className="inline-flex items-end gap-0.5" aria-hidden="true">
        {[0, 150, 300].map((delay) => (
          <span key={delay} className="h-1 w-1 rounded-full bg-slate-400 motion-safe:animate-bounce" style={{ animationDelay: `${delay}ms` }} />
        ))}
      </span>
    </p>
  );
}
