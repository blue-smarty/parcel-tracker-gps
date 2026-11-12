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
    this.children = [];
    this.listeners = {};
  }
  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  async dispatch(type) {
    for (const handler of this.listeners[type] || []) await handler({ preventDefault() {} });
  }
  setAttribute() {}
  append(...children) {
    for (const child of children) {
      child.parent = this;
      this.children.push(child);
    }
  }
  remove() { this.parent.children = this.parent.children.filter(child => child !== this); }
  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }
  querySelectorAll(selector) {
    return this.children.flatMap(child => [
      ...(selector === 'input' && child.tagName === 'INPUT' ? [child] : []),
      ...child.querySelectorAll(selector)
    ]);
  }
}

async function setup(api = async () => {}) {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  const document = {
    getElementById: byId,
    createElement: tag => Object.assign(new Element(), { tagName: tag.toUpperCase() })
  };
  const PP = {
    byId,
    api,
    money: value => `$${value.toFixed(2)}`,
    setMessage: (id, message, kind = '') => {
      byId(id).textContent = message;
      byId(id).kind = kind;
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/quote.js'), 'utf8'),
    { document, PP });
  return { byId };
}

function fillPackage(row, { weight = '2.5', length = '30', width = '20', height = '10', quantity = '2' } = {}) {
  for (const input of row.querySelectorAll('input')) {
    input.value = ({ weight, length, width, height, quantity })[input.name];
  }
}

test('calculates a route and quote from the public homepage form', async () => {
  const requests = [];
  const { byId } = await setup(async (url, options) => {
    requests.push(url);
    if (url === '/api/booking-route') {
      return {
        distanceMeters: 12345,
        durationSeconds: 1500,
        points: [{ latitude: 51.5, longitude: -0.1 }, { latitude: 51.6, longitude: -0.2 }]
      };
    }
    return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5 };
  });
  byId('quoteOrigin').value = 'Pickup';
  byId('quoteDestination').value = 'Destination';
  fillPackage(byId('quotePackages').children[0]);

  await byId('quoteForm').dispatch('submit');

  assert.deepEqual(requests.sort(), ['/api/booking-route', '/api/pricing']);
  assert.equal(byId('quoteDistance').textContent, '12.3 km');
  assert.equal(byId('quoteWeight').textContent, '5 kg');
  assert.equal(byId('quotePrice').textContent, '$22.26');
  assert.equal(byId('quoteResult').hidden, false);
});

test('rejects incomplete parcel details without calling quote APIs', async () => {
  let calls = 0;
  const { byId } = await setup(async () => { calls++; });
  byId('quoteOrigin').value = 'Pickup';
  byId('quoteDestination').value = 'Destination';

  await byId('quoteForm').dispatch('submit');

  assert.equal(calls, 0);
  assert.match(byId('quoteMessage').textContent, /weight and dimensions/);
});

test('explains how to resolve an unavailable route service', async () => {
  const { byId } = await setup(async url => {
    if (url === '/api/booking-route') {
      const error = new Error('Service Unavailable');
      error.status = 503;
      throw error;
    }
    return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5 };
  });
  byId('quoteOrigin').value = 'Pickup';
  byId('quoteDestination').value = 'Destination';
  fillPackage(byId('quotePackages').children[0]);

  await byId('quoteForm').dispatch('submit');

  assert.match(byId('quoteMessage').textContent, /Admin settings/);
  assert.match(byId('quoteMessage').textContent, /connection to TomTom/);
});

test('discards a quote result after the visitor changes an address', async () => {
  let resolveRoute;
  const { byId } = await setup(async url => {
    if (url === '/api/booking-route') return new Promise(resolve => { resolveRoute = resolve; });
    return { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5 };
  });
  byId('quoteOrigin').value = 'Pickup';
  byId('quoteDestination').value = 'Destination';
  fillPackage(byId('quotePackages').children[0]);
  const pending = byId('quoteForm').dispatch('submit');
  byId('quoteOrigin').value = 'Updated pickup';
  await byId('quoteOrigin').dispatch('input');
  resolveRoute({
    distanceMeters: 12345,
    durationSeconds: 1500,
    points: [{ latitude: 51.5, longitude: -0.1 }, { latitude: 51.6, longitude: -0.2 }]
  });
  await pending;

  assert.equal(byId('quoteResult').hidden, true);
  assert.equal(byId('calculateQuote').disabled, false);
});
