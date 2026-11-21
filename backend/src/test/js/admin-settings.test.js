const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('admin navigation groups all settings and retains related workspaces', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin.html'), 'utf8');
  const navigation = html.match(/<nav aria-label="Administration navigation">([\s\S]*?)<\/nav>/)[1];
  for (const group of ['delivery-pricing', 'booking-limits', 'fleet-access', 'system-comms']) {
    assert.ok(navigation.includes(`href="#${group}"`));
    assert.ok(html.includes(`data-admin-group="${group}"`));
  }
  for (const url of ['/admin-branding.html', '/admin-credit.html', '/delivery-coverage.html',
    '/address-book.html', '/dispatch.html']) {
    assert.ok(html.includes(`href="${url}"`));
  }
});

test('switches sidebar groups and supports links to individual settings without losing edits', () => {
  const groups = {
    'service-levels': 'delivery-pricing', pricing: 'delivery-pricing', operations: 'booking-limits',
    'courier-capacity': 'fleet-access', dispatchers: 'fleet-access',
    email: 'system-comms', notifications: 'system-comms', 'configuration-history': 'system-comms'
  };
  const panels = Object.entries(groups).map(([id, group]) => ({
    id, dataset: { adminGroup: group }, hidden: false, value: 'unsaved edit'
  }));
  const links = [...new Set(Object.values(groups))].map(group => ({
    dataset: { adminNav: group }, attributes: {},
    setAttribute(name, value) { this.attributes[name] = value; },
    removeAttribute(name) { delete this.attributes[name]; }
  }));
  const title = {}, description = {};
  const window = { location: { hash: '' }, addEventListener(event, callback) { this[event] = callback; } };
  const document = {
    querySelectorAll: selector => selector === '[data-admin-group]' ? panels : links,
    getElementById: id => id === 'adminSectionTitle' ? title : description
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-navigation.js'), 'utf8'),
    { document, window });
  assert.equal(title.textContent, 'Delivery & Pricing');
  assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['service-levels', 'pricing']);
  window.location.hash = '#fleet-access';
  window.hashchange();
  assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['courier-capacity', 'dispatchers']);
  assert.equal(links.find(link => link.dataset.adminNav === 'fleet-access').attributes['aria-current'], 'page');
  window.location.hash = '#email';
  window.hashchange();
  assert.equal(title.textContent, 'System & Comms');
  assert.equal(panels.find(panel => panel.id === 'email').hidden, false);
  assert.equal(panels.find(panel => panel.id === 'pricing').value, 'unsaved edit');
  window.location.hash = '#constructor';
  window.hashchange();
  assert.equal(title.textContent, 'Delivery & Pricing');
  assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['service-levels', 'pricing']);
});

async function setup({ failLoad = false, failSave = false, user = { name: 'Admin' }, historyChanges = [] } = {}) {
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
    chargeableWeight(pricing, items) {
      return items.reduce((sum, item) => {
        const cubic = item.length * item.width * item.height / pricing.cubicDivisor;
        const each = pricing.weightMode === 'CUBIC' ? cubic
          : pricing.weightMode === 'GREATER' ? Math.max(item.weight, cubic) : item.weight;
        return sum + each * item.quantity;
      }, 0);
    },
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
      if (route === '/api/service-levels') {
        return { generalMinutes: 240, expressMinutes: 120, targetStart: 'BOOKING' };
      }
      if (route === '/api/admin/service-levels') return JSON.parse(options.body);
      if (route === '/api/admin/smtp') return options?.method === 'PUT' ? JSON.parse(options.body) : null;
      if (route === '/api/admin/operational-settings' && options?.method === 'PUT') return JSON.parse(options.body);
      if (route === '/api/admin/operational-settings') return {
        maxItemsPerBooking: 200, maxPackageLines: 10, maxQuantityPerItem: 20,
        maxItemWeightKg: 10000, maxDimensionCm: 10000, bookingCutoff: '17:00',
        timeZone: 'Australia/Melbourne', maxScheduleDaysAhead: 30,
        deliveryWindows: ['08:00-10:00', '10:00-12:00'],
        staleAssignmentEnabled: true, staleAssignmentMinutes: 120, unassignStaleAssignments: false
      };
      if (route === '/api/admin/courier-capacity') return [];
      if (route === '/api/admin/configuration-history') return historyChanges;
      if (route.startsWith('/api/admin/configuration-history/') && options?.method === 'POST') return {};
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

test('loads and saves configurable delivery targets and clock start', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('generalMinutes').value, 240);
  assert.equal(byId('expressMinutes').value, 120);
  byId('generalMinutes').value = '300';
  byId('expressMinutes').value = '90';
  byId('targetStart').value = 'PICKUP';
  await byId('serviceLevelsForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-levels');
  assert.deepEqual(JSON.parse(request.options.body), {
    generalMinutes: 300, expressMinutes: 90, targetStart: 'PICKUP'
  });
  assert.match(messages.serviceLevelsMessage.message, /saved/);
});

