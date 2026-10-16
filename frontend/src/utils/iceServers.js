const list = (value) =>
  String(value ?? '')
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);

export function envIceServers() {
  const servers = [];
  const stun = list(import.meta.env.VITE_STUN_SERVER);
  servers.push({ urls: stun.length ? stun : ['stun:stun.l.google.com:19302'] });

  const turn = list(import.meta.env.VITE_TURN_SERVER);
  const username = import.meta.env.VITE_TURN_USERNAME;
  const credential = import.meta.env.VITE_TURN_CREDENTIAL;
  if (turn.length && username && credential) servers.push({ urls: turn, username, credential });
  return servers;
}

export function buildIceServers(response) {
  const fromServer = response?.data?.iceServers;
  if (Array.isArray(fromServer) && fromServer.length) {
    return fromServer
      .filter((s) => s?.urls?.length)
      .map((s) => (s.username && s.credential ? { urls: s.urls, username: s.username, credential: s.credential } : { urls: s.urls }));
  }
  return envIceServers();
}
