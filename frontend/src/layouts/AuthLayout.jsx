import { motion } from 'framer-motion';
import Logo from '../components/Logo.jsx';

export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="pt-safe pb-safe pl-safe pr-safe flex min-h-dvh w-full items-center justify-center bg-linear-to-br from-indigo-50 via-white to-violet-100 px-3 py-6 min-[400px]:px-4 min-[400px]:py-8 dark:from-slate-950 dark:via-slate-950 dark:to-indigo-950">
      <motion.main
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -8 }}
        transition={{ duration: 0.22, ease: 'easeOut' }}
        className="w-full min-w-0 max-w-md"
      >
        <div className="mb-5 flex justify-center min-[400px]:mb-6">
          <Logo />
        </div>

        <div className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-xl min-[400px]:p-5 shadow-indigo-900/5 sm:p-8 dark:border-slate-800 dark:bg-slate-900 dark:shadow-black/30">
          <h1 className="break-words text-xl font-bold tracking-tight text-slate-900 min-[360px]:text-2xl dark:text-white">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>

        {footer && <div className="mt-5 text-center text-sm text-slate-600 dark:text-slate-400">{footer}</div>}
      </motion.main>
    </div>
  );
}
