import { CheckIcon } from '../Icons.jsx';

const LABEL = { SENT: 'Sent', DELIVERED: 'Delivered', READ: 'Read' };

export default function MessageStatus({ status = 'SENT' }) {
  const state = LABEL[status] ? status : 'SENT';
  const double = state !== 'SENT';
  const colour = state === 'READ' ? 'text-sky-300' : '';
  return (
    <span title={LABEL[state]} role="img" aria-label={LABEL[state]} className={`inline-flex items-center ${colour}`}>
      <CheckIcon className="h-3.5 w-3.5" />
      {double && <CheckIcon className="-ml-2 h-3.5 w-3.5" />}
    </span>
  );
}
