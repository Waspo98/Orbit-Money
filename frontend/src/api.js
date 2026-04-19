// Thin fetch wrapper. Sends cookies (credentials: 'same-origin') and parses JSON.
// Throws an Error with .status and .data populated for non-2xx responses.

async function request(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    credentials: 'same-origin'
  });

  // Tolerate empty bodies (e.g. 204 responses).
  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    const err = new Error(data.error || `Request failed: ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }

  return data;
}

export const api = {
  get: (path) => request(path),
  post: (path, body = {}) =>
    request(path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body = {}) =>
    request(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: (path, body = {}) =>
    request(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del: (path) => request(path, { method: 'DELETE' })
};
