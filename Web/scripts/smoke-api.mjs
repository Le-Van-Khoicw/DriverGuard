// Optional read-only integration check. Never creates/deletes business data.
import assert from 'node:assert/strict';

const base = process.env.SMOKE_API_URL || 'http://localhost:8000/api/v1';
const username = process.env.SMOKE_USERNAME;
const password = process.env.SMOKE_PASSWORD;
if (!username || !password) throw new Error('Set SMOKE_USERNAME and SMOKE_PASSWORD for a local test admin.');
async function send(path, options = {}) {
  const response = await fetch(`${base}${path}`, { ...options, signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200, `${path}: HTTP ${response.status}`);
  return response;
}
const login = await send('/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ username, password }),
});
const { access_token } = await login.json();
assert.equal(typeof access_token, 'string');
const headers = { Authorization: `Bearer ${access_token}`, Origin: 'http://localhost:5173' };
for (const path of ['/dashboard/summary', '/dashboard/alert-trend', '/users?role=driver', '/devices', '/vehicles', '/monitoring-sessions', '/drowsiness-events?pageSize=100', '/reports/export']) {
  const response = await send(path, { headers });
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://localhost:5173', `${path}: CORS`);
  if (path === '/reports/export') assert.match(response.headers.get('content-type'), /text\/csv/);
  else {
    const body = await response.json();
    if (path === '/dashboard/summary') assert.equal(typeof body.totalDevices, 'number');
    else if (path.startsWith('/drowsiness-events')) assert.ok(Array.isArray(body.items));
    else assert.ok(Array.isArray(body));
  }
  console.log(`PASS ${path}`);
}
console.log('Live API smoke passed: login + 8 reads + CORS. No business data changed.');
