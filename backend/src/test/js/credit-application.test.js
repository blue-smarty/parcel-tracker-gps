const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('invitation stays out of the URL and is submitted after customer sign-in', async () => {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', hidden: false, listeners: {},
      addEventListener(event, callback) { this.listeners[event] = callback; }
    });
    return elements.get(id);
  };
  const token = 'A'.repeat(43);
  let savedSession = null;
  let replacedUrl;
  let submission;
  const PP = {
    byId,
    session: {
      get: () => savedSession,
      set: value => { savedSession = value; },
      clear: () => { savedSession = null; }
    },
    basicAuthorization: (email, password) => `${email}:${password}`,
    setMessage: (id, message) => { byId(id).textContent = message; },
    api: async (route, options) => {
      if (route === '/api/auth/me') return { role: 'CUSTOMER', username: 'client@example.com' };
      submission = { route, ...options, body: JSON.parse(options.body) };
      return null;
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/credit-application.js'), 'utf8'), {
    PP, window: { location: { hash: `#${token}`, pathname: '/credit-application.html' } },
    history: { replaceState(_state, _title, url) { replacedUrl = url; } }
  });
  assert.equal(replacedUrl, '/credit-application.html');
  byId('email').value = 'client@example.com';
  byId('password').value = 'password';
  const signInButton = {};
  await byId('loginForm').listeners.submit({ preventDefault() {}, submitter: signInButton });
  assert.equal(byId('password').value, '');
  assert.equal(byId('applicationForm').hidden, false);
  byId('businessName').value = ' Business ';
  byId('contactName').value = ' Contact ';
  byId('contactEmail').value = 'client@example.com';
  byId('contactPhone').value = '123';
  byId('requestedLimit').value = '500.00';
  await byId('applicationForm').listeners.submit({ preventDefault() {} });
  assert.equal(submission.route, '/api/credit/applications');
  assert.equal(submission.body.token, token);
  assert.equal(submission.body.businessName, 'Business');
  assert.equal(byId('applicationForm').hidden, true);
  assert.match(byId('message').textContent, /submitted/i);
});

test('missing invitation does not expose a sign-in form', () => {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, { hidden: false });
    return elements.get(id);
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/credit-application.js'), 'utf8'), {
    PP: { byId, setMessage: (id, message) => { byId(id).textContent = message; } },
    window: { location: { hash: '', pathname: '/credit-application.html' } },
    history: { replaceState() {} }
  });
  assert.equal(byId('loginForm').hidden, true);
  assert.match(byId('message').textContent, /original invitation link/i);
});
