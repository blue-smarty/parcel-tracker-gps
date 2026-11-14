window.CourierPush = (() => {
  const { api, byId, setMessage } = PP;
  let courierId;
  const path = suffix => `/api/couriers/${encodeURIComponent(courierId)}/${suffix}`;

  function supported() {
    return window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  }

  function decodeKey(value) {
    const raw = atob(value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '='));
    return Uint8Array.from(raw, char => char.charCodeAt(0));
  }

  async function enable() {
    if (!supported()) throw new Error('Push requires HTTPS and a browser that supports notifications and service workers.');
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') throw new Error('Notification permission was not granted. Check your browser settings.');
    const { publicKey } = await api(path('push-key'));
    const registration = await navigator.serviceWorker.register('/courier-sw.js');
    let subscription = await registration.pushManager.getSubscription();
    const applicationServerKey = decodeKey(publicKey);
    if (subscription && subscription.options.applicationServerKey) {
      const currentKey = new Uint8Array(subscription.options.applicationServerKey);
      if (currentKey.length !== applicationServerKey.length || currentKey.some((byte, i) => byte !== applicationServerKey[i])) {
        await api(path('push-unsubscribe'), { method: 'POST', body: JSON.stringify({ endpoint: subscription.endpoint }) });
        if (!await subscription.unsubscribe()) throw new Error('Could not replace the old push subscription.');
        subscription = null;
      }
    }
    if (!subscription) {
      subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey });
    }
    await api(path('push-subscription'), { method: 'POST', body: JSON.stringify(subscription.toJSON()) });
    setMessage('pushMessage', 'Notifications enabled on this browser.', 'success');
  }

  async function disable() {
    if (!supported()) return;
    const registration = await navigator.serviceWorker.getRegistration('/');
    const subscription = registration && await registration.pushManager.getSubscription();
    if (!subscription) return;
    await api(path('push-unsubscribe'), { method: 'POST', body: JSON.stringify({ endpoint: subscription.endpoint }) });
    if (!await subscription.unsubscribe()) throw new Error('Could not disable browser notifications.');
  }

  function init(id) {
    courierId = id;
    byId('enablePushButton').addEventListener('click', async () => {
      try { await enable(); } catch (error) { setMessage('pushMessage', error.message, 'error'); }
    });
    byId('disablePushButton').addEventListener('click', async () => {
      try {
        await disable();
        setMessage('pushMessage', 'Notifications disabled on this browser.', 'success');
      } catch (error) { setMessage('pushMessage', error.message, 'error'); }
    });
    if (!supported()) setMessage('pushMessage', 'Push requires HTTPS and a supported browser.', 'error');
  }

  async function signOut(complete) {
    try {
      await disable();
      complete();
    } catch (error) {
      setMessage('pushMessage', `Could not disable notifications before sign-out: ${error.message}`, 'error');
    }
  }

  return { init, signOut };
})();
