import { useEffect, useRef, useState } from 'react';
import { copyText } from '../../utils/clipboard';
import { CheckIcon, CopyIcon } from '../Icons.jsx';

export default function CodeBlock({ lang, text }) {
  const [copied, setCopied] = useState(false);
  const timer = useRef(0);

  useEffect(() => () => clearTimeout(timer.current), []);

  const copy = async () => {
    if (await copyText(text)) {
      setCopied(true);
      clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopied(false), 1500);
    }
  };

  return (
    <div className="my-3 max-w-full overflow-hidden rounded-xl border border-slate-200 bg-slate-900 text-slate-100 dark:border-slate-700">
      <div className="flex items-center justify-between bg-slate-800 px-3 py-1.5 text-xs">
        <span className="font-medium uppercase tracking-wide text-slate-300">{lang || 'code'}</span>
        <button
          type="button"
          onClick={copy}
          aria-label={copied ? 'Copied' : 'Copy code'}
          className="inline-flex min-h-8 items-center gap-1.5 rounded-md px-2 py-1 font-semibold text-slate-200 transition hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
        >
          {copied ? <CheckIcon className="h-3.5 w-3.5 text-emerald-400" /> : <CopyIcon className="h-3.5 w-3.5" />}
          <span aria-live="polite">{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className="overflow-x-auto p-3 text-sm leading-relaxed">
        <code>{text}</code>
      </pre>
    </div>
  );
}
