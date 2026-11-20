const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function element(reset = () => {}) {
  const children = new Map();
  return {
    children: [], value: '', textContent: '', disabled: false, listeners: {}, options: [],
    addEventListener(event, callback) { this.listeners[event] = callback; },
    setAttribute() {},
    querySelector(selector) {
      if (!children.has(selector)) children.set(selector, element());
      return children.get(selector);
    },
    append(...items) { this.children.push(...items); },
    replaceChildren(...items) {
      this.children = items;
      this.options = items;
      if (items.length === 1 && items[0].value !== undefined) this.value = items[0].value;
    },
    add(option) { this.options.push(option); },
    reset
  };
}

async function setup({ failCreate = false, failCouriers = false, user = { name: 'Admin' } } = {}) {
  const elements = new Map();
  function byId(id) {
    if (!elements.has(id)) {
      const reset = id === 'adhocForm'
        ? () => {
          for (const key of ['adhocSender', 'adhocContact', 'adhocPickup', 'adhocRecipient', 'adhocDestination', 'adhocCourier']) {
            byId(key).value = '';
          }
        }
        : () => {};
      elements.set(id, element(reset));
    }
    return elements.get(id);
  }
  const requests = [];
  const messages = {};
  const document = {
    createElement: element,
    querySelector: selector => selector.includes('speed') ? { value: 'GENERAL' } : element()
  };
  const PP = {
    keys: { couriers: 'couriers', tickets: 'tickets' },
    terminalStatuses: new Set(['DELIVERED']), byId, money: String, text: String,
    statusText: String, escapeHtml: String, serviceLevelName: String, estimatePrice: () => 10,
    showServiceTargets() {}, readArray: () => [], saveArray() {}, drawMap() {},
    detail: () => '', setMessage(id, message) { messages[id] = message; },
    requireRole: async role => { assert.deepEqual(Array.from(role), ['ADMIN', 'DISPATCHER']); return user; }, signOut() {},
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/dispatch/couriers') {
        if (failCouriers) throw new Error('Unavailable');
        return [{ courierId: 'courier-1' }];
      }
      if (route === '/api/booking-route') return {
        distanceMeters: 12345, durationSeconds: 1500, straightLineDistanceKm: 11,
        points: [{ latitude: 51.5, longitude: -0.1 }, { latitude: 51.6, longitude: -0.2 }]
      };
      if (route === '/api/parcels/adhoc') {
        if (failCreate) throw new Error('Connection interrupted');
        return { id: 'p1', trackingNumber: 'TRK-1' };
      }
      if (route === '/api/parcels') return [];
      if (route === '/api/pricing') return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5 };
      if (route === '/api/service-levels') return {};
      throw new Error(`Unexpected request ${route}`);
    }
  };
  const context = {
    PP, document, window: { setInterval() {} }, localStorage: { getItem() { return null; } },
    Option: function (label, value) { this.label = label; this.value = value; }
  };
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-adhoc.js'), 'utf8');
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

async function prepareBooking(byId) {
  byId('adhocPickup').value = '1 Main';
  byId('adhocDestination').value = '2 Oak';
  byId('itemTiles').children[0].children[0].listeners.click();
  byId('calculateRouteButton').listeners.click();
  await new Promise(resolve => setImmediate(resolve));
}

test('creates an assigned parcel with pickup details and resets on success', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('adhocCourier').options[1].value, 'courier-1');
  await prepareBooking(byId);
  fill(byId);
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/parcels/adhoc');
  assert.equal(request.options.method, 'POST');
  const payload = JSON.parse(request.options.body);
  assert.equal(payload.sender, 'Alice');
  assert.equal(payload.customerContact, '12345');
  assert.equal(payload.pickupAddress, '1 Main');
  assert.equal(payload.recipient, 'Bob');
  assert.equal(payload.destination, '2 Oak');
  assert.equal(payload.courierId, 'courier-1');
  assert.equal(payload.deliverySpeed, 'GENERAL');
  assert.equal(Object.hasOwn(payload, 'scheduledDate'), false);
  assert.equal(Object.hasOwn(payload, 'scheduledWindow'), false);
  assert.match(messages.adhocMessage, /TRK-1.*courier-1/);
  assert.equal(byId('adhocSender').value, '');
  assert.equal(byId('adhocButton').disabled, false);
  assert.equal(byId('viewDeliveryLink').href, '/dispatch.html?parcel=p1');
  assert.equal(byId('viewDeliveryLink').hidden, false);
});

test('requires a verified admin before loading couriers or enabling the form', async () => {
  const { byId, requests } = await setup({ user: null });
  assert.equal(requests.length, 0);
  assert.equal(byId('adhocForm').listeners.submit, undefined);
});

test('shows courier loading failures and allows retry', async () => {
  const { byId, messages, requests } = await setup({ failCouriers: true });
  assert.match(messages.courierMessage, /Could not load couriers/);
  assert.equal(byId('refreshCouriersButton').disabled, false);
  await byId('refreshCouriersButton').listeners.click();
  assert.equal(requests.filter(item => item.route === '/api/dispatch/couriers').length, 2);
});

test('retains form values and warns against duplicate retries after errors', async () => {
  const { byId, requests, messages } = await setup({ failCreate: true });
  await prepareBooking(byId);
  fill(byId);
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  assert.equal(requests.filter(item => item.route === '/api/parcels/adhoc').length, 1);
  assert.equal(byId('adhocSender').value, ' Alice ');
  assert.match(messages.adhocMessage, /Check the parcel queue/);
  assert.equal(byId('adhocButton').disabled, false);
});

test('does not submit blank fields', async () => {
  const { byId, requests, messages } = await setup();
  await prepareBooking(byId);
  fill(byId);
  byId('adhocSender').value = '   ';
  await byId('adhocForm').listeners.submit({ preventDefault() {} });
  assert.equal(requests.some(item => item.route === '/api/parcels/adhoc'), false);
  assert.match(messages.adhocMessage, /Complete the sender, recipient, address, and courier details/);
});
