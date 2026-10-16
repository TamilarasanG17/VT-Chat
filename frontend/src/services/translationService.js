import api from './api';

const unwrap = (promise) => promise.then((res) => res.data.data);

let languagesPromise = null;

export const translationService = {
  translateMessage: (messageId, targetLanguage) =>
    unwrap(api.post(`/messages/${messageId}/translate`, { targetLanguage })),

  getLanguages() {
    if (!languagesPromise) {
      languagesPromise = unwrap(api.get('/translation/languages')).catch((err) => {
        languagesPromise = null;
        throw err;
      });
    }
    return languagesPromise;
  },
};
