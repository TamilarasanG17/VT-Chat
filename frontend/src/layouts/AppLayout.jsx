import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import Logo from '../components/Logo.jsx';
import NotificationBell from '../components/notifications/NotificationBell.jsx';
import Avatar from '../components/profile/Avatar.jsx';
import { ChatIcon, PhoneIcon, HomeIcon, LogoutIcon, SettingsIcon, SparklesIcon, UserIcon } from '../components/Icons.jsx';
import { useAuth } from '../hooks/useAuth';
import { confirmLogout, toastInfo, toastSuccess } from '../utils/alerts';
import { websocketService } from '../services/websocketService';
import { dropdownVariants, pageVariants } from '../utils/motion';

const NAV_ITEMS = [
  { to: '/home', label: 'Home', Icon: HomeIcon },
  { to: '/chats', label: 'Chats', Icon: ChatIcon, also: '/chat/' },
  { to: '/calls', label: 'Calls', Icon: PhoneIcon },
  { to: '/ai', label: 'AI', Icon: SparklesIcon },
  { to: '/profile', label: 'Profile', Icon: UserIcon },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
];

const isFullBleed = (path) => path === '/chats' || path.startsWith('/chat/') || path === '/ai';
const isOpenConversation = (path) => path.startsWith('/chat/');

const ring = 'focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30';

function isActive(item, pathname) {
  return pathname === item.to || pathname.startsWith(`${item.to}/`) || (item.also && pathname.startsWith(item.also));
}

