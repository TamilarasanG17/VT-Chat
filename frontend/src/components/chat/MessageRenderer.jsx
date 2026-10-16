import CallMessage from './CallMessage.jsx';
import FileMessage from './FileMessage.jsx';
import ImageMessage from './ImageMessage.jsx';
import VideoMessage from './VideoMessage.jsx';
import VoiceMessage from './VoiceMessage.jsx';

export default function MessageRenderer({ message, mine, selectionMode, currentUserId }) {
  const caption = message.content ? (
    <p className="mt-1.5 whitespace-pre-wrap break-anywhere text-[15px] leading-snug">{message.content}</p>
  ) : null;

  switch (message.messageType) {
    case 'IMAGE':
      return (
        <>
          <ImageMessage message={message} disabled={selectionMode} />
          {caption}
        </>
      );
    case 'VIDEO':
      return (
        <>
          <VideoMessage message={message} disabled={selectionMode} />
          {caption}
        </>
      );
    case 'FILE':
      return (
        <>
          <FileMessage message={message} mine={mine} disabled={selectionMode} currentUserId={currentUserId} />
          {caption}
        </>
      );
    case 'VOICE':
      return <VoiceMessage message={message} mine={mine} disabled={selectionMode} />;
    case 'VOICE_CALL':
    case 'VIDEO_CALL':
      return <CallMessage message={message} currentUserId={currentUserId} disabled={selectionMode} />;
    default:
      return <p className="whitespace-pre-wrap break-anywhere text-[15px] leading-snug">{message.content}</p>;
  }
}
