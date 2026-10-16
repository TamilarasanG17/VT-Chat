export const FALLBACK_LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'ta', name: 'Tamil' },
  { code: 'hi', name: 'Hindi' },
  { code: 'ml', name: 'Malayalam' },
  { code: 'kn', name: 'Kannada' },
];

export const GENERIC_TRANSLATION_ERROR = 'Unable to translate this message right now.';

const KEY = 'vtchat.translateTo';

export function getLastTargetLanguage() {
  try {
    return localStorage.getItem(KEY) || 'en';
  } catch {
    return 'en';
  }
}

export function rememberTargetLanguage(code) {
  try {
    localStorage.setItem(KEY, code);
  } catch {
  }
}

export const languageName = (code, languages = FALLBACK_LANGUAGES) =>
  languages.find((l) => l.code === code)?.name ?? code?.toUpperCase() ?? '';

export function translationErrorMessage(err) {
  const status = err?.response?.status;
  const serverMessage = err?.response?.data?.message;
  if ([400, 403, 404, 409, 422].includes(status) && serverMessage) return serverMessage;
  if (status === 503 && err?.response?.data?.code === 'TRANSLATION_UNAVAILABLE') return 'Translation service is temporarily unavailable.';
  return GENERIC_TRANSLATION_ERROR;
}

export function describeSource(entry, languages) {
  if (!entry.sourceLanguage) return null;
  const name = languageName(entry.sourceLanguage, languages);
  return entry.romanized ? `${name} (Romanized)` : name;
}
