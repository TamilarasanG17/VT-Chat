export const AI_MAX_MESSAGE_LENGTH = Number(import.meta.env.VITE_AI_MAX_MESSAGE_LENGTH) || 4000;

export const AI_SUGGESTIONS = [
  'Explain Java inheritance simply',
  'Give me 5 Spring Boot interview questions',
  'Help me debug a NullPointerException',
  'Summarize this text for me: ',
];

export const RAG_MAX_QUERY_LENGTH = Number(import.meta.env.VITE_RAG_MAX_QUERY_LENGTH) || 2000;
export const RAG_MAX_DOCUMENT_MB = Number(import.meta.env.VITE_RAG_MAX_DOCUMENT_SIZE_MB) || 20;
export const KNOWLEDGE_EXTENSIONS = ['pdf', 'txt', 'md', 'doc', 'docx'];
export const KNOWLEDGE_ACCEPT = KNOWLEDGE_EXTENSIONS.map((e) => `.${e}`).join(',');
export const BROWSER_VIEWABLE = ['pdf', 'txt', 'md'];

export const MODE_NORMAL = 'NORMAL';
export const MODE_RAG = 'RAG';

export const STATUS_LABEL = { UPLOADED: 'Queued', PROCESSING: 'Processing', READY: 'Ready', FAILED: 'Failed' };
export const isBusyStatus = (s) => s === 'UPLOADED' || s === 'PROCESSING';

export const extensionOfName = (name = '') => {
  const i = name.lastIndexOf('.');
  return i < 0 ? '' : name.slice(i + 1).toLowerCase();
};

export function validateKnowledgeFile(file) {
  if (!file) return 'Please choose a file.';
  if (!KNOWLEDGE_EXTENSIONS.includes(extensionOfName(file.name))) return 'Supported documents: PDF, TXT, MD, DOC and DOCX.';
  if (file.size === 0) return 'That file is empty.';
  if (file.size > RAG_MAX_DOCUMENT_MB * 1024 * 1024) return `That file is too large (maximum ${RAG_MAX_DOCUMENT_MB} MB).`;
  return '';
}
