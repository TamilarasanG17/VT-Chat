import { useTheme } from '../../hooks/useTheme';
import { ComputerIcon, MoonIcon, SunIcon } from '../Icons.jsx';

const OPTIONS = [
  { value: 'light', label: 'Light', description: 'Always light', Icon: SunIcon },
  { value: 'dark', label: 'Dark', description: 'Always dark', Icon: MoonIcon },
  { value: 'system', label: 'System', description: 'Match your device', Icon: ComputerIcon },
];

export default function ThemeSelector() {
  const { theme, setTheme } = useTheme();

  return (
    <div role="radiogroup" aria-label="Theme" className="grid grid-cols-1 gap-3 min-[425px]:grid-cols-3">
      {OPTIONS.map(({ value, label, description, Icon }) => {
        const selected = theme === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => setTheme(value)}
            className={`flex items-center gap-3 rounded-xl border p-3 text-left transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 min-[425px]:flex-col min-[425px]:items-start ${
              selected
                ? 'border-indigo-500 bg-indigo-50 dark:border-indigo-400 dark:bg-indigo-500/10'
                : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:hover:border-slate-600'
            }`}
          >
            <Icon
              className={`h-6 w-6 shrink-0 ${
                selected ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 dark:text-slate-400'
              }`}
            />
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-slate-900 dark:text-white">{label}</span>
              <span className="block text-xs text-slate-500 dark:text-slate-400">{description}</span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
