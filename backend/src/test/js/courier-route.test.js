const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const tick = () => new Promise(resolve => setImmediate(resolve));
const parcel = (id, status = 'ACCEPTED') => ({
  id, status, trackingNumber: `TRK-${id}`, pickupAddress: `Pickup ${id}`, destination: `Delivery ${id}`
});

async function setup(initial, routeHandler, denyLocation = false) {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, {
      listeners: {}, innerHTML: '', classList: { add() {} },
      addEventListener(type, handler) { this.listeners[type] = handler; }
    });
    return elements.get(id);
  };
  let deliveries = initial;
  let refresh;
  let gpsCalls = 0;
  const requests = [];
  const PP = {
    byId, escapeHtml: String, text: String, statusText: String, detail: () => '',
    serviceLevelName: String, signOut() {}, requireRole: async () => ({ username: 'c1' }),
    setMessage: (id, message) => { byId(id).textContent = message; },
    api: async (url, options) => {
      if (url.endsWith('/deliveries')) return deliveries;
      if (url.endsWith('/route')) {
        requests.push(JSON.parse(options.body));
        return routeHandler(deliveries);
      }
      throw new Error(`Unexpected request: ${url}`);
    }
  };
  const navigator = { geolocation: { getCurrentPosition(success, failure) {
    gpsCalls++;
    if (denyLocation) return failure();
    success({ coords: { latitude: -37.8, longitude: 144.9 } });
  } } };
  const context = { PP, navigator, document: {}, CourierPush: { init() {} }, setInterval: fn => { refresh = fn; } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/courier.js'), 'utf8'), context);
  await tick();
  await tick();
  return { byId, requests, gpsCalls: () => gpsCalls, refresh, setDeliveries: value => { deliveries = value; } };
}

function route(parcels) {
  return { distanceMeters: 1200, estimatedLateDeliveries: 0, stops: parcels.flatMap(p => {
    const stop = (type, address) => ({
      parcelId: p.id, trackingNumber: p.trackingNumber, type, address,
      targetDeadline: p.targetDeadline, targetStart: p.targetStart, targetMinutes: p.targetMinutes,
      estimatedArrival: '2026-01-01T10:00:00Z'
    });
    return [
      ...(p.status === 'CREATED' || p.status === 'ACCEPTED' ? [stop('PICKUP', p.pickupAddress)] : []),
      stop('DELIVERY', p.destination)
    ];
  }) };
}

test('automatically recalculates only when actionable stops change and supports manual refresh', async () => {
  const app = await setup([parcel('1'), parcel('2', 'PICKED_UP')], route);
  assert.equal(app.requests.length, 1);
  assert.deepEqual(app.requests[0], { latitude: -37.8, longitude: 144.9 });
  assert.match(app.byId('routeStops').innerHTML, /Pickup.*TRK-1.*Pickup 1/);
  assert.match(app.byId('routeStops').innerHTML, /Delivery.*TRK-1.*Delivery 1/);
  assert.match(app.byId('routeStops').innerHTML, /Delivery.*TRK-2.*Delivery 2/);
  app.refresh();
  await tick();
  assert.equal(app.requests.length, 1);
  app.setDeliveries([parcel('1', 'PICKED_UP'), parcel('2', 'IN_TRANSIT')]);
  app.refresh();
  await tick();
  await tick();
  assert.equal(app.requests.length, 2);
  assert.match(app.byId('routeStops').innerHTML, /Delivery.*TRK-1.*Delivery 1/);
  app.byId('optimizeRouteButton').listeners.click();
  await tick();
  assert.equal(app.requests.length, 3);
  assert.equal(app.gpsCalls(), 3);
});

test('recalculates when a deadline changes and displays estimated lateness', async () => {
  const app = await setup([parcel('1', 'PICKED_UP')], parcels => {
    const result = route(parcels);
    result.estimatedLateDeliveries = 1;
    result.stops[0].late = true;
    return result;
  });
  assert.match(app.byId('routeStops').innerHTML, /estimated late/);
  assert.match(app.byId('routeMessage').textContent, /1 estimated late/);
  app.setDeliveries([{ ...parcel('1', 'PICKED_UP'), targetDeadline: '2026-01-01T12:00:00Z' }]);
  app.refresh();
  await tick();
  await tick();
  assert.equal(app.requests.length, 2);
});

test('does not request routes beyond the limit and reports provider errors', async () => {
  const app = await setup(Array.from({ length: 10 }, (_, i) => parcel(String(i))), route);
  assert.equal(app.requests.length, 0);
  assert.match(app.byId('routeMessage').textContent, /up to 9/);
  app.setDeliveries([parcel('1')]);
  app.refresh();
  await tick();
  assert.equal(app.requests.length, 1);
  const failed = await setup([parcel('1')], () => { throw new Error('Unavailable'); });
  assert.match(failed.byId('routeMessage').textContent, /Unavailable/);
});

test('requires location permission before calculating a route', async () => {
  const app = await setup([parcel('1')], route, true);
  assert.equal(app.requests.length, 0);
  assert.match(app.byId('routeMessage').textContent, /Check browser permissions/);
});

test('discards a route response when the assigned stops change', async () => {
  let resolveFirst;
  let calls = 0;
  const app = await setup([parcel('1')], parcels => {
    if (++calls === 1) return new Promise(resolve => { resolveFirst = resolve; });
    return route(parcels);
  });
  app.setDeliveries([parcel('2')]);
  app.refresh();
  await tick();
  resolveFirst(route([parcel('1')]));
  await tick();
  assert.match(app.byId('routeStops').innerHTML, /TRK-2/);
  assert.doesNotMatch(app.byId('routeStops').innerHTML, /TRK-1/);
});
