import { API_URL } from '../services/api';

export function resolveAssetUrl(path) {
  if (!path) return null;
  if (/^(https?:|blob:|data:)/i.test(path)) return path;
  return new URL(path, API_URL).href;
}
