const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = name => fs.readFileSync(path.join(__dirname, '../../main/resources/static', name), 'utf8');

test('enables a subscription and removes it when signing out', async () => {
  const handlers = {};
  const requests = [];
  let unsubscribed = false;
  const subscription = {
    endpoint: 'https://fcm.googleapis.com/fcm/send/test',
    options: {},
    toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/fcm/send/test', keys: { p256dh: 'key', auth: 'auth' } }),
    unsubscribe: async () => { unsubscribed = true; return true; }
  };
  const registration = { pushManager: {
    getSubscription: async () => subscription,
    subscribe: async () => subscription
  } };
  const window = { isSecureContext: true, PushManager: {}, Notification: {} };
  const context = {
    window, Notification: { requestPermission: async () => 'granted' }, Uint8Array,
    atob: value => Buffer.from(value, 'base64').toString('binary'),
    navigator: { serviceWorker: {
      register: async () => registration, getRegistration: async () => registration
    } },
    PP: {
      byId: id => ({ addEventListener: (_name, fn) => { handlers[id] = fn; } }),
      setMessage() {},
      api: async (url, options) => {
        requests.push({ url, options });
        return url.endsWith('/push-key') ? { publicKey: 'AQ' } : null;
      }
    }
  };
  vm.runInNewContext(source('courier-push.js'), context);
  window.CourierPush.init('courier-1');
  await handlers.enablePushButton();
  assert.match(requests[1].url, /courier-1\/push-subscription$/);
  let signedOut = false;
  await window.CourierPush.signOut(() => { signedOut = true; });
  assert.match(requests[2].url, /courier-1\/push-unsubscribe$/);
  assert.equal(unsubscribed, true);
  assert.equal(signedOut, true);
});

test('service worker shows pushed messages and opens the courier page', async () => {
  const handlers = {};
  const calls = [];
  const self = {
    addEventListener: (event, fn) => { handlers[event] = fn; },
    registration: { showNotification: async (title, options) => { calls.push([title, options.body]); } }
  };
  const clients = { openWindow: async url => { calls.push(url); } };
  vm.runInNewContext(source('courier-sw.js'), { self, clients });
  let promise;
  handlers.push({ data: { text: () => 'New assignment' }, waitUntil: p => { promise = p; } });
  await promise;
  assert.deepEqual(calls[0], ['ParcelPilot', 'New assignment']);
  handlers.notificationclick({ notification: { close() {} }, waitUntil: p => { promise = p; } });
  await promise;
  assert.equal(calls[1], '/courier.html');
});

test('admin is told when no subscribed browser accepted a message', async () => {
  const elements = {
    courierMessageForm: { addEventListener: (_name, handler) => { elements.submit = handler; } },
    messageCourierId: { value: ' courier-1 ' },
    courierMessageText: { value: ' Please call dispatch ' }
  };
  let request;
  let result;
  vm.runInNewContext(source('admin-push.js'), { PP: {
    byId: id => elements[id],
    api: async (url, options) => { request = { url, body: JSON.parse(options.body) }; return { accepted: 0 }; },
    setMessage: (...args) => { result = args; }
  } });
  const button = {};
  await elements.submit({ preventDefault() {}, submitter: button });
  assert.equal(request.url, '/api/admin/couriers/courier-1/push-message');
  assert.equal(request.body.message, 'Please call dispatch');
  assert.match(result[1], /No push service accepted/);
  assert.equal(result[2], 'error');
  assert.equal(elements.courierMessageText.value, ' Please call dispatch ');
  assert.equal(button.disabled, false);
});
