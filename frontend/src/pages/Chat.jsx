import { useParams } from 'react-router-dom';
import ChatPanel from '../components/chat/ChatPanel.jsx';

export default function Chat() {
  const { conversationId } = useParams();
  return <ChatPanel key={conversationId} conversationId={conversationId} />;
}
