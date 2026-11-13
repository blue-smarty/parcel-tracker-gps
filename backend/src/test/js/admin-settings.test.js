const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function setup({ failLoad = false, failSave = false, user = { name: 'Admin' } } = {}) {
  const elements = new Map();
  function byId(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', disabled: false, hidden: true, listeners: {}, options: [],
      children: [],
      get selectedOptions() { return this.options.filter(option => option.selected); },
      addEventListener(event, callback) { this.listeners[event] = callback; },
      append(...children) { this.children.push(...children); },
      replaceChildren(...children) { this.options = children; this.children = children; },
      classList: { add() {} }
    });
    return elements.get(id);
  }
  const requests = [];
  const messages = {};
  const PP = {
    byId, money: value => `$${Number(value).toFixed(2)}`,
    estimatePrice(pricing, distance, weight, speed, radiusDistance) {
      const charge = pricing.pricingMode === 'RADIUS'
        ? pricing.radiusBands.find(band => band.upToKm == null || radiusDistance <= band.upToKm)?.distanceFee
        : distance * pricing.perKmRate;
      if (charge == null) throw new Error('No matching band');
      return Math.round((pricing.baseFee + charge + weight * pricing.perKgRate)
        * (speed === 'SAME_DAY' ? pricing.sameDayMultiplier : 1) * 100) / 100;
    },
    setMessage(id, message, kind = '') { messages[id] = { message, kind }; },
    requireRole: async role => { assert.equal(role, 'ADMIN'); return user; },
    signOut() {},
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/pricing') {
        if (failLoad) throw new Error('Unavailable');
        return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75,
          pricingMode: 'ROUTE', radiusBands: [] };
      }
      if (route === '/api/admin/pricing') {
        if (failSave) throw new Error('Rejected');
        return JSON.parse(options.body);
      }
      if (route === '/api/admin/dispatchers') return [];
      throw new Error(`Unexpected request ${route}`);
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-settings.js'), 'utf8'), {
    PP, window: { confirm: () => true },
    document: { createElement: tag => ({
      tag, value: '', textContent: '', children: [], listeners: {},
      append(...children) { this.children.push(...children); },
      addEventListener(event, callback) { this.listeners[event] = callback; },
      setAttribute() {}
    }) }
  });
  await new Promise(resolve => setImmediate(resolve));
  return { byId, requests, messages };
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
    baseFee: 7, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75,
    pricingMode: 'ROUTE', radiusBands: []
  });
  assert.match(messages.pricingMessage.message, /Pricing saved/);
});

test('saves configurable radius bands and shows a radius-based preview', async () => {
  const { byId, requests } = await setup();
  byId('pricingMode').value = 'RADIUS';
  byId('pricingMode').listeners.change();
  byId('addRadiusBandButton').listeners.click();
  const row = byId('radiusBandsList').children[0];
  const feeInput = row.children[1].children[0];
  feeInput.value = '12';
  feeInput.listeners.input();
  byId('pricingForm').listeners.submit({ preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  const request = requests.find(item => item.route === '/api/admin/pricing' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body).radiusBands, [{ upToKm: null, distanceFee: 12 }]);
  assert.equal(JSON.parse(request.options.body).pricingMode, 'RADIUS');
  assert.equal(byId('pricingPreview').textContent, 'Example (10 km, 2 kg): scheduled $18.00 · same-day $31.50');
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
