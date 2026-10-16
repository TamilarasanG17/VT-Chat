import api, { API_URL } from '../services/api';
import { downloadState } from './downloadState';
import { showError } from './alerts';

const messageFor = async (err) => {
  const status = err?.response?.status;
  if (status === 403) return 'You can open your own files, but only the receiver can download them.';
  if (status === 404) return 'This file is no longer available.';
  if (status === 503) return 'File storage is temporarily unavailable. Please try again shortly.';
  return 'Download failed. Please try again.';
};

export async function downloadFileMessage(message, userId) {
  try {
    const res = await api.get(`/media/${message.id}/download-url`);
    const { url, absolute } = res.data.data;
    const href = absolute ? url : `${API_URL.replace(/\/api$/, '')}${url}`;
    const a = document.createElement('a');
    a.href = href;
    a.download = message.mediaName || 'file';
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    a.remove();
    downloadState.mark(userId, message.id);
    return true;
  } catch (err) {
    await showError('Download failed', await messageFor(err));
    return false;
  }
}
