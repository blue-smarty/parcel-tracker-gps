const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/website-branding.js'), 'utf8');

async function apply(data) {
  const properties = {};
  const brand = {
    attributes: {},
    setAttribute(key, value) { this.attributes[key] = value; },
    replaceChildren(...children) { this.children = children; }
  };
  const footerText = { nodeType: 3, textContent: 'PARCELPILOT · DEMO' };
  const document = {
    title: 'ParcelPilot — Sign in',
    head: { append(element) { this.child = element; } },
    documentElement: { style: { setProperty(key, value) { properties[key] = value; } } },
    createElement() { return { style: {} }; },
    querySelectorAll(selector) {
      return selector === '.brand' ? [brand] : [{ childNodes: [footerText] }];
    }
  };
  const errors = [];
  vm.runInNewContext(script, {
    document, Node: { TEXT_NODE: 3 }, console: { error: (...args) => errors.push(args) },
    fetch: async () => ({ ok: true, json: async () => data })
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(errors.length, 0);
  return { document, brand, footerText, properties };
}

test('applies saved identity, logo, colors and font without parsing the name as HTML', async () => {
  const result = await apply({
    configured: true,
    fontFamily: 'Georgia',
    branding: { appName: '<Acme>', primaryColor: '#112233', accentColor: '#445566',
      logoContentType: 'image/png', logoBase64: 'abcd' }
  });
  assert.equal(result.document.title, '<Acme> — Sign in');
  assert.equal(result.properties['--navy'], '#112233');
  assert.equal(result.properties['--teal'], '#445566');
  assert.equal(result.brand.children[0].src, 'data:image/png;base64,abcd');
  assert.equal(result.brand.children[1].textContent, '<Acme>');
  assert.equal(result.footerText.textContent, '<Acme> · DEMO');
  assert.match(result.document.head.child.textContent, /Georgia/);
});

test('preserves default identity when no identity has been saved', async () => {
  const result = await apply({ configured: false, branding: {}, fontFamily: null });
  assert.equal(result.document.title, 'ParcelPilot — Sign in');
  assert.equal(result.brand.children, undefined);
  assert.equal(result.document.head.child, undefined);
});

test('replaces a removed logo with the business initial and supports Arial override', async () => {
  const result = await apply({
    configured: true, fontFamily: 'Arial',
    branding: { appName: 'Acme', primaryColor: '#112233', accentColor: '#445566' }
  });
  assert.equal(result.brand.children[0].textContent, 'A');
  assert.equal(result.brand.children[0].className, 'brand-mark');
  assert.match(result.document.head.child.textContent, /Arial/);
});
