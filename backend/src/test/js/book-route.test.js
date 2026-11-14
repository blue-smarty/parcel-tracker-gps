const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.value = '';
    this.hidden = false;
    this.disabled = false;
    this.listeners = {};
    this.children = [];
  }
  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  dispatch(type) { for (const handler of this.listeners[type] || []) handler({ preventDefault() {} }); }
  setAttribute() {}
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  querySelector() { return new Element(); }
}

async function setup(routeRequest) {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  const document = {
    getElementById: byId,
    createElement: () => new Element(),
    querySelector: selector => selector.includes('speed') ? { value: 'SAME_DAY' } : new Element()
  };
  const maps = [];
  const bookings = [];
  const PP = {
    byId, money: String, estimatePrice: () => 10,
    setMessage: (id, message) => { byId(id).textContent = message; }, signOut() {},
    requireRole: async () => ({ name: 'Customer', username: 'customer' }),
    customerTracking: { initialize() {}, refresh: async () => {} },
    api: async (url, options) => {
      if (url === '/api/booking-route') return routeRequest(JSON.parse(options.body));
      if (url === '/api/parcels') {
        bookings.push(JSON.parse(options.body));
        return { trackingNumber: 'TRK-1', estimatedPrice: 10 };
      }
      return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75 };
    },
    drawMap: (...args) => maps.push(args)
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/book.js'), 'utf8'),
    { document, PP, console });
  await new Promise(resolve => setImmediate(resolve));
  byId('pickupAddress').value = 'Pickup';
  byId('deliveryAddress').value = 'Destination';
  return { byId, maps, bookings };
}

const response = {
  distanceMeters: 12345, durationSeconds: 1500,
  straightLineDistanceKm: 11,
  points: [{ latitude: 51.5, longitude: -0.1 }, { latitude: 51.6, longitude: -0.2 }]
};

test('calculates the route, updates distance and clears it when an address changes', async () => {
  const { byId, maps } = await setup(async request => {
    assert.equal(request.origin, 'Pickup');
    assert.equal(request.destination, 'Destination');
    return response;
  });
  byId('calculateRouteButton').dispatch('click');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(byId('routeDistance').textContent, '12.3 km');
  assert.match(byId('routeSummary').textContent, /25 min/);
  assert.equal(maps.length, 1);
  byId('deliveryAddress').dispatch('input');
  assert.equal(byId('routeDistance').textContent, 'Not calculated');
  assert.equal(byId('routeMap').hidden, true);
});

test('ignores a pending route after the user edits an address', async () => {
  let resolveRoute;
  const { byId, maps } = await setup(() => new Promise(resolve => { resolveRoute = resolve; }));
  byId('calculateRouteButton').dispatch('click');
  byId('deliveryAddress').value = 'New destination';
  byId('deliveryAddress').dispatch('input');
  resolveRoute(response);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(byId('routeDistance').textContent, 'Not calculated');
  assert.equal(maps.length, 0);
  assert.equal(byId('calculateRouteButton').disabled, false);
});

test('reports lookup errors without retaining an old distance', async () => {
  const { byId } = await setup(async () => { throw new Error('Unavailable'); });
  byId('calculateRouteButton').dispatch('click');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(byId('routeDistance').textContent, 'Not calculated');
  assert.match(byId('routeSummary').textContent, /Unavailable/);
});

test('requires a current calculated route before submitting a booking', async () => {
  const { byId, bookings } = await setup(async () => response);
  byId('itemTiles').children[0].children[0].dispatch('click');
  byId('recipientName').value = 'Recipient';
  byId('builderForm').dispatch('submit');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(bookings.length, 0);
  assert.match(byId('builderMessage').textContent, /Calculate the driving route/);

  byId('calculateRouteButton').dispatch('click');
  await new Promise(resolve => setImmediate(resolve));
  byId('builderForm').dispatch('submit');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(bookings.length, 1);
  assert.equal(bookings[0].distanceKm, 12.3);
  assert.equal(bookings[0].straightLineDistanceKm, 11);

  byId('deliveryAddress').value = 'Different address';
  byId('builderForm').dispatch('submit');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(bookings.length, 1);
  assert.match(byId('builderMessage').textContent, /current addresses/);
});
