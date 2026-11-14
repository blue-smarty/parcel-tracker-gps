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
    this.classList = { add() {} };
  }
  addEventListener(type, callback) { this.listeners[type] = callback; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  querySelector() { return this.button ||= new Element(); }
  querySelectorAll() { return this.children.flatMap(row => row.children.filter(child => child.type === 'button')); }
  reset() {}
  focus() {}
}

async function setup(role, api) {
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  const messages = {};
  const PP = {
    byId, api, signOut() {}, setupAddressAutocomplete() {},
    setMessage(id, message) { messages[id] = message; },
    requireRole: async () => ({ role, name: 'User' })
  };
  const document = {
    createElement: () => new Element(),
    createTextNode: text => ({ textContent: text })
  };
  byId('bookContent').hidden = true;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/address-book.js'), 'utf8'),
    { PP, document, window: { confirm: () => true } });
  await new Promise(resolve => setImmediate(resolve));
  return { byId, messages };
}

test('customers can save, edit, and delete entries without choosing an owner', async () => {
  const calls = [];
  const { byId } = await setup('CUSTOMER', async (url, options = {}) => {
    calls.push({ url, ...options });
    if (!options.method) return [];
    if (options.method === 'DELETE') return null;
    return { id: 'a1', ...JSON.parse(options.body) };
  });
  byId('addressLabel').value = '<Home>';
  byId('addressValue').value = '1 Main';
  await byId('addressForm').listeners.submit({ preventDefault() {} });
  assert.equal(calls[1].url, '/api/address-book');
  assert.equal(byId('addressList').children[0].children[0].children[0].textContent, '<Home>');
  byId('addressList').children[0].children[1].listeners.click();
  byId('addressValue').value = '2 Oak';
  await byId('addressForm').listeners.submit({ preventDefault() {} });
  assert.equal(calls[2].method, 'PUT');
  assert.equal(calls[2].url, '/api/address-book/a1');
  await byId('addressList').children[0].children[2].listeners.click();
  assert.equal(calls[3].method, 'DELETE');
  assert.equal(byId('addressList').children[0].textContent, 'No saved addresses yet.');
});

test('staff open a customer book and failures keep it closed', async () => {
  const calls = [];
  const { byId, messages } = await setup('DISPATCHER', async url => {
    calls.push(url);
    if (url.includes('missing')) throw new Error('Not found');
    return [];
  });
  assert.equal(byId('bookContent').hidden, true);
  byId('customerEmail').value = ' Alice+test@Example.com ';
  await byId('customerLookup').listeners.submit({ preventDefault() {} });
  assert.equal(calls[0], '/api/address-book?customer=alice%2Btest%40example.com');
  assert.equal(byId('bookContent').hidden, false);
  byId('customerEmail').value = 'missing@example.com';
  await byId('customerLookup').listeners.submit({ preventDefault() {} });
  assert.equal(byId('bookContent').hidden, true);
  assert.match(messages.addressMessage, /Not found/);
});
