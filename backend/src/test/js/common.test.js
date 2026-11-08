const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor(tagName) {
    this.tagName = tagName;
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    this.hidden = false;
    this.className = '';
    this.value = '';
    this.textContent = '';
    this.classList = {
      toggle: (name, force) => {
        const names = new Set(this.className.split(/\s+/).filter(Boolean));
        if (force) names.add(name);
        else names.delete(name);
        this.className = [...names].join(' ');
      }
    };
  }

  appendChild(child) {
    child.parentNode?.removeChild(child);
    this.children.push(child);
    child.parentNode = this;
    return child;
  }

  insertBefore(child, reference) {
    child.parentNode?.removeChild(child);
    const index = this.children.indexOf(reference);
    this.children.splice(index < 0 ? this.children.length : index, 0, child);
    child.parentNode = this;
  }

  removeChild(child) {
    this.children = this.children.filter(item => item !== child);
    child.parentNode = null;
  }

  replaceChildren(...children) {
    this.children.forEach(child => { child.parentNode = null; });
    this.children = [];
    children.forEach(child => this.appendChild(child));
  }

  remove() { this.parentNode?.removeChild(this); }
  addEventListener(type, callback) { this.listeners[type] = callback; }
  removeEventListener(type) { delete this.listeners[type]; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  removeAttribute(name) { delete this.attributes[name]; }
  dispatchEvent(event) { this.listeners[event.type]?.(event); }
  scrollIntoView() {}
  get childElementCount() { return this.children.length; }

  querySelectorAll(selector) {
    const role = selector.match(/^\[role="([^"]+)"\]$/)?.[1];
    return this.children.flatMap(child => [
      ...(child.getAttribute?.('role') === role ? [child] : []),
      ...child.querySelectorAll(selector)
    ]);
  }
}

async function setup(fetch) {
  const field = new Element('input');
  field.id = 'address';
  const label = new Element('label');
  label.appendChild(field);
  const document = {
    getElementById: id => id === field.id ? field : null,
    createElement: tagName => new Element(tagName)
  };
  const window = { setTimeout };
  const context = {
    window, document, fetch, Headers, AbortController, Event, setTimeout, clearTimeout,
    console: { warn() {} },
    sessionStorage: { getItem: () => null },
    localStorage: { getItem: () => null, setItem() {} }
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../../main/resources/static/common.js'), 'utf8'),
    context
  );
  window.PP.setupAddressAutocomplete('address', { debounceMs: 0 });
  field.listeners.input({ target: { value: '10 Downing Street' } });
  await new Promise(resolve => setTimeout(resolve, 50));
  return { field, status: field.parentNode.children[1], list: field.parentNode.children[2] };
}

test('loads and selects suggestions from the authenticated app API', async () => {
  let requestedUrl;
  const { field, list } = await setup(async url => {
    requestedUrl = url;
    return { ok: true, json: async () => ['10 Downing Street, London'] };
  });
  assert.match(requestedUrl, /^\/api\/address-suggestions\?q=/);
  assert.equal(list.hidden, false);
  assert.equal(list.children[0].textContent, '10 Downing Street, London');
  list.children[0].listeners.click();
  assert.equal(field.value, '10 Downing Street, London');
  assert.equal(field.getAttribute('aria-expanded'), 'false');
});

test('shows a manual-entry fallback when address lookup fails', async () => {
  const { status, list } = await setup(async () => { throw new Error('offline'); });
  assert.equal(status.hidden, false);
  assert.match(status.textContent, /unavailable/);
  assert.equal(list.hidden, true);
});
