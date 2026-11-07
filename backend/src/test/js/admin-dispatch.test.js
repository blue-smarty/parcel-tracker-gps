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
      addEventListener() {}, querySelectorAll() { return []; }
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
    byId, money: String, text: String, statusText: String, escapeHtml: String,
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
