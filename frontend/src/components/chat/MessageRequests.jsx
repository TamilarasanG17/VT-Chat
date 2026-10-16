import { useRef, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import Button from '../Button.jsx';
import EmptyState from '../EmptyState.jsx';
import Spinner from '../Spinner.jsx';
import { ChatIcon } from '../Icons.jsx';
import ChatRequestCard from './ChatRequestCard.jsx';
import { chatRequestService } from '../../services/chatRequestService';
import { getChatErrorMessage } from '../../utils/errors';
import { showError, toastSuccess } from '../../utils/alerts';

export default function MessageRequests({ requests, onChanged }) {
  const navigate = useNavigate();
  const inFlight = useRef(new Set());
  const [busy, setBusy] = useState({});

  const answer = async (request, kind) => {
    if (inFlight.current.has(request.id)) return;
    inFlight.current.add(request.id);
    setBusy((b) => ({ ...b, [request.id]: kind }));
    try {
      const res = kind === 'accept' ? await chatRequestService.accept(request.id) : await chatRequestService.decline(request.id);
      requests.remove(request.id);
      onChanged?.();
      if (kind === 'accept') {
        toastSuccess(`You can now chat with ${request.sender.username}`);
        const id = res.data?.conversation?.id;
        if (id) navigate(`/chat/${id}`);
      } else {
        toastSuccess('Request declined');
      }
    } catch (err) {
      showError(kind === 'accept' ? 'Could not accept' : 'Could not decline', getChatErrorMessage(err, 'Something went wrong. Please try again.'));
      requests.reload({ quiet: true });
    } finally {
      inFlight.current.delete(request.id);
      setBusy((b) => {
        const next = { ...b };
        delete next[request.id];
        return next;
      });
    }
  };

  if (requests.status === 'loading') {
    return (
      <div className="flex justify-center py-10" role="status" aria-label="Loading message requests">
        <Spinner className="h-6 w-6 text-indigo-600" />
      </div>
    );
  }
  if (requests.status === 'error' && requests.items.length === 0) {
    return (
      <div className="px-4 py-8 text-center" role="alert">
        <p className="text-sm text-slate-600 dark:text-slate-300">{requests.error}</p>
        <Button onClick={() => requests.reload()} full={false} className="mt-3">
          Try again
        </Button>
      </div>
    );
  }
  if (requests.items.length === 0) {
    return <EmptyState Icon={ChatIcon} title="No message requests" hint="When someone asks to chat with you, it shows up here." className="py-10" />;
  }
  return (
    <ul aria-label="Message requests">
      <AnimatePresence initial={false}>
        {requests.items.map((r) => (
          <ChatRequestCard key={r.id} request={r} busy={busy[r.id]} onAccept={(x) => answer(x, 'accept')} onDecline={(x) => answer(x, 'decline')} />
        ))}
      </AnimatePresence>
    </ul>
  );
}
