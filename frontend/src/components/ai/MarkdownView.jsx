import { memo } from 'react';
import { parseBlocks, parseInline } from '../../utils/markdown';
import CodeBlock from './CodeBlock.jsx';

function Inline({ nodes }) {
  return nodes.map((n, i) => {
    switch (n.type) {
      case 'code':
        return (
          <code key={i} className="rounded bg-slate-200/70 px-1 py-0.5 font-mono text-[0.85em] dark:bg-slate-700/70">
            {n.text}
          </code>
        );
      case 'bold':
        return <strong key={i}><Inline nodes={n.children} /></strong>;
      case 'italic':
        return <em key={i}><Inline nodes={n.children} /></em>;
      case 'link':
        return (
          <a key={i} href={n.href} target="_blank" rel="noopener noreferrer nofollow" className="break-anywhere font-medium text-indigo-600 underline underline-offset-2 dark:text-indigo-300">
            <Inline nodes={n.children} />
          </a>
        );
      default:
        return <span key={i}>{n.text}</span>;
    }
  });
}

const text = (s) => <Inline nodes={parseInline(s)} />;
const HEADINGS = ['text-xl', 'text-lg', 'text-base', 'text-base', 'text-sm', 'text-sm'];

function MarkdownView({ source }) {
  const blocks = parseBlocks(source);
  return (
    <div className="break-anywhere min-w-0 space-y-3 text-[0.95rem] leading-relaxed">
      {blocks.map((b, i) => {
        switch (b.type) {
          case 'code':
            return <CodeBlock key={i} lang={b.lang} text={b.text} />;
          case 'heading':
            return (
              <p key={i} role="heading" aria-level={b.level} className={`${HEADINGS[b.level - 1]} font-bold`}>
                {text(b.text)}
              </p>
            );
          case 'ul':
            return (
              <ul key={i} className="list-disc space-y-1 pl-6">
                {b.items.map((it, j) => <li key={j} className="whitespace-pre-line">{text(it)}</li>)}
              </ul>
            );
          case 'ol':
            return (
              <ol key={i} start={b.start} className="list-decimal space-y-1 pl-6">
                {b.items.map((it, j) => <li key={j} className="whitespace-pre-line">{text(it)}</li>)}
              </ol>
            );
          case 'quote':
            return (
              <blockquote key={i} className="whitespace-pre-line border-l-4 border-slate-300 pl-3 text-slate-600 dark:border-slate-600 dark:text-slate-300">
                {text(b.text)}
              </blockquote>
            );
          case 'hr':
            return <hr key={i} className="border-slate-200 dark:border-slate-700" />;
          case 'table':
            return (
              <div key={i} className="overflow-x-auto">
                <table className="min-w-full border-collapse text-sm">
                  <thead>
                    <tr>{b.head.map((h, j) => <th key={j} className="border border-slate-300 bg-slate-100 px-2 py-1 text-left font-semibold dark:border-slate-600 dark:bg-slate-800">{text(h)}</th>)}</tr>
                  </thead>
                  <tbody>
                    {b.rows.map((r, j) => (
                      <tr key={j}>{r.map((c, k) => <td key={k} className="border border-slate-300 px-2 py-1 dark:border-slate-600">{text(c)}</td>)}</tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          default:
            return <p key={i} className="whitespace-pre-line">{text(b.text)}</p>;
        }
      })}
    </div>
  );
}

export default memo(MarkdownView);
