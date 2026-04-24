export function sendOk(res, payload = {}) {
  return res.json(payload);
}

export function sendCreated(res, payload = {}) {
  return res.status(201).json(payload);
}

export function sendBadRequest(res, message) {
  return sendError(res, 400, message);
}

export function sendNotFound(res, message) {
  return sendError(res, 404, message);
}

export function sendUnauthorized(res, message) {
  return sendError(res, 401, message);
}

export function sendServerError(res, err) {
  return sendError(res, 500, err?.message || 'Unexpected server error.');
}

export function sendRouteError(res, err) {
  return sendError(res, err?.status || 500, err?.message || 'Unexpected server error.');
}

export function sendStatusPayload(res, status, payload) {
  return res.status(status).json(payload);
}

export function sendError(res, status, message) {
  return res.status(status).json({ error: message });
}
