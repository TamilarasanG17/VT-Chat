export function mediaErrorMessage(err, callType = 'VOICE') {
  const video = callType === 'VIDEO';
  const device = video ? 'camera and microphone' : 'microphone';

  if (err?.code === 'NO_MEDIA_SUPPORT') {
    return 'Calls are not supported here. Use a recent browser and open the app over HTTPS (or localhost).';
  }
  switch (err?.name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
    case 'SecurityError':
      return `Allow access to your ${device} in the browser's address bar or site settings, then try again.`;
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return video ? 'No camera or microphone was found on this device.' : 'No microphone was found on this device.';
    case 'NotReadableError':
    case 'TrackStartError':
    case 'AbortError':
      return `Your ${device} is being used by another app. Close it and try again.`;
    case 'OverconstrainedError':
    case 'ConstraintNotSatisfiedError':
      return `Your ${device} does not support the required settings.`;
    default:
      return `Could not start your ${device}. Please check it and try again.`;
  }
}

export function callServerErrorMessage(error) {
  switch (error?.code) {
    case 'ALREADY_IN_CALL':
      return 'You are already in a call.';
    case 'CALL_NOT_FOUND':
      return 'This call is no longer available.';
    case 'CALL_NOT_RINGING':
      return 'This call can no longer be answered.';
    case 'CALL_UNAVAILABLE':
      return 'Calls are temporarily unavailable. Please try again.';
    default:
      return 'The call could not be completed.';
  }
}
