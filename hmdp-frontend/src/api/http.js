const API_BASE = import.meta.env.VITE_API_BASE || '/api';

export async function request(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { authorization: token } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    cache: 'no-cache'
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.success === false) {
    throw new Error(data.errorMsg || res.statusText);
  }
  return data.data;
}

export async function upload(path, file, token) {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: token ? { authorization: token } : undefined,
    body: form
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.success === false) {
    throw new Error(data.errorMsg || res.statusText);
  }
  return data.data;
}

function getStoredToken() {
  try {
    return localStorage.getItem('hmdp-token') || '';
  } catch {
    return '';
  }
}

// axios-like minimal wrapper (for legacy/new pages)
export default {
  get(path) {
    return request(path, { method: 'GET', token: getStoredToken() });
  },
  post(path, body) {
    return request(path, { method: 'POST', body, token: getStoredToken() });
  },
  put(path, body) {
    return request(path, { method: 'PUT', body, token: getStoredToken() });
  },
  delete(path, body) {
    return request(path, { method: 'DELETE', body, token: getStoredToken() });
  }
};
