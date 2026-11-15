const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function setup({ user = { name: 'Admin' }, failTheme = false } = {}) {
  const elements = new Map();
  const messages = {};
  const requests = [];
  function byId(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', hidden: true, disabled: false, checked: false, files: [], listeners: {},
      addEventListener(event, callback) { this.listeners[event] = callback; },
      replaceChildren() {},
      querySelector() { return this.button ||= {}; },
      classList: { add() {} }
    });
    return elements.get(id);
  }
  const PP = {
    byId,
    requireRole: async role => { assert.equal(role, 'ADMIN'); return user; },
    signOut() {},
    setMessage(id, message, kind) { messages[id] = { message, kind }; },
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/branding/website') return {
        configured: true,
        branding: { appName: 'Acme', primaryColor: '#112233', accentColor: '#445566',
          logoContentType: 'image/png', logoBase64: 'original' }
      };
      if (route === '/api/admin/website-theme') {
        if (failTheme) throw new Error('Unavailable');
        return options ? JSON.parse(options.body) : { css: 'body {}', fontFamily: 'Arial' };
      }
      if (route === '/api/admin/branding') return JSON.parse(options.body);
      if (route === '/api/admin/branding/images') return [];
      throw new Error(`Unexpected request ${route}`);
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-branding.js'), 'utf8'), {
    PP, window: { confirm: () => true }
  });
  await new Promise(resolve => setImmediate(resolve));
  return { byId, messages, requests };
}

test('requires a verified administrator before loading settings', async () => {
  const { requests, byId } = await setup({ user: null });
  assert.equal(requests.length, 0);
  assert.equal(byId('brandingView').hidden, true);
});

test('saves identity without unintentionally removing the existing logo', async () => {
  const { byId, requests, messages } = await setup();
  byId('appName').value = 'New name';
  const button = {};
  await byId('identityForm').listeners.submit({ preventDefault() {}, submitter: button });
  const request = requests.find(item => item.route === '/api/admin/branding');
  assert.deepEqual(JSON.parse(request.options.body), {
    appName: 'New name', primaryColor: '#112233', accentColor: '#445566',
    logoContentType: 'image/png', logoBase64: 'original'
  });
  assert.equal(button.disabled, false);
  assert.match(messages.identityMessage.message, /saved/);
});

test('saves stylesheet and blocks saving when it cannot be loaded', async () => {
  const loaded = await setup();
  loaded.byId('css').value = 'body { color: red; }';
  await loaded.byId('themeForm').listeners.submit({ preventDefault() {}, submitter: {} });
  const saved = loaded.requests.find(item => item.route === '/api/admin/website-theme' && item.options);
  assert.deepEqual(JSON.parse(saved.options.body), { css: 'body { color: red; }', fontFamily: 'Arial' });
  const failed = await setup({ failTheme: true });
  assert.equal(failed.byId('themeForm').querySelector('button').disabled, true);
  assert.match(failed.messages.themeMessage.message, /Could not load stylesheet/);
});
