const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function setup(fetch) {
  const window = {};
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/common.js'), 'utf8');
  vm.runInNewContext(source, {
    window, fetch, Headers, console,
    sessionStorage: { getItem: () => JSON.stringify({ authorization: 'Basic example' }) }
  });
  return window.PP;
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

test('refreshes an expired CSRF token and retries the rejected write once', async () => {
  const requests = [];
  let tokenRequests = 0;
  const pp = setup(async (url, options) => {
    requests.push({ url, token: options.headers?.get('X-XSRF-TOKEN'), auth: options.headers?.get('Authorization') });
    if (url === '/api/csrf') return jsonResponse(200, { token: ++tokenRequests === 1 ? 'stale' : 'fresh' });
    if (options.headers.get('X-XSRF-TOKEN') === 'stale') {
      return jsonResponse(403, { code: 'CSRF_INVALID', error: 'Security token expired.' });
    }
    return jsonResponse(200, { saved: true });
  });
  assert.equal((await pp.api('/api/admin/pricing', { method: 'PUT', body: '{}' })).saved, true);
  assert.deepEqual(requests.map(request => request.url), [
    '/api/csrf', '/api/admin/pricing', '/api/csrf', '/api/admin/pricing'
  ]);
  assert.equal(requests[1].token, 'stale');
  assert.equal(requests[3].token, 'fresh');
  assert.equal(requests[3].auth, 'Basic example');
});

test('does not retry unrelated authorization failures or persistent CSRF failures', async () => {
  let writes = 0;
  const forbidden = setup(async url => {
    if (url === '/api/csrf') return jsonResponse(200, { token: 'token' });
    writes++;
    return jsonResponse(403, { error: 'Forbidden' });
  });
  await assert.rejects(forbidden.api('/api/admin/pricing', { method: 'PUT' }), /Forbidden/);
  assert.equal(writes, 1);

  writes = 0;
  const expired = setup(async url => {
    if (url === '/api/csrf') return jsonResponse(200, { token: 'token' });
    writes++;
    return jsonResponse(403, { code: 'CSRF_INVALID', error: 'Security token expired.' });
  });
  await assert.rejects(expired.api('/api/admin/pricing', { method: 'PUT' }), /Security token expired/);
  assert.equal(writes, 2);
});

test('401 errors instruct the user to sign in again', async () => {
  const pp = setup(async () => jsonResponse(401, { error: 'Unauthorized' }));
  await assert.rejects(pp.api('/api/pricing'), error =>
    error.status === 401 && /sign in again/i.test(error.message));
});
