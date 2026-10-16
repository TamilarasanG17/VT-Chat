import { useCallback, useEffect, useRef, useState } from 'react';
import Avatar from '../profile/Avatar.jsx';
import EmptyState from '../EmptyState.jsx';
import { PhoneIcon, VideoIcon } from '../Icons.jsx';
import { useCall } from '../../hooks/useCall';
import { isTerminal } from '../../utils/callState';
import { describeCallItem, formatCallTime } from '../../utils/calls';
import { getChatErrorMessage } from '../../utils/errors';

const PAGE_SIZE = 20;

export default function CallHistoryList({ loadPage, emptyText = 'No calls yet' }) {
  const { call, startCall, inCall } = useCall();
  const [state, setState] = useState({ status: 'loading', items: [], page: 0, hasNext: false, error: '' });
  const request = useRef(0);

  const load = useCallback(
    (page, append) => {
      const id = ++request.current;
      setState((s) => ({ ...s, status: append ? 'more' : s.items.length ? 'refreshing' : 'loading', error: '' }));
      loadPage(page, PAGE_SIZE)
        .then((res) => {
          if (id !== request.current) return;
          const data = res.data ?? {};
          setState((s) => ({
            status: 'done',
            items: append ? [...s.items, ...(data.items ?? []).filter((n) => !s.items.some((o) => o.callId === n.callId))] : data.items ?? [],
            page,
            hasNext: !!data.hasNext,
            error: '',
          }));
        })
        .catch((err) => {
          if (id === request.current) setState((s) => ({ ...s, status: 'error', error: getChatErrorMessage(err, 'Unable to load call history') }));
        });
    },
    [loadPage],
  );

  useEffect(() => {
    load(0, false);
    return () => {
      request.current += 1;
    };
  }, [load]);

  const finished = isTerminal(call.phase) ? call.callId : null;
  useEffect(() => {
    if (!finished) return undefined;
    const t = setTimeout(() => load(0, false), 900);
    return () => clearTimeout(t);
  }, [finished, load]);

  const note = 'px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400';
  if (state.status === 'loading') {
    return (
      <div role="status" aria-label="Loading calls" className="divide-y divide-slate-100 dark:divide-slate-800">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex animate-pulse items-center gap-3 px-3 py-3 min-[480px]:px-4">
            <div className="h-12 w-12 shrink-0 rounded-full bg-slate-200 dark:bg-slate-700" />
            <div className="min-w-0 flex-1 space-y-2">
              <div className="h-3.5 w-1/3 rounded bg-slate-200 dark:bg-slate-700" />
              <div className="h-3 w-1/2 rounded bg-slate-200 dark:bg-slate-700" />
            </div>
          </div>
        ))}
        <span className="sr-only">Loading calls…</span>
      </div>
    );
  }
  if (state.status === 'error' && state.items.length === 0) {
    return (
      <div className={note}>
        <p role="alert">{state.error}</p>
        <button type="button" onClick={() => load(0, false)} className="mt-2 font-semibold text-indigo-600 hover:underline dark:text-indigo-400">
          Try again
        </button>
      </div>
    );
  }
  if (state.items.length === 0) return <EmptyState Icon={PhoneIcon} title="No calls yet" hint={emptyText.replace(/^No calls yet\.?\s*/, '') || undefined} />;

  return (
    <div>
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {state.items.map((item) => {
          const d = describeCallItem(item);
          const when = formatCallTime(item.startedAt);
          return (
            <li key={item.callId} className="flex items-center gap-3 px-3 py-3 transition-colors hover:bg-slate-50 min-[480px]:px-4 dark:hover:bg-slate-800/50">
              <span className="relative shrink-0">
                <Avatar src={item.participant?.profilePictureUrl} name={item.participant?.username} size="md" />
                <span
                  aria-hidden="true"
                  className={`absolute -bottom-0.5 -right-0.5 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white text-white dark:border-slate-900 ${
                    d.missed ? 'bg-red-500' : 'bg-indigo-600'
                  }`}
                >
                  {d.video ? <VideoIcon className="h-3 w-3" /> : <PhoneIcon className="h-3 w-3" />}
                </span>
              </span>
              <div className="min-w-0 flex-1">
                <p className={`truncate font-semibold ${d.missed ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white'}`}>
                  {item.participant?.username}
                </p>
                <p className="truncate text-sm text-slate-600 dark:text-slate-300">
                  <span aria-hidden="true">{d.direction === 'Outgoing' ? '↗' : '↙'} </span>
                  {d.direction} · {d.statusLabel}
                </p>
                <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                  {when}
                  {d.duration && <span className="tabular-nums"> · {d.duration}</span>}
                </p>
              </div>
              <button
                type="button"
                disabled={inCall}
                onClick={() => startCall(item.conversationId, item.participant, d.video ? 'VIDEO' : 'VOICE')}
                aria-label={`${d.video ? 'Video call' : 'Call back'} ${item.participant?.username ?? ''}`}
                title={inCall ? 'You are already in a call' : d.video ? 'Video call' : 'Call back'}
                className="inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 rounded-xl border border-slate-300 px-3 text-sm font-semibold text-slate-700 transition-[background-color,transform] hover:bg-slate-50 active:scale-95 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {d.video ? <VideoIcon className="h-5 w-5" /> : <PhoneIcon className="h-5 w-5" />}
                <span className="hidden sm:inline">{d.video ? 'Video call' : 'Call back'}</span>
              </button>
            </li>
          );
        })}
      </ul>
      {state.error && <p className={`${note} text-red-600 dark:text-red-400`} role="alert">{state.error}</p>}
      {state.hasNext && (
        <div className="p-3 text-center">
          <button
            type="button"
            disabled={state.status === 'more'}
            onClick={() => load(state.page + 1, true)}
            className="rounded-lg px-4 py-2 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50 disabled:opacity-60 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
          >
            {state.status === 'more' ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  );
}
