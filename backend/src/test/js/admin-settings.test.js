const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function setup({ failLoad = false, failSave = false, user = { name: 'Admin' }, withMap = false } = {}) {
  const elements = new Map();
  function byId(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', disabled: false, hidden: true, listeners: {}, options: [],
      get selectedOptions() { return this.options.filter(option => option.selected); },
      addEventListener(event, callback) { this.listeners[event] = callback; },
      append() {},
      replaceChildren(...children) { this.options = children; },
      classList: { add() {} }
    });
    return elements.get(id);
  }
  const requests = [];
  const messages = {};
  const PP = {
    byId, money: value => `$${Number(value).toFixed(2)}`,
    setMessage(id, message, kind = '') { messages[id] = { message, kind }; },
    requireRole: async role => { assert.equal(role, 'ADMIN'); return user; },
    signOut() {},
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/pricing') {
        if (failLoad) throw new Error('Unavailable');
        return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75 };
      }
      if (route === '/api/admin/pricing') {
        if (failSave) throw new Error('Rejected');
        return JSON.parse(options.body);
      }
      if (route === '/api/admin/service-area') {
        return options?.method === 'PUT' ? JSON.parse(options.body)
          : { coverageMode: 'UNRESTRICTED', states: [], councils: [], polygons: [] };
      }
      if (route === '/api/admin/service-area/councils') return [{ code: '25150', name: 'Melbourne', state: '2' }];
      if (route === '/api/admin/dispatchers') return [];
      throw new Error(`Unexpected request ${route}`);
    }
  };
  const map = { setView() { return this; }, on(event, handler) { this[event] = handler; }, invalidateSize() {} };
  const layer = { addTo() { return this; }, clearLayers() {} };
  const tileRequests = [];
  const L = { map: () => map, tileLayer: (url, options) => { tileRequests.push({ url, options }); return layer; }, layerGroup: () => layer,
    polygon: () => layer, polyline: () => layer, circleMarker: () => layer };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-settings.js'), 'utf8'), {
    PP, L, window: { confirm: () => true, L: withMap ? L : undefined, setTimeout: fn => fn() },
    document: { createElement: () => ({ append() {}, addEventListener() {} }) }
  });
  await new Promise(resolve => setImmediate(resolve));
  return { byId, requests, messages, map, tileRequests };
}

test('loads current pricing and previews sample estimates', async () => {
  const { byId, messages } = await setup();
  assert.equal(byId('settingsView').hidden, false);
  assert.equal(byId('baseFee').value, 5);
  assert.equal(byId('pricingPreview').textContent, 'Example (10 km, 2 kg): scheduled $18.00 · same-day $31.50');
  assert.equal(messages.pricingMessage.kind, '');
});

test('saves edited rates and reports success', async () => {
  const { byId, requests, messages } = await setup();
  byId('baseFee').value = '7';
  await byId('pricingForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/pricing');
  assert.equal(request.options.method, 'PUT');
  assert.deepEqual(JSON.parse(request.options.body), {
    baseFee: 7, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75
  });
  assert.match(messages.pricingMessage.message, /Pricing saved/);
});

test('shows pricing load and save errors instead of claiming success', async () => {
  const loadFailure = await setup({ failLoad: true });
  assert.match(loadFailure.messages.pricingMessage.message, /Could not load pricing/);
  const saveFailure = await setup({ failSave: true });
  await saveFailure.byId('pricingForm').listeners.submit({ preventDefault() {} });
  assert.match(saveFailure.messages.pricingMessage.message, /Could not save pricing/);
  assert.equal(saveFailure.byId('savePricingButton').disabled, false);
});

test('requires a verified admin before loading settings', async () => {
  const { byId, requests } = await setup({ user: null });
  assert.equal(requests.length, 0);
  assert.equal(byId('settingsView').hidden, true);
});

test('creates a dispatcher through the admin-only endpoint', async () => {
  const { byId, requests, messages } = await setup();
  byId('dispatcherUsername').value = 'dispatch1';
  byId('dispatcherPassword').value = 'strong-password';
  await byId('dispatcherForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/dispatchers' && item.options?.method === 'POST');
  assert.deepEqual(JSON.parse(request.options.body), { username: 'dispatch1', password: 'strong-password' });
  assert.equal(byId('dispatcherPassword').value, '');
  assert.match(messages.dispatcherMessage.message, /created/);
});

test('loads and saves the Melbourne service area choice', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('serviceAreaMode').value, 'UNRESTRICTED');
  byId('serviceAreaMode').value = 'MELBOURNE';
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body), { coverageMode: 'MELBOURNE', states: [], councils: [], polygons: [] });
  assert.equal(messages.serviceAreaMessage.kind, 'success');
});

test('saves selected states and council codes in custom coverage', async () => {
  const { byId, requests } = await setup();
  byId('serviceAreaMode').value = 'CUSTOM';
  byId('coverageStates').options = [{ value: '2', selected: true }];
  byId('coverageCouncils').options[0].selected = true;
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body), {
    coverageMode: 'CUSTOM', states: ['2'], councils: ['25150'], polygons: []
  });
});

test('draws and saves a custom area and blocks unfinished drawings', async () => {
    const { byId, requests, messages, map, tileRequests } = await setup({ withMap: true });
    byId('serviceAreaMode').value = 'CUSTOM';
    byId('serviceAreaMode').listeners.change();
    assert.equal(tileRequests[0].url, 'https://tile.openstreetmap.org/{z}/{x}/{y}.png');
    assert.equal(tileRequests[0].options.referrerPolicy, 'origin');
    byId('drawCoverageButton').listeners.click();
    map.click({ latlng: { lat: -38, lng: 144 } });
    map.click({ latlng: { lat: -38, lng: 145 } });
    map.click({ latlng: { lat: -37, lng: 145 } });
    await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
    assert.match(messages.serviceAreaMessage.message, /Finish or cancel/);
    assert.equal(requests.filter(item => item.options?.method === 'PUT').length, 0);
    byId('finishCoverageButton').listeners.click();
    await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
    const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
    assert.deepEqual(JSON.parse(request.options.body).polygons, [[[-38, 144], [-38, 145], [-37, 145]]]);
});
