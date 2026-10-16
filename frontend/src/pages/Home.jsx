import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import Avatar from '../components/profile/Avatar.jsx';
import Card from '../components/Card.jsx';
import { ChatIcon, PhoneIcon, SettingsIcon, ShieldIcon, SparklesIcon, UserIcon } from '../components/Icons.jsx';
import { useAuth } from '../hooks/useAuth';
import { userService } from '../services/userService';
import { EASE } from '../utils/motion';

const SHORTCUTS = [
  { to: '/chats', title: 'Chats', text: 'Search users and open conversations', Icon: ChatIcon },
  { to: '/calls', title: 'Calls', text: 'Your voice and video call history', Icon: PhoneIcon },
  { to: '/ai', title: 'AI Assistant', text: 'Ask questions and search your documents', Icon: SparklesIcon },
  { to: '/profile', title: 'Profile', text: 'Update your picture and username', Icon: UserIcon },
  { to: '/settings', title: 'Settings', text: 'Theme, privacy and password', Icon: SettingsIcon },
];

const list = { animate: { transition: { staggerChildren: 0.04 } } };
const item = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.2, ease: EASE } },
};

export default function Home() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(user);

  useEffect(() => {
    let active = true;
    userService
      .getMe()
      .then((res) => active && setProfile(res.data))
      .catch(() => {
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="mx-auto max-w-3xl">
      <Card className="relative overflow-hidden p-5 sm:p-8">
        <div aria-hidden="true" className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-indigo-500/10 blur-2xl" />
        <div className="relative flex flex-col items-center gap-4 text-center min-[480px]:flex-row min-[480px]:items-center min-[480px]:text-left">
          <Avatar src={profile?.profilePictureUrl} name={profile?.username} size="lg" className="ring-4 ring-indigo-100 dark:ring-indigo-500/20" />
          <div className="min-w-0">
            <p className="text-sm font-medium text-indigo-600 dark:text-indigo-300">Welcome back</p>
            <h1 className="mt-0.5 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl dark:text-white">{profile?.username}</h1>
            <p className="mt-2 flex items-start justify-center gap-1.5 text-sm text-slate-600 min-[480px]:justify-start dark:text-slate-400">
              <ShieldIcon className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
              <span>You&apos;re signed in securely. Find people and start a private conversation.</span>
            </p>
          </div>
        </div>
      </Card>

      <motion.ul variants={list} initial="initial" animate="animate" className="mt-4 grid gap-3 sm:mt-6 sm:grid-cols-2 sm:gap-4">
        {SHORTCUTS.map(({ to, title, text, Icon }, i) => (
          <motion.li key={to} variants={item} className={i === 0 ? 'sm:col-span-2' : ''}>
            <Link
              to={to}
              className="group block rounded-2xl focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30"
            >
              <Card as="div" interactive className="flex min-h-16 items-center gap-3 p-4">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 transition-colors group-hover:bg-indigo-100 dark:bg-indigo-500/15 dark:text-indigo-300 dark:group-hover:bg-indigo-500/25">
                  <Icon className="h-6 w-6" />
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-slate-900 dark:text-white">{title}</span>
                  <span className="block text-sm text-slate-500 dark:text-slate-400">{text}</span>
                </span>
              </Card>
            </Link>
          </motion.li>
        ))}
      </motion.ul>
    </div>
  );
}
