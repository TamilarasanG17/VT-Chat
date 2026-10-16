import { LibraryIcon, SparklesIcon } from '../Icons.jsx';
import { MODE_NORMAL, MODE_RAG } from '../../utils/ai';

const options = [
  { value: MODE_NORMAL, label: 'Normal AI', Icon: SparklesIcon },
  { value: MODE_RAG, label: 'My Knowledge', Icon: LibraryIcon },
];

export default function AIModeSwitch({ mode, onChange, disabled }) {
  return (
    <div role="radiogroup" aria-label="Answer mode" className="inline-flex max-w-full rounded-xl bg-slate-100 p-1 dark:bg-slate-800">
      {options.map(({ value, label, Icon }) => {
        const active = mode === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            disabled={disabled && !active}
            onClick={() => onChange(value)}
            title={disabled && !active ? 'Wait for the answer to finish, or press Stop' : undefined}
            className={`inline-flex min-h-8 items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50 ${
              active
                ? 'bg-white text-indigo-700 shadow-sm dark:bg-slate-700 dark:text-indigo-300'
                : 'text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white'
            }`}
          >
            <Icon className="h-3.5 w-3.5" />
            {label}
          </button>
        );
      })}
    </div>
  );
}
