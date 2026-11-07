const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function setup({ failCreate = false } = {}) {
  const elements = new Map();
  function byId(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', disabled: false, listeners: {}, options: [],
      addEventListener(event, callback) { this.listeners[event] = callback; },
      querySelectorAll() { return []; },
      replaceChildren(option) { this.options = [option]; this.value = option.value; },
      add(option) { this.options.push(option); },
      reset() {
        for (const key of ['adhocSender', 'adhocContact', 'adhocPickup', 'adhocRecipient', 'adhocDestination', 'adhocCourier']) {
          byId(key).value = '';
        }
      }
    });
    return elements.get(id);
  }
  const requests = [];
  const messages = {};
  const PP = {
    keys: { couriers: 'couriers', tickets: 'tickets' },
    terminalStatuses: new Set(['DELIVERED']), byId, money: String, text: String,
    statusText: String, escapeHtml: String, readArray: () => [], saveArray() {}, drawMap() {},
    detail: () => '', setMessage(id, message) { messages[id] = message; },
    requireRole: async () => ({ name: 'Admin' }), signOut() {},
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/admin/couriers') return [{ courierId: 'courier-1' }];
      if (route === '/api/parcels/adhoc') {
        if (failCreate) throw new Error('Connection interrupted');
        return { id: 'p1', trackingNumber: 'TRK-1' };
      }
      if (route === '/api/parcels') return [];
      throw new Error(`Unexpected request ${route}`);
    }
  };
  const context = {
    PP, window: { setInterval() {} }, localStorage: { getItem() { return null; } },
    Option: function (label, value) { this.label = label; this.value = value; }
  };
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin.js'), 'utf8');
  vm.runInNewContext(source, context);
  await new Promise(resolve => setImmediate(resolve));
  return { byId, requests, messages };
}

function fill(byId) {
  byId('adhocSender').value = ' Alice ';
  byId('adhocContact').value = '12345';
  byId('adhocPickup').value = '1 Main';
  byId('adhocRecipient').value = 'Bob';
  byId('adhocDestination').value = '2 Oak';
  byId('adhocCourier').value = 'courier-1';
}

test('creates an assigned parcel with pickup details and resets on success', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('adhocCourier').options[1].value, 'courier-1');
  fill(byId);
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/parcels/adhoc');
  assert.equal(request.options.method, 'POST');
  assert.deepEqual(JSON.parse(request.options.body), {
    sender: 'Alice', customerContact: '12345', pickupAddress: '1 Main',
    recipient: 'Bob', destination: '2 Oak', courierId: 'courier-1'
  });
  assert.match(messages.adhocMessage, /TRK-1.*courier-1/);
  assert.equal(byId('adhocSender').value, '');
  assert.equal(byId('adhocButton').disabled, false);
});

test('retains form values and warns against duplicate retries after errors', async () => {
  const { byId, requests, messages } = await setup({ failCreate: true });
  fill(byId);
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  assert.equal(requests.filter(item => item.route === '/api/parcels/adhoc').length, 1);
  assert.equal(byId('adhocSender').value, ' Alice ');
  assert.match(messages.adhocMessage, /Check the parcel queue/);
  assert.equal(byId('adhocButton').disabled, false);
});

test('does not submit blank fields', async () => {
  const { byId, requests, messages } = await setup();
  fill(byId);
  byId('adhocPickup').value = '   ';
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  assert.equal(requests.some(item => item.route === '/api/parcels/adhoc'), false);
  assert.match(messages.adhocMessage, /Complete all fields/);
});
