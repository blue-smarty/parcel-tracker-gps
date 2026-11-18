const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('dispatch selects the delivery linked from the ad hoc page', async () => {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', innerHTML: '',
      addEventListener() {}, querySelectorAll() { return []; }, classList: { add() {} }
    });

    return elements.get(id);
  };
  const parcels = [
    { id: 'first', trackingNumber: 'TRK-1', recipient: 'A', status: 'CREATED' },
    { id: 'second', trackingNumber: 'TRK-2', recipient: 'B', status: 'CREATED' }
  ];
  let selected;
  const PP = {
    keys: { couriers: 'couriers', tickets: 'tickets' }, terminalStatuses: new Set(),
    byId, money: String, text: String, statusText: String, escapeHtml: String, serviceLevelName: value => value,
    readArray: () => [], saveArray() {}, setMessage() {}, detail: () => '',
    drawMap(_container, _parcels, id) { selected = id; },
    api: async () => parcels, requireRole: async () => ({ name: 'Admin' }), signOut() {}
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin.js'), 'utf8'), {
    PP, URLSearchParams, window: { location: { search: '?parcel=second' }, setInterval() {} },
    localStorage: { getItem() { return null; } }
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(selected, 'second');
  assert.match(byId('dispatchRows').innerHTML, /data-parcel-id="second" class="selected-row"/);
});

test('dispatchers see the dispatch workspace without the admin link', async () => {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', innerHTML: '', hidden: true,
      classList: { add() {} }, addEventListener() {}, querySelectorAll() { return []; }
    });

    return elements.get(id);
  };
  const PP = {
    keys: { couriers: 'couriers', tickets: 'tickets' }, terminalStatuses: new Set(),
    byId, money: String, text: String, statusText: String, escapeHtml: String,
    readArray: () => [], saveArray() {}, setMessage() {}, detail: () => '', drawMap() {},
    api: async () => [], requireRole: async roles => {
      assert.deepEqual(Array.from(roles), ['ADMIN', 'DISPATCHER']);
      return { name: 'Dispatcher', role: 'DISPATCHER' };
    }, signOut() {}
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin.js'), 'utf8'), {
    PP, URLSearchParams, window: { location: { search: '' }, setInterval() {} },
    localStorage: { getItem() { return null; } }
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(byId('dispatchView').hidden, false);
  assert.equal(byId('adminSettingsLink').hidden, true);
});

test('auto-dispatch processes direct before express and general', async () => {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', innerHTML: '', listeners: {}, classList: { add() {} },
      addEventListener(event, callback) { this.listeners[event] = callback; },
      querySelectorAll() { return []; }
    });
    return elements.get(id);
  };
  const parcels = ['GENERAL', 'DIRECT', 'EXPRESS'].map(speed => ({
    id: speed, trackingNumber: speed, recipient: 'A', status: 'CREATED', deliverySpeed: speed
  }));
  const assigned = [];
  const PP = {
    keys: { couriers: 'couriers', tickets: 'tickets' }, terminalStatuses: new Set(),
    byId, money: String, statusText: String, escapeHtml: String, serviceLevelName: String,
    readArray: () => [], saveArray() {}, setMessage() {}, detail: () => '', drawMap() {}, signOut() {},
    requireRole: async () => ({ name: 'Admin', role: 'ADMIN' }),
    api: async (route, options) => {
      if (options?.method === 'PUT') assigned.push(route.split('/')[3]);
      return route === '/api/parcels' ? parcels : {};
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin.js'), 'utf8'), {
    PP, URLSearchParams, window: { location: { search: '' }, setInterval() {} },
    localStorage: { getItem() { return null; }, setItem() {} }
  });
  await new Promise(resolve => setImmediate(resolve));
  byId('courierIdsInput').value = 'courier-1';
  await byId('autoDispatchButton').listeners.click();
  assert.deepEqual(assigned, ['DIRECT', 'EXPRESS', 'GENERAL']);
});
