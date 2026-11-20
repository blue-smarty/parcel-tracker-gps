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
  let routeKey = null;
  let routeRequest = 0;

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
      ${detail('Service', PP.serviceLevelName(parcel.deliverySpeed) || 'Standard')}
      ${detail('Target', parcel.targetDeadline ? new Date(parcel.targetDeadline).toLocaleString()
        : parcel.targetStart === 'PICKUP' ? `${parcel.targetMinutes} minutes after pickup` : 'None')}
      <div class="assignment-controls" style="margin-top:12px;flex-wrap:wrap">${actions}</div>
    </div>`;
  }

  function render() {
    byId('deliveryList').innerHTML = deliveries.length
      ? deliveries.map(card).join('')
      : '<div class="panel"><div class="empty-state">No active deliveries assigned to you.</div></div>';
  }

  function stopsKey(parcels) {
    return JSON.stringify(parcels.flatMap(parcel => {
      const pickup = parcel.status === 'CREATED' || parcel.status === 'ACCEPTED';
      const target = [parcel.targetDeadline ?? null, parcel.targetStart ?? null, parcel.targetMinutes ?? null];
      return [
        ...(pickup ? [[parcel.id, 'PICKUP', parcel.pickupAddress?.trim() ?? null, ...target]] : []),
        [parcel.id, 'DELIVERY', parcel.destination?.trim() ?? null, ...target]
      ];
    }).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))));
  }

  async function optimizeRoute(force = false) {
    const key = stopsKey(deliveries);
    if (!force && key === routeKey) return;
    routeKey = key;
    const request = ++routeRequest;
    byId('routeStops').innerHTML = '';
    if (!deliveries.length) {
      setMessage('routeMessage', 'No active deliveries to route.');
      return;
    }
    if (deliveries.length > 9) {
      setMessage('routeMessage', 'Route optimization supports up to 9 active deliveries.', 'error');
      return;
    }
    setMessage('routeMessage', 'Calculating suggested route…');
    try {
      const location = await position();
      if (request !== routeRequest) return;
      const result = await api(`${base()}/route`, {
        method: 'POST', body: JSON.stringify({ latitude: location.latitude, longitude: location.longitude })
      });
      if (request !== routeRequest) return;
      const actual = JSON.stringify(result.stops.map(stop => [stop.parcelId, stop.type, stop.address,
        stop.targetDeadline ?? null, stop.targetStart ?? null, stop.targetMinutes ?? null])
        .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))));
      if (actual !== key) throw new Error('Deliveries changed while calculating. Refresh and try again.');
      byId('routeStops').innerHTML = result.stops.map(stop =>
        `<li><strong>${escapeHtml(stop.type === 'PICKUP' ? 'Pickup' : 'Delivery')} · ${escapeHtml(text(stop.trackingNumber))}</strong> — ${escapeHtml(text(stop.address))} · estimated completion ${escapeHtml(new Date(stop.estimatedArrival).toLocaleString())}${stop.late ? ' · estimated late' : ''}</li>`).join('');
      setMessage('routeMessage', `Suggested distance: ${(result.distanceMeters / 1000).toFixed(1)} km · ${result.estimatedLateDeliveries} estimated late. Estimates are not guarantees.`, 'success');
    } catch (error) {
      if (request === routeRequest) setMessage('routeMessage', `Could not optimize route: ${error.message}`, 'error');
    }
  }

  async function load(quiet) {
    if (busy) return;
    try {
      deliveries = await api(`${base()}/deliveries`);
      render();
      optimizeRoute();
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
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
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
    byId('courierView').classList.add('active-view');
    CourierPush.init(courierId);
    byId('signOutButton').addEventListener('click', () => CourierPush.signOut(signOut));
    byId('refreshButton').addEventListener('click', () => load(false));
    byId('optimizeRouteButton').addEventListener('click', () => optimizeRoute(true));
    byId('deliveryList').addEventListener('click', onClick);
    await load(false);
    setInterval(() => load(true), 30000);
  }

  init();
})();
