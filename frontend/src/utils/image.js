export const MAX_AVATAR_MB = 2;
export const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/png'];

export function validateImageFile(file) {
  if (!file) return 'Please choose an image';
  if (!ALLOWED_AVATAR_TYPES.includes(file.type)) return 'Only JPEG and PNG images are allowed';
  if (file.size === 0) return 'That file is empty';
  if (file.size > MAX_AVATAR_MB * 1024 * 1024) return `Image must be ${MAX_AVATAR_MB} MB or smaller`;
  return '';
}
