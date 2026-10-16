import { useEffect, useState } from 'react';
import { resolveAssetUrl } from '../../utils/assets';

const SIZES = {
  sm: 'h-9 w-9',
  md: 'h-12 w-12',
  lg: 'h-28 w-28 sm:h-32 sm:w-32',
};

export default function Avatar({ src, name = 'User', size = 'md', className = '' }) {
  const [failed, setFailed] = useState(false);
  const url = resolveAssetUrl(src);

  useEffect(() => {
    setFailed(false);
  }, [url]);

  const shared = `${SIZES[size]} shrink-0 rounded-full ${className}`;

  if (url && !failed) {
    return (
      <img
        src={url}
        alt={`${name}'s profile picture`}
        onError={() => setFailed(true)}
        className={`${shared} bg-slate-200 object-cover dark:bg-slate-700`}
      />
    );
  }

  return (
    <span
      role="img"
      aria-label="Default avatar"
      className={`${shared} inline-flex items-center justify-center overflow-hidden bg-linear-to-br from-indigo-400 to-violet-500 text-white`}
    >
      <svg viewBox="0 0 24 24" className="h-3/5 w-3/5 translate-y-[8%]" fill="currentColor" aria-hidden="true">
        <circle cx="12" cy="8" r="4.2" />
        <path d="M3.5 21c0-4.7 3.8-8 8.5-8s8.5 3.3 8.5 8v1h-17v-1Z" />
      </svg>
    </span>
  );
}