test('loads current pricing and previews sample estimates', async () => {
  const { byId, messages } = await setup();
  assert.equal(byId('settingsView').hidden, false);
  assert.equal(byId('baseFee').value, 5);
  assert.equal(byId('pricingPreview').textContent, 'Example (10 km, 2 kg, 50 × 40 × 25 cm): scheduled $18.00 · same-day $31.50');
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
    pricingMode: 'ROUTE', weightMode: 'ACTUAL', cubicDivisor: 5000, radiusBands: []
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
  assert.equal(byId('pricingPreview').textContent, 'Example (10 km, 2 kg, 50 × 40 × 25 cm): scheduled $18.00 · same-day $31.50');
});

test('previews and saves cubic weight pricing', async () => {
  const { byId, requests } = await setup();
  byId('weightMode').value = 'CUBIC';
  byId('weightMode').listeners.change();
  assert.match(byId('pricingPreview').textContent, /scheduled \$22\.00/);
  await byId('pricingForm').listeners.submit({ preventDefault() {} });
  const body = JSON.parse(requests.find(item => item.route === '/api/admin/pricing').options.body);
  assert.equal(body.weightMode, 'CUBIC');
  assert.equal(body.cubicDivisor, 5000);
});

test('saves SMTP settings and can return to deployment settings', async () => {
  const { byId, requests, messages } = await setup();
  byId('smtpHost').value = 'smtp.example.com';
  byId('smtpPort').value = '587';
  byId('smtpFrom').value = 'support@example.com';
  byId('smtpSecurity').value = 'STARTTLS';
  byId('smtpAuth').checked = true;
  byId('smtpUsername').value = 'mailer';
  await byId('smtpForm').listeners.submit({ preventDefault() {} });
  const save = requests.find(item => item.route === '/api/admin/smtp' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(save.options.body), { host: 'smtp.example.com', port: 587,
    fromAddress: 'support@example.com', security: 'STARTTLS', authentication: true, username: 'mailer' });
  await byId('clearSmtpButton').listeners.click();
  assert.equal(requests.some(item => item.route === '/api/admin/smtp' && item.options?.method === 'DELETE'), true);
  assert.match(messages.smtpMessage.message, /deployment settings/);
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

test('loads and saves operational package, scheduling, and stale-assignment limits', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('maxItemsPerBooking').value, 200);
  byId('maxItemsPerBooking').value = '120';
  byId('bookingCutoff').value = '16:30';
  byId('timeZone').value = 'Australia/Sydney';
  byId('deliveryWindows').value = '09:00-11:00, 13:00-15:00';
  byId('staleAssignmentEnabled').checked = false;
  await byId('operationalForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/operational-settings'
    && item.options?.method === 'PUT');
  const body = JSON.parse(request.options.body);
  assert.equal(body.maxItemsPerBooking, 120);
  assert.equal(body.bookingCutoff, '16:30');
  assert.equal(body.timeZone, 'Australia/Sydney');
  assert.deepEqual(body.deliveryWindows, ['09:00-11:00', '13:00-15:00']);
  assert.equal(body.staleAssignmentEnabled, false);
  assert.match(messages.operationalMessage.message, /saved/);
});

test('allows restoring a configuration change whose prior state was unset', async () => {
  const { byId, requests } = await setup({ historyChanges: [{
    id: 8, category: 'SMTP', changedBy: 'Admin', changedAt: '2026-11-20T00:00:00Z', before: null, after: {}
  }] });
  const button = byId('configurationHistoryList').children[0].children[1];
  assert.notEqual(button.disabled, true);
  await button.listeners.click();
  assert.equal(requests.some(item => item.route === '/api/admin/configuration-history/8/restore'
    && item.options?.method === 'POST'), true);
});
