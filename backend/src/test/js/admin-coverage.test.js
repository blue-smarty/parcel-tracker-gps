const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function setup({ user = { name: 'Admin' }, serviceArea = {}, withMap = false, failCouriers = false } = {}) {
  const elements = new Map();
  const createdElements = [];
  function byId(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', disabled: false, hidden: true, listeners: {}, options: [],
      get selectedOptions() { return this.options.filter(option => option.selected); },
      addEventListener(event, callback) { this.listeners[event] = callback; },
      append(...children) { this.children = [...(this.children || []), ...children]; },
      replaceChildren(...children) { this.options = children; },
      classList: { add() {} }
    });
    return elements.get(id);
  }
  const requests = [];
  const messages = {};
  const PP = {
    byId,
    setMessage(id, message, kind = '') { messages[id] = { message, kind }; },
    requireRole: async role => { assert.equal(role, 'ADMIN'); return user; },
    signOut() {},
    async api(route, options) {
      requests.push({ route, options });
      if (route === '/api/admin/service-area') {
        return options?.method === 'PUT' ? JSON.parse(options.body) : {
          coverageMode: 'UNRESTRICTED', states: [], councils: [], polygons: [], ...serviceArea
        };
      }
      if (route === '/api/admin/service-area/councils') {
        return [{ code: '25150', name: 'Melbourne', state: '2' }];
      }
      if (route === '/api/admin/couriers') {
        if (failCouriers) throw new Error('Unavailable');
        return [{ courierId: 'courier-1' }];
      }
      throw new Error(`Unexpected request ${route}`);
    }
  };
  const map = { setView() { return this; }, on(event, handler) { this[event] = handler; }, invalidateSize() {} };
  const layer = { addTo() { return this; }, clearLayers() {} };
  const tileRequests = [];
  const L = {
    map: () => map,
    tileLayer: (url, options) => { tileRequests.push({ url, options }); return layer; },
    layerGroup: () => layer,
    polygon: () => layer,
    polyline: () => layer,
    circleMarker: () => layer
  };
  function createElement(tag) {
    const element = {
      tag, value: '', textContent: '', listeners: {}, children: [],
      setAttribute() {},
      append(...children) { this.children.push(...children); },
      addEventListener(event, callback) { this.listeners[event] = callback; }
    };
    createdElements.push(element);
    return element;
  }
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-coverage.js'), 'utf8'), {
    PP, L, window: { confirm: () => true, L: withMap ? L : undefined, setTimeout: fn => fn() },
    document: { createElement }
  });
  await new Promise(resolve => setImmediate(resolve));
  return { byId, requests, messages, map, tileRequests, createdElements };
}

test('requires a verified admin before loading coverage', async () => {
  const { byId, requests } = await setup({ user: null });
  assert.equal(requests.length, 0);
  assert.equal(byId('coverageView').hidden, true);
});

test('loads and saves the coverage mode', async () => {
  const { byId, requests, messages } = await setup();
  assert.equal(byId('coverageView').hidden, false);
  byId('serviceAreaMode').value = 'MELBOURNE';
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body), {
    coverageMode: 'MELBOURNE', states: [], councils: [], polygons: [], polygonCouriers: []
  });
  assert.match(messages.serviceAreaMessage.message, /saved/);
});

test('saves selected states and council codes', async () => {
  const { byId, requests } = await setup();
  byId('coverageStates').options = [{ value: '2', selected: true }];
  byId('coverageCouncils').options[0].selected = true;
  byId('serviceAreaMode').value = 'CUSTOM';
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body), {
    coverageMode: 'CUSTOM', states: ['2'], councils: ['25150'], polygons: [], polygonCouriers: []
  });
});

test('draws an area, assigns a courier, and blocks unfinished drawings', async () => {
  const { byId, requests, messages, map, tileRequests, createdElements } = await setup({ withMap: true });
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
  const courierSelect = createdElements.find(element => element.tag === 'select');
  courierSelect.value = 'courier-1';
  courierSelect.listeners.change();
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const request = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(request.options.body).polygons, [[[-38, 144], [-38, 145], [-37, 145]]]);
  assert.deepEqual(JSON.parse(request.options.body).polygonCouriers, ['courier-1']);
});

test('preserves assigned couriers when courier accounts cannot be loaded', async () => {
  const { byId, requests } = await setup({
    failCouriers: true,
    serviceArea: {
      coverageMode: 'CUSTOM',
      polygons: [[[-38, 144], [-38, 145], [-37, 145]]],
      polygonCouriers: ['courier-1']
    }
  });
  await byId('serviceAreaForm').listeners.submit({ preventDefault() {} });
  const save = requests.find(item => item.route === '/api/admin/service-area' && item.options?.method === 'PUT');
  assert.deepEqual(JSON.parse(save.options.body).polygonCouriers, ['courier-1']);
});
