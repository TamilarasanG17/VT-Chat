export const EASE = [0.22, 1, 0.36, 1];

export const DURATION = { fast: 0.12, base: 0.18, slow: 0.26 };

export const pageVariants = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: DURATION.slow, ease: EASE } },
  exit: { opacity: 0, transition: { duration: DURATION.fast } },
};

export const fadeVariants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: DURATION.base } },
  exit: { opacity: 0, transition: { duration: DURATION.fast } },
};

export const modalVariants = {
  initial: { opacity: 0, y: 12, scale: 0.98 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: DURATION.base, ease: EASE } },
  exit: { opacity: 0, y: 6, scale: 0.99, transition: { duration: DURATION.fast } },
};

export const dropdownVariants = {
  initial: { opacity: 0, y: -4, scale: 0.98 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: DURATION.fast + 0.02, ease: EASE } },
  exit: { opacity: 0, y: -4, transition: { duration: DURATION.fast } },
};

export const messageVariants = {
  initial: { opacity: 0, y: 8, scale: 0.98 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: DURATION.base, ease: EASE } },
};

export const sidebarVariants = {
  initial: { opacity: 0, x: -16 },
  animate: { opacity: 1, x: 0, transition: { duration: DURATION.slow, ease: EASE } },
  exit: { opacity: 0, x: -16, transition: { duration: DURATION.base } },
};

export const sheetVariants = {
  initial: { opacity: 0, y: 24 },
  animate: { opacity: 1, y: 0, transition: { duration: DURATION.slow, ease: EASE } },
  exit: { opacity: 0, y: 24, transition: { duration: DURATION.base } },
};

export const panelSwitchVariants = {
  initial: { opacity: 0, x: 12 },
  animate: { opacity: 1, x: 0, transition: { duration: DURATION.base, ease: EASE } },
  exit: { opacity: 0, transition: { duration: DURATION.fast } },
};

export const tap = { scale: 0.96 };
