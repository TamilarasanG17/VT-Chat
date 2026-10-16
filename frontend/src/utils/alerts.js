import Swal from 'sweetalert2';

const modal = Swal.mixin({
  confirmButtonColor: '#4f46e5',
  cancelButtonColor: '#64748b',
  customClass: { popup: 'rounded-2xl', confirmButton: 'rounded-lg', cancelButton: 'rounded-lg' },
});

const toast = Swal.mixin({
  toast: true,
  position: 'top',
  showConfirmButton: false,
  timer: 3500,
  timerProgressBar: true,
  customClass: { popup: 'rounded-xl' },
});

const themed = (surface) =>
  document.documentElement.classList.contains('dark') ? { background: surface, color: '#e2e8f0' } : {};

export const showSuccess = (title, text) => modal.fire({ icon: 'success', title, text, ...themed('#0f172a') });
export const showError = (title, text) => modal.fire({ icon: 'error', title, text, ...themed('#0f172a') });
export const showInfo = (title, text) => modal.fire({ icon: 'info', title, text, ...themed('#0f172a') });

export const toastSuccess = (title) => toast.fire({ icon: 'success', title, ...themed('#1e293b') });
export const toastError = (title) => toast.fire({ icon: 'error', title, ...themed('#1e293b') });
export const toastInfo = (title) => toast.fire({ icon: 'info', title, ...themed('#1e293b') });

export async function confirmAction({ title, text, confirmText = 'Confirm', cancelText = 'Cancel', icon = 'question' }) {
  const result = await modal.fire({
    icon,
    title,
    text,
    showCancelButton: true,
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    focusCancel: true,
    ...themed('#0f172a'),
  });
  return result.isConfirmed;
}

export const confirmLogout = () =>
  confirmAction({
    title: 'Log out?',
    text: 'You will need a new verification code the next time you log in.',
    confirmText: 'Log out',
    cancelText: 'Stay logged in',
  });

export async function chooseDeleteScope({ canDeleteForEveryone, count = 1 }) {
  const noun = count === 1 ? 'this message' : `these ${count} messages`;
  if (!canDeleteForEveryone) {
    const result = await modal.fire({
      icon: 'question',
      title: 'Delete message?',
      text: `This removes ${noun} only for you. The other person will still see ${count === 1 ? 'it' : 'them'}.`,
      showCancelButton: true,
      confirmButtonText: 'Delete for me',
      cancelButtonText: 'Cancel',
      confirmButtonColor: '#dc2626',
      focusCancel: true,
      ...themed('#0f172a'),
    });
    return result.isConfirmed ? 'ME' : null;
  }
  const result = await modal.fire({
    title: 'Delete message?',
    input: 'radio',
    inputOptions: { ME: 'Delete for me', EVERYONE: 'Delete for everyone' },
    inputValue: 'ME',
    showCancelButton: true,
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    confirmButtonColor: '#dc2626',
    ...themed('#0f172a'),
  });
  return result.isConfirmed ? result.value : null;
}
