import { callSummary, isCallType } from './calls';

const mb = (name, fallback) => {
  const n = Number(import.meta.env[name]);
  return Number.isFinite(n) && n > 0 ? n : fallback;
};

export const LIMITS_MB = {
  IMAGE: mb('VITE_MAX_IMAGE_MB', 10),
  VIDEO: mb('VITE_MAX_VIDEO_MB', 50),
  FILE: mb('VITE_MAX_FILE_MB', 25),
  VOICE: mb('VITE_MAX_VOICE_MB', 10),
};

const TYPES = {
  IMAGE: ['jpg', 'jpeg', 'png', 'webp', 'gif'],
  VIDEO: ['mp4', 'webm', 'mov'],
  FILE: ['pdf', 'doc', 'docx', 'txt', 'xls', 'xlsx', 'zip'],
};

export const ACCEPT = {
  IMAGE: TYPES.IMAGE.map((e) => `.${e}`).join(','),
  VIDEO: TYPES.VIDEO.map((e) => `.${e}`).join(','),
  FILE: TYPES.FILE.map((e) => `.${e}`).join(','),
};

export const MAX_VOICE_SECONDS = 15 * 60;

export const extensionOf = (name = '') => {
  const i = name.lastIndexOf('.');
  return i < 0 ? '' : name.slice(i + 1).toLowerCase();
};

export function kindOfFile(file) {
  const ext = extensionOf(file?.name);
  return Object.keys(TYPES).find((k) => TYPES[k].includes(ext)) ?? null;
}

export function validateFile(file) {
  if (!file) return 'Please choose a file to send';
  const kind = kindOfFile(file);
  if (!kind) return 'This file type is not supported';
  if (file.size === 0) return 'This file is empty';
  const max = LIMITS_MB[kind] * 1024 * 1024;
  if (file.size > max) return `That file is too large (maximum ${LIMITS_MB[kind]} MB for ${kind === 'FILE' ? 'documents' : `${kind.toLowerCase()}s`})`;
  return '';
}

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDuration(seconds) {
  const s = Math.max(0, Math.round(Number(seconds) || 0));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function mediaLabel(m, viewerId) {
  switch (m?.messageType) {
    case 'VOICE_CALL':
    case 'VIDEO_CALL':
      return callSummary(m, viewerId);
    case 'IMAGE':
      return '📷 Photo';
    case 'VIDEO':
      return '🎥 Video';
    case 'VOICE':
      return `🎤 Voice message${m.duration ? ` (${formatDuration(m.duration)})` : ''}`;
    case 'FILE':
      return `📄 ${m.mediaName || 'File'}`;
    default:
      return '';
  }
}

export function previewText(m, viewerId) {
  if (!m) return '';
  if (!m.messageType || m.messageType === 'TEXT') return m.content ?? '';
  const label = mediaLabel(m, viewerId);
  if (isCallType(m.messageType)) return label;
  return m.content ? `${label}: ${m.content}` : label;
}

export const isMedia = (m) => !!m?.messageType && m.messageType !== 'TEXT' && !isCallType(m.messageType);
export const isCall = (m) => isCallType(m?.messageType);
