import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import Avatar from '../profile/Avatar.jsx';
import Button from '../Button.jsx';
import { CloseIcon } from '../Icons.jsx';
import PresenceStatus from '../PresenceStatus.jsx';
import { usePresence } from '../../hooks/usePresence';
import { chatRequestService, CHAT_REQUEST_EVENTS, REL } from '../../services/chatRequestService';
import { websocketService } from '../../services/websocketService';
import { getChatErrorMessage } from '../../utils/errors';
import { confirmAction, showError, toastSuccess } from '../../utils/alerts';

export default function UserPreviewModal({ user, isSelf = false, hideEmail = false, onOpenChat, onChanged, onClose }) {
  const dialogRef = useRef(null);
  const inFlight = useRef(false);
  const presence = usePresence(user?.id, { fetch: !!user && !isSelf });
  const [rel, setRel] = useState({ status: 'loading', data: null });
  const [busy, setBusy] = useState(null);

  const userId = user?.id;
  const loadStatus = useCallback(() => {
    if (!userId || isSelf) return undefined;
    let active = true;
    chatRequestService
      .status(userId)
      .then((res) => active && setRel({ status: 'ready', data: res.data }))
      .catch((err) => active && setRel({ status: 'error', data: null, error: getChatErrorMessage(err, 'Could not check chat status') }));
    return () => {
      active = false;
    };
  }, [userId, isSelf]);

  useEffect(() => {
    setRel({ status: 'loading', data: null });
    return loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    if (!userId) return undefined;
    return websocketService.onUserEvent((event) => {
      if (CHAT_REQUEST_EVENTS.includes(event?.type)) loadStatus();
    });
  }, [userId, loadStatus]);

  const starting = !!busy;
  const relationship = rel.data?.relationship;

  const run = async (kind, call) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(kind);
    try {
      const res = await call();
      setRel({ status: 'ready', data: res.data.relationship });
      onChanged?.();
      if (kind === 'accept') {
        toastSuccess(`You can now chat with ${user.username}`);
        if (res.data.conversation?.id) onOpenChat(res.data.conversation.id);
      } else if (kind === 'request') {
        if (res.data.relationship?.relationship === REL.CONNECTED && res.data.relationship.conversationId) {
          onOpenChat(res.data.relationship.conversationId);
        } else if (res.data.relationship?.relationship === REL.OUTGOING) {
          toastSuccess('Chat request sent');
        }
      }
    } catch (err) {
      await showError('Something went wrong', getChatErrorMessage(err, 'Please try again.'));
      loadStatus();
    } finally {
      inFlight.current = false;
      setBusy(null);
    }
  };

  const requestId = rel.data?.requestId;

  const unfriend = async () => {
    if (inFlight.current) return;
    const ok = await confirmAction({
      title: `Remove ${user.username}?`,
      text: 'You will both stop being able to send messages. Your chat history is kept, and you can send a new chat request later.',
      confirmText: 'Confirm Unfriend',
      cancelText: 'Cancel',
      icon: 'warning',
    });
    if (!ok) return;
    await run('unfriend', async () => {
      const res = await chatRequestService.unfriend(userId);
      toastSuccess(`You are no longer connected with ${user.username}`);
      return { data: { relationship: res.data } };
    });
  };

  let action = null;
  if (isSelf) {
    action = null;
  } else if (rel.status === 'loading') {
    action = <Button full loading disabled>Checking…</Button>;
  } else if (rel.status === 'error') {
    action = (
      <div role="alert">
        <p className="mb-2 text-sm text-red-600 dark:text-red-400">{rel.error}</p>
        <Button full variant="secondary" onClick={() => { setRel({ status: 'loading', data: null }); loadStatus(); }}>Try again</Button>
      </div>
    );
  } else if (relationship === REL.CONNECTED) {
    action = (
      <div className="flex flex-col gap-2 min-[360px]:flex-row">
        <Button full disabled={starting} onClick={() => onOpenChat(rel.data.conversationId)}>Message</Button>
        <Button full variant="secondary" loading={busy === 'unfriend'} disabled={starting} onClick={unfriend}>Unfriend</Button>
      </div>
    );
  } else if (relationship === REL.OUTGOING) {
    action = <Button full variant="secondary" disabled>Request Sent</Button>;
  } else if (relationship === REL.INCOMING) {
    action = (
      <div className="flex flex-col gap-2 min-[360px]:flex-row">
        <Button full loading={busy === 'accept'} disabled={starting} onClick={() => run('accept', () => chatRequestService.accept(requestId))}>Accept</Button>
        <Button full variant="secondary" loading={busy === 'decline'} disabled={starting} onClick={() => run('decline', () => chatRequestService.decline(requestId))}>Decline</Button>
      </div>
    );
  } else if (relationship === REL.DECLINED) {
    action = (
      <div>
        <Button full variant="secondary" disabled>Request Declined</Button>
        {rel.data.retryAt && <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">You can ask again after {new Date(rel.data.retryAt).toLocaleString()}.</p>}
      </div>
    );
  } else {
    action = <Button full loading={busy === 'request'} disabled={starting} onClick={() => run('request', () => chatRequestService.send(userId))}>Request to Chat</Button>;
  }

  useEffect(() => {
    if (!user) return undefined;
    const onKey = (e) => e.key === 'Escape' && !starting && onClose();
    document.addEventListener('keydown', onKey);
    dialogRef.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [user, starting, onClose]);

  return (
    <AnimatePresence>
      {user && (
        <motion.div
          className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-3 min-[400px]:p-4 sm:items-center"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onMouseDown={(e) => e.target === e.currentTarget && !starting && onClose()}
        >
          <motion.div
            ref={dialogRef}
            tabIndex={-1}
            role="dialog"
            aria-modal="true"
            aria-label={`${user.username}'s profile`}
            initial={{ opacity: 0, y: 24, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.97 }}
            transition={{ duration: 0.18 }}
            className="relative w-full max-w-sm max-h-[calc(100dvh-1.5rem)] overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 text-center min-[400px]:p-6 shadow-xl outline-none dark:border-slate-700 dark:bg-slate-900"
          >
            <button
              type="button"
              onClick={onClose}
              disabled={starting}
              aria-label="Close"
              className="absolute right-2 top-2 flex h-10 w-10 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 dark:text-slate-400 dark:hover:bg-slate-800"
            >
              <CloseIcon className="h-5 w-5" />
            </button>

            <div className="flex justify-center">
              <Avatar src={user.profilePictureUrl} name={user.username} size="lg" />
            </div>
            <h2 className="mt-4 break-words text-xl font-bold text-slate-900 dark:text-white">{user.username}</h2>
            {!hideEmail && <p className="mt-1 break-all text-sm text-slate-500 dark:text-slate-400">{user.email}</p>}
            {user.bio && <p className="mx-auto mt-2 max-w-xs whitespace-pre-line break-words text-sm text-slate-600 dark:text-slate-300">{user.bio}</p>}
            {!isSelf && (
              <div className="mt-1.5 min-h-4">
                <PresenceStatus presence={presence} />
              </div>
            )}

            {isSelf && (
              <p className="mt-3 text-sm text-amber-600 dark:text-amber-400">You cannot chat with yourself.</p>
            )}

            <div className="mt-6">
              {action}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
