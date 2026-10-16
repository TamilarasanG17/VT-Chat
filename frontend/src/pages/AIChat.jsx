import { useCallback, useState } from 'react';
import { motion } from 'framer-motion';
import { panelSwitchVariants } from '../utils/motion';
import AIChatWindow from '../components/ai/AIChatWindow.jsx';
import AIConversationList from '../components/ai/AIConversationList.jsx';
import KnowledgePanel from '../components/ai/KnowledgePanel.jsx';
import { useKnowledge } from '../hooks/useKnowledge';
import { useAIChat } from '../hooks/useAIChat';
import { confirmAction, toastError, toastSuccess } from '../utils/alerts';

export default function AIChat() {
  const ai = useAIChat();
  const knowledge = useKnowledge();
  const [tab, setTab] = useState('chats');
  const [showList, setShowList] = useState(true);

  const active = ai.conversations.find((c) => c.id === ai.activeId);

  const open = (id) => {
    ai.open(id);
    setShowList(false);
  };
  const create = () => {
    ai.newChat();
    setShowList(false);
  };
  const remove = async (c) => {
    const ok = await confirmAction({
      title: 'Delete this AI chat?',
      text: `"${c.title}" and its messages will be permanently deleted. Your private chats are not affected.`,
      confirmText: 'Delete',
      icon: 'warning',
    });
    if (ok && (await ai.remove(c.id))) toastSuccess('AI chat deleted');
  };

  const confirmDeleteDocument = (d) =>
    confirmAction({
      title: 'Delete this document?',
      text: `"${d.fileName}" will be permanently deleted, together with everything the AI learned from it. Past answers keep their text.`,
      confirmText: 'Delete',
      icon: 'warning',
    });

  const sendWithScope = (text) => ai.send(text, { documentIds: knowledge.selected });

  const openSource = useCallback((s) => knowledge.open(s.documentId, s.fileName, s.pageNumber), [knowledge.open]);

  const showKnowledge = () => {
    setTab('knowledge');
    setShowList(true);
  };

  const tabButton = (id, label) => (
    <button
      type="button"
      role="tab"
      aria-selected={tab === id}
      onClick={() => setTab(id)}
      className={`min-h-11 flex-1 border-b-2 px-3 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-indigo-500 ${
        tab === id ? 'border-indigo-600 text-indigo-700 dark:border-indigo-400 dark:text-indigo-300' : 'border-transparent text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-100'
      }`}
    >
      {label}
    </button>
  );

  return (
    <>
      <div className="flex h-full min-h-0 overflow-hidden bg-white md:rounded-2xl md:border md:border-slate-200 md:shadow-sm dark:bg-slate-900 md:dark:border-slate-800">
        <aside className={`${showList ? 'flex' : 'hidden md:flex'} min-h-0 w-full flex-col border-slate-200 md:w-64 md:shrink-0 md:border-r lg:w-72 xl:w-80 dark:border-slate-800`}>
          <div role="tablist" aria-label="AI sidebar" className="flex border-b border-slate-200 dark:border-slate-800">
            {tabButton('chats', 'Chats')}
            {tabButton('knowledge', `Knowledge${knowledge.documents.length ? ` (${knowledge.documents.length})` : ''}`)}
          </div>
          <motion.div key={tab} variants={panelSwitchVariants} initial="initial" animate="animate" className="flex min-h-0 flex-1 flex-col">
          {tab === 'chats' ? (
            <AIConversationList
              conversations={ai.conversations}
              status={ai.listStatus}
              error={ai.listError}
              activeId={ai.activeId}
              busy={ai.generating}
              onOpen={open}
              onNew={create}
              onDelete={remove}
              onRetry={ai.reloadList}
            />
          ) : (
            <KnowledgePanel knowledge={knowledge} onConfirmDelete={confirmDeleteDocument} onOpenError={toastError} />
          )}
          </motion.div>
        </aside>
        <section className={`${showList ? 'hidden md:flex' : 'flex'} min-h-0 min-w-0 flex-1 flex-col`}>
          <AIChatWindow
            title={active?.title}
            messages={ai.messages}
            messagesStatus={ai.messagesStatus}
            generating={ai.generating}
            error={ai.error}
            blockedUntil={ai.blockedUntil}
            onSend={sendWithScope}
            mode={ai.mode}
            onModeChange={ai.setMode}
            readyCount={knowledge.readyCount}
            selectedCount={knowledge.selected.length}
            onOpenKnowledge={showKnowledge}
            onOpenSource={openSource}
            onStop={ai.stop}
            onDismissError={ai.dismissError}
            onBack={() => setShowList(true)}
            onReload={() => ai.activeId && ai.open(ai.activeId)}
          />
        </section>
      </div>
    </>
  );
}
