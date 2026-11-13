(() => {
  const { byId, api, escapeHtml, text, statusText, detail, setMessage, signOut, requireRole } = PP;
  const nextSteps = {
    CREATED: [['PICKED_UP', 'Mark picked up']],
    ACCEPTED: [['PICKED_UP', 'Mark picked up']],
    PICKED_UP: [['IN_TRANSIT', 'Mark in transit']],
    IN_TRANSIT: [['OUT_FOR_DELIVERY', 'Out for delivery']],
    OUT_FOR_DELIVERY: []
  };
  let courierId;
  let deliveries = [];
  let busy = false;

  const base = () => `/api/couriers/${encodeURIComponent(courierId)}`;
  const deliveryUrl = (id, suffix) => `${base()}/deliveries/${encodeURIComponent(id)}${suffix}`;
  const put = (url, body) => api(url, { method: 'PUT', body: JSON.stringify(body) });

  function card(parcel) {
    const id = escapeHtml(parcel.id);
    const status = parcel.status;
    let actions = '';
    if (status === 'CREATED') {
      actions += `<button class="button button-primary" data-action="accept" data-id="${id}">Accept</button>
        <button class="button button-quiet" data-action="reject" data-id="${id}">Reject</button>`;
    }
    (nextSteps[status] || []).forEach(([next, label]) => {
      actions += `<button class="button button-secondary" data-action="status" data-next="${next}" data-id="${id}">${label}</button>`;
    });
    if (status === 'OUT_FOR_DELIVERY') {
      actions += `<input data-barcode="${id}" maxlength="120" placeholder="Barcode or recipient signature" aria-label="Barcode or recipient signature">
        <input data-note="${id}" maxlength="400" placeholder="Drop-off note (optional)" aria-label="Drop-off note">
        <button class="button button-primary" data-action="deliver" data-id="${id}">Complete delivery</button>`;
    }
    if (nextSteps[status]) {
      actions += `<button class="button button-quiet" data-action="status" data-next="FAILED" data-id="${id}">Mark failed</button>
        <button class="button button-quiet" data-action="location" data-id="${id}">Share my location</button>`;
    }
    return `<div class="panel" style="padding:18px;margin-bottom:14px">
      <div class="panel-heading"><div><p class="eyebrow">${escapeHtml(text(parcel.trackingNumber))}</p><h3>${escapeHtml(text(parcel.recipient))}</h3></div>
        <span class="count-pill">${escapeHtml(statusText(status))}</span></div>
      ${detail('Pickup', parcel.pickupAddress)}${detail('Destination', parcel.destination)}
      ${detail('Contact', parcel.customerContact)}${detail('Package', parcel.packageSize)}
      <div class="assignment-controls" style="margin-top:12px;flex-wrap:wrap">${actions}</div>
    </div>`;
  }

  function render() {
    byId('deliveryList').innerHTML = deliveries.length
      ? deliveries.map(card).join('')
      : '<div class="panel"><div class="empty-state">No active deliveries assigned to you.</div></div>';
  }

  async function load(quiet) {
    if (busy) return;
    try {
      deliveries = await api(`${base()}/deliveries`);
      render();
      if (!quiet) setMessage('courierMessage', '');
    } catch (error) {
      setMessage('courierMessage', error.message, 'error');
    }
  }

  function position() {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) return reject(new Error('Location is not available in this browser.'));
      navigator.geolocation.getCurrentPosition(
        p => resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude, timestamp: new Date().toISOString() }),
        () => reject(new Error('Could not read your location. Check browser permissions.')),
        { enableHighAccuracy: true, timeout: 10000 });
    });
  }

  async function perform(button) {
    const { action, id, next } = button.dataset;
    if (action === 'accept') return put(deliveryUrl(id, '/decision'), { accepted: true });
    if (action === 'reject') return put(deliveryUrl(id, '/decision'), { accepted: false });
    if (action === 'status') return put(deliveryUrl(id, '/status'), { status: next });
    if (action === 'location') {
      return api(deliveryUrl(id, '/location'), { method: 'POST', body: JSON.stringify(await position()) });
    }
    if (action === 'deliver') {
      const note = document.querySelector(`[data-note="${CSS.escape(id)}"]`)?.value.trim() || null;
      const barcode = document.querySelector(`[data-barcode="${CSS.escape(id)}"]`)?.value.trim();
      if (!barcode) throw new Error('Enter the parcel barcode or recipient signature to complete delivery.');
      return api(deliveryUrl(id, '/proof'), { method: 'POST', body: JSON.stringify({ barcode, dropOffNote: note }) });
    }
  }

  async function onClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button || busy) return;
    busy = true;
    button.disabled = true;
    try {
      await perform(button);
      busy = false;
      await load(true);
      setMessage('courierMessage', 'Updated.', 'success');
    } catch (error) {
      setMessage('courierMessage', error.message, 'error');
    } finally {
      busy = false;
      button.disabled = false;
    }
  }

  async function init() {
    const current = await requireRole('COURIER');
    if (!current) return;
    courierId = current.username;
    byId('accountName').textContent = current.name || current.username;
    byId('courierView').hidden = false;
    byId('signOutButton').addEventListener('click', signOut);
    byId('refreshButton').addEventListener('click', () => load(false));
    byId('deliveryList').addEventListener('click', onClick);
    await load(false);
    setInterval(() => load(true), 30000);
  }

  init();
})();
