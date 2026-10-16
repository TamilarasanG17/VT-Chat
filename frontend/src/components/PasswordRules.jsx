import { motion } from 'framer-motion';
import { getPasswordRules } from '../utils/validators';
import { CheckIcon, DotIcon } from './Icons.jsx';

export default function PasswordRules({ password }) {
  if (!password) return null;
  const rules = getPasswordRules(password);

  return (
    <motion.ul
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      className="mt-2 grid grid-cols-1 gap-x-4 gap-y-1 overflow-hidden text-sm min-[425px]:grid-cols-2"
      aria-label="Password requirements"
    >
      {rules.map((rule) => (
        <li
          key={rule.id}
          className={`flex items-center gap-1.5 ${
            rule.met ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          {rule.met ? <CheckIcon className="h-4 w-4 shrink-0" /> : <DotIcon className="h-4 w-4 shrink-0" />}
          <span>{rule.label}</span>
        </li>
      ))}
    </motion.ul>
  );
}
