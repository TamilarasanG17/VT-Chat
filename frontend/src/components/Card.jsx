export default function Card({ as: Tag = 'section', interactive = false, className = '', children, ...rest }) {
  return (
    <Tag
      className={`min-w-0 rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900 ${
        interactive
          ? 'transition-[box-shadow,border-color,transform] duration-200 hover:border-indigo-300 hover:shadow-md active:scale-[0.99] dark:hover:border-indigo-500/50'
          : ''
      } ${className}`}
      {...rest}
    >
      {children}
    </Tag>
  );
}