function TopNav({ pathname }) {
  return (
    <nav aria-label="Main" className="hidden items-center gap-1 sm:flex">
      {NAV_ITEMS.map((item) => {
        const active = isActive(item, pathname);
        return (
          <NavLink
            key={item.to}
            to={item.to}
            title={item.label}
            aria-label={item.label}
            aria-current={active ? 'page' : undefined}
            className={`relative flex h-10 items-center gap-2 rounded-xl px-2.5 text-sm font-semibold transition-colors lg:px-3.5 ${ring} ${
              active
                ? 'text-indigo-700 dark:text-indigo-300'
                : 'text-slate-600 hover:bg-slate-100 active:bg-slate-200 dark:text-slate-300 dark:hover:bg-slate-800 dark:active:bg-slate-700'
            }`}
          >
            {active && (
              <motion.span
                layoutId="top-nav-active"
                className="absolute inset-0 rounded-xl bg-indigo-50 dark:bg-indigo-500/15"
                transition={{ type: 'spring', stiffness: 500, damping: 38 }}
              />
            )}
            <item.Icon className="relative h-5 w-5 shrink-0" />
            <span className="relative hidden lg:inline">{item.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
}

function BottomNav({ pathname }) {
  return (
    <nav aria-label="Main" className="pb-safe pl-safe pr-safe shrink-0 border-t border-slate-200 bg-white sm:hidden dark:border-slate-800 dark:bg-slate-900">
      <ul className="grid grid-cols-6">
        {NAV_ITEMS.map((item) => {
          const active = isActive(item, pathname);
          return (
            <li key={item.to} className="min-w-0">
              <NavLink
                to={item.to}
                aria-current={active ? 'page' : undefined}
                className={`relative flex h-14 flex-col items-center justify-center gap-0.5 px-0.5 text-[10px] font-semibold transition-colors ${ring} focus-visible:ring-inset ${
                  active ? 'text-indigo-700 dark:text-indigo-300' : 'text-slate-500 active:bg-slate-100 dark:text-slate-400 dark:active:bg-slate-800'
                }`}
              >
                {active && (
                  <motion.span
                    layoutId="bottom-nav-active"
                    className="absolute inset-x-2 top-0 h-0.5 rounded-b bg-indigo-600 dark:bg-indigo-400"
                    transition={{ type: 'spring', stiffness: 500, damping: 38 }}
                  />
                )}
                <item.Icon className="h-5 w-5" />
                <span className="max-w-full truncate">{item.label}</span>
              </NavLink>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function ProfileMenu({ user, onLogout }) {
  const [open, setOpen] = useState(false);
  const box = useRef(null);
  const { pathname } = useLocation();

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => box.current && !box.current.contains(e.target) && setOpen(false);
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const item = `flex w-full items-center gap-2.5 px-3 py-2.5 text-left text-sm font-medium focus-visible:outline-none focus-visible:bg-slate-100 dark:focus-visible:bg-slate-700`;

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Account menu"
        aria-haspopup="menu"
        aria-expanded={open}
        className={`flex items-center gap-2 rounded-full p-1 transition-colors hover:bg-slate-100 md:pr-3 dark:hover:bg-slate-800 ${ring}`}
      >
        <Avatar src={user?.profilePictureUrl} name={user?.username} size="sm" />
        <span className="hidden max-w-[9rem] truncate text-sm font-semibold text-slate-700 md:block dark:text-slate-200">{user?.username}</span>
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            variants={dropdownVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            style={{ transformOrigin: 'top right' }}
            className="absolute right-0 top-full z-40 mt-2 w-56 max-w-[calc(100vw-1rem)] overflow-hidden rounded-2xl border border-slate-200 bg-white py-1 shadow-xl dark:border-slate-700 dark:bg-slate-800"
          >
            <div className="border-b border-slate-100 px-3 py-2.5 dark:border-slate-700">
              <p className="truncate text-sm font-semibold text-slate-900 dark:text-white">{user?.username}</p>
              {user?.email && <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user.email}</p>}
            </div>
            <Link to="/profile" role="menuitem" className={`${item} text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700/60`}>
              <UserIcon className="h-4 w-4" /> Profile
            </Link>
            <Link to="/settings" role="menuitem" className={`${item} text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700/60`}>
              <SettingsIcon className="h-4 w-4" /> Settings
            </Link>
            <button type="button" role="menuitem" onClick={onLogout} className={`${item} text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10`}>
              <LogoutIcon className="h-4 w-4" /> Log out
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const full = isFullBleed(pathname);
  const hideBottom = isOpenConversation(pathname);

  const seenRemoval = useRef(null);
  useEffect(
    () =>
      websocketService.onUserEvent((event) => {
        if (event?.type !== 'FRIEND_REMOVED' || event.byMe) return;
        const key = `${event.otherUserId}:${event.at}`;
        if (seenRemoval.current === key) return;
        seenRemoval.current = key;
        toastInfo('Your connection with this user has ended. You can no longer send messages until a new chat request is accepted.');
      }),
    [],
  );
  const pageKey = pathname.startsWith('/chat') ? 'chats' : pathname;

  const handleLogout = async () => {
    if (!(await confirmLogout())) return;
    await logout();
    toastSuccess('You have been logged out');
  };

  return (
    <div className="pt-safe flex h-dvh min-w-0 flex-col overflow-hidden bg-slate-50 dark:bg-slate-950">
      <header className="pl-safe pr-safe relative z-30 shrink-0 border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto flex h-14 w-full max-w-7xl items-center justify-between gap-2 px-2 min-[360px]:px-3 sm:px-4">
          <Link to="/home" aria-label="VTChat home" className={`min-w-0 shrink-0 rounded-xl ${ring}`}>
            <Logo compact />
          </Link>

          <TopNav pathname={pathname} />

          <div className="flex min-w-0 shrink-0 items-center gap-0.5 min-[360px]:gap-1">
            <NotificationBell />
            <ProfileMenu user={user} onLogout={handleLogout} />
          </div>
        </div>
      </header>

      <div className="relative min-h-0 flex-1">
        <motion.main
          key={pageKey}
          variants={pageVariants}
          initial="initial"
          animate="animate"
          className={`pl-safe pr-safe absolute inset-0 min-w-0 ${full ? 'overflow-hidden' : 'overflow-y-auto overscroll-contain'}`}
        >
          {full ? (
            <div className="mx-auto h-full w-full max-w-7xl min-w-0 md:p-4">
              <Outlet />
            </div>
          ) : (
            <div className="mx-auto w-full max-w-5xl min-w-0 px-3 py-5 min-[480px]:px-4 sm:py-8">
              <Outlet />
            </div>
          )}
        </motion.main>
      </div>

      {!hideBottom && <BottomNav pathname={pathname} />}
    </div>
  );
}
