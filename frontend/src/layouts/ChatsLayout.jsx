import { Outlet, useParams } from 'react-router-dom';
import ChatsShell from '../components/chat/ChatsShell.jsx';

export default function ChatsLayout() {
  const { conversationId } = useParams();
  return <ChatsShell conversationId={conversationId}>{conversationId ? <Outlet /> : null}</ChatsShell>;
}
