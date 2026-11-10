(() => {
  const { byId, money, api, setMessage } = PP;

  // Sizes are ordered so the largest selected item sets the parcel's package size.
  const catalog = [
    { id: 'documents', name: 'Documents', hint: 'Letters, contracts, A4 envelopes', size: 'SMALL', weight: 0.5, dims: [32, 23, 2], icon: '✉️' },
    { id: 'satchel', name: 'Satchel', hint: 'Laptops, bags, briefcases', size: 'SMALL', weight: 3, dims: [50, 30, 20], icon: '💼' },
    { id: 'box', name: 'Small box', hint: 'Clothing, small appliances, gifts', size: 'MEDIUM', weight: 5, dims: [40, 30, 30], icon: '📦' },
    { id: 'fragile', name: 'Fragile', hint: 'Cakes, flowers, pottery', size: 'MEDIUM', weight: 4, dims: [40, 40, 40], icon: '🎂' },
    { id: 'large', name: 'Large box', hint: 'Frames, chairs, flat-pack items', size: 'LARGE', weight: 15, dims: [80, 60, 50], icon: '🗃️' },
    { id: 'oversized', name: 'Oversized', hint: 'Pallets, plants, bulky goods', size: 'LARGE', weight: 30, dims: [120, 80, 80], icon: '🛋️' }
  ];
  const sizeRank = { SMALL: 0, MEDIUM: 1, LARGE: 2 };
  const maxQuantity = 20;
  let pricing = { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75 };
  const lines = new Map();
  let user = null;

  const positive = value => Number.isFinite(value) && value > 0;
  const kg = value => `${(Math.round(value * 100) / 100).toLocaleString()} kg`;
  const speed = () => document.querySelector('input[name="speed"]:checked').value;

  function addItem(id, change) {
    const item = catalog.find(entry => entry.id === id);
    const line = lines.get(id) || { id, quantity: 0, weight: item.weight, length: item.dims[0], width: item.dims[1], height: item.dims[2] };
    line.quantity = Math.min(maxQuantity, Math.max(0, line.quantity + change));
    if (line.quantity === 0) lines.delete(id);
    else lines.set(id, line);
    renderTiles();
    renderManifest();
    updateSummary();
  }

  function renderTiles() {
    const host = byId('itemTiles');
    host.replaceChildren(...catalog.map(item => {
      const quantity = lines.get(item.id)?.quantity || 0;
      const tile = document.createElement('div');
      tile.className = `item-tile${quantity ? ' selected' : ''}`;
      const pick = document.createElement('button');
      pick.type = 'button';
      pick.className = 'item-pick';
      pick.setAttribute('aria-label', `Add ${item.name}`);
      pick.innerHTML = '<span class="item-icon" aria-hidden="true"></span><strong></strong><small></small>';
      pick.querySelector('.item-icon').textContent = item.icon;
      pick.querySelector('strong').textContent = item.name;
      pick.querySelector('small').textContent = item.hint;
      pick.addEventListener('click', () => addItem(item.id, 1));
      tile.append(pick);
      if (quantity) {
        const badge = document.createElement('span');
        badge.className = 'item-count';
        badge.textContent = `×${quantity}`;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'item-remove';
        remove.setAttribute('aria-label', `Remove one ${item.name}`);
        remove.textContent = '−';
        remove.addEventListener('click', () => addItem(item.id, -1));
        tile.append(badge, remove);
      }
      return tile;
    }));
  }

  function numberField(label, line, key, step) {
    const wrapper = document.createElement('label');
    wrapper.textContent = label;
    const input = document.createElement('input');
    input.type = 'number';
    input.min = step;
    input.step = step;
    input.required = true;
    input.value = line[key];
    input.addEventListener('input', () => {
      line[key] = Number(input.value);
      updateSummary();
    });
    wrapper.append(input);
    return wrapper;
  }

  function renderManifest() {
    byId('manifestStep').hidden = lines.size === 0;
    byId('manifestRows').replaceChildren(...[...lines.values()].map(line => {
      const item = catalog.find(entry => entry.id === line.id);
      const row = document.createElement('div');
      row.className = 'manifest-row';
      const title = document.createElement('strong');
      title.textContent = `${item.icon} ${item.name} × ${line.quantity}`;
      const fields = document.createElement('div');
      fields.className = 'manifest-fields';
      fields.append(
        numberField('Weight each (kg)', line, 'weight', '0.1'),
        numberField('Length (cm)', line, 'length', '0.1'),
        numberField('Width (cm)', line, 'width', '0.1'),
        numberField('Height (cm)', line, 'height', '0.1')
      );
      row.append(title, fields);
      return row;
    }));
  }

  function totals() {
    const all = [...lines.values()];
    const weight = all.reduce((sum, line) => sum + line.quantity * line.weight, 0);
    const valid = all.length > 0 && all.every(line => [line.weight, line.length, line.width, line.height].every(positive));
    return { all, weight, valid };
  }

  function estimate(distance, weight) {
    const multiplier = speed() === 'SAME_DAY' ? pricing.sameDayMultiplier : 1;
    return Math.round((pricing.baseFee + distance * pricing.perKmRate + weight * pricing.perKgRate) * multiplier * 100) / 100;
  }

  function updateSummary() {
    const { all, weight, valid } = totals();
    const distance = Number(byId('routeDistance').value);
    const list = byId('ticketItems');
    list.replaceChildren(...(all.length ? all.map(line => {
      const item = document.createElement('li');
      item.textContent = `${line.quantity} × ${catalog.find(entry => entry.id === line.id).name}`;
      return item;
    }) : [Object.assign(document.createElement('li'), { className: 'ticket-empty', textContent: 'No items yet' })]));
    byId('ticketWeight').textContent = valid ? kg(weight) : '—';
    byId('ticketDistance').textContent = positive(distance) ? `${distance} km` : '—';
    byId('ticketSpeed').textContent = speed() === 'SAME_DAY' ? 'Same-day' : 'Scheduled';
    byId('ticketPrice').textContent = valid && positive(distance) ? money(estimate(distance, weight)) : '$—';
    const scheduled = speed() === 'SCHEDULED';
    byId('scheduleFields').hidden = !scheduled;
    byId('scheduleDate').required = scheduled;
    byId('scheduleWindow').required = scheduled;
  }

  function buildPayload() {
    const { all, weight, valid } = totals();
    if (!all.length) throw new Error('Choose at least one item to send.');
    if (!valid) throw new Error('Enter a weight and size greater than zero for each item.');
    const distance = Number(byId('routeDistance').value);
    if (!positive(distance)) throw new Error('Enter the route distance in kilometres.');
    const fields = {
      sender: byId('pickupName').value.trim(),
      customerContact: byId('pickupContact').value.trim(),
      pickupAddress: byId('pickupAddress').value.trim(),
      recipient: byId('recipientName').value.trim(),
      destination: byId('deliveryAddress').value.trim()
    };
    if (Object.values(fields).some(value => !value)) throw new Error('Complete the pickup and delivery details.');
    const scheduled = speed() === 'SCHEDULED';
    if (scheduled && (!byId('scheduleDate').value || !byId('scheduleWindow').value)) {
      throw new Error('Choose a delivery date and time window.');
    }
    // Items are combined into one parcel: widest footprint, stacked height, largest size class.
    return {
      ...fields,
      packageSize: all.map(line => catalog.find(entry => entry.id === line.id).size)
        .reduce((best, size) => sizeRank[size] > sizeRank[best] ? size : best, 'SMALL'),
      widthCm: Math.max(...all.map(line => line.width)),
      lengthCm: Math.max(...all.map(line => line.length)),
      heightCm: all.reduce((sum, line) => sum + line.quantity * line.height, 0),
      weightKg: weight,
      distanceKm: distance,
      deliverySpeed: speed(),
      paymentMethod: byId('paymentChoice').value,
      scheduledDate: scheduled ? byId('scheduleDate').value : null,
      scheduledWindow: scheduled ? byId('scheduleWindow').value : null
    };
  }

  async function submit(event) {
    event.preventDefault();
    const button = byId('submitBooking');
    if (button.disabled) return;
    let payload;
    try {
      payload = buildPayload();
    } catch (error) {
      setMessage('builderMessage', error.message, 'error');
      return;
    }
    button.disabled = true;
    setMessage('builderMessage', 'Booking your delivery…');
    try {
      const parcel = await api('/api/parcels', { method: 'POST', body: JSON.stringify(payload) });
      byId('doneTracking').textContent = parcel.trackingNumber;
      byId('doneText').textContent = `${money(parcel.estimatedPrice)} is a demo estimate; no payment was collected.`;
      byId('bookingDone').hidden = false;
      byId('builderForm').hidden = true;
      document.querySelector('.booking-ticket').hidden = true;
      setMessage('builderMessage', '');
    } catch (error) {
      setMessage('builderMessage', `Could not book delivery: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  const route = { ready: false, loading: null, map: null, line: null, request: 0 };

  function setRouteSummary(message, kind = '') {
    const element = byId('routeSummary');
    element.textContent = message;
    element.className = `route-summary${kind ? ` ${kind}` : ''}`;
  }

  function loadGoogleMaps(apiKey) {
    if (route.loading) return route.loading;
    route.loading = new Promise((resolve, reject) => {
      window.gm_authFailure = () => setRouteSummary('Google Maps rejected the API key. Enter the distance manually.', 'error');
      window.initGoogleMaps = resolve;
      const script = document.createElement('script');
      script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&loading=async&v=weekly&callback=initGoogleMaps`;
      script.async = true;
      script.onerror = () => reject(new Error('Google Maps could not be loaded.'));
      document.head.append(script);
    });
    route.loading.catch(() => { route.loading = null; });
    return route.loading;
  }

  async function calculateRoute() {
    const origin = byId('pickupAddress').value.trim();
    const destination = byId('deliveryAddress').value.trim();
    if (!route.ready || !origin || !destination) return;
    const requestId = ++route.request;
    byId('calculateRouteButton').disabled = true;
    setRouteSummary('Calculating route…');
    try {
      const { Route } = await google.maps.importLibrary('routes');
      const { routes } = await Route.computeRoutes({
        origin, destination, travelMode: 'DRIVING',
        fields: ['distanceMeters', 'durationMillis', 'path']
      });
      if (requestId !== route.request) return;
      const best = routes?.[0];
      if (!best || !positive(best.distanceMeters)) throw new Error('No driving route found.');
      const km = Math.max(0.1, Math.round(best.distanceMeters / 100) / 10);
      byId('routeDistance').value = km;
      byId('routeDistance').dispatchEvent(new Event('input', { bubbles: true }));
      const minutes = Math.max(1, Math.round((best.durationMillis || 0) / 60000));
      setRouteSummary(`Driving distance ${km} km · about ${minutes} min`);
      await drawRoute(best.path || []);
    } catch (error) {
      if (requestId !== route.request) return;
      console.warn('Route lookup failed.', error);
      byId('routeMap').hidden = true;
      setRouteSummary('Could not calculate a route for these addresses. Check them or enter the distance manually.', 'error');
    } finally {
      if (requestId === route.request) byId('calculateRouteButton').disabled = false;
    }
  }

  async function drawRoute(path) {
    const { Map: GoogleMap, Polyline } = await google.maps.importLibrary('maps');
    const points = path.map(point => ({ lat: point.lat, lng: point.lng }));
    const container = byId('routeMap');
    container.hidden = false;
    if (!route.map) route.map = new GoogleMap(container, { disableDefaultUI: true, zoomControl: true, center: points[0], zoom: 10 });
    route.line?.setMap(null);
    route.line = new Polyline({ map: route.map, path: points, strokeColor: '#e9904b', strokeWeight: 4 });
    const bounds = new google.maps.LatLngBounds();
    points.forEach(point => bounds.extend(point));
    route.map.fitBounds(bounds, 30);
  }

  async function setupRouteLookup() {
    try {
      const { apiKey } = await api('/api/maps-config');
      if (!apiKey) return;
      await loadGoogleMaps(apiKey);
      route.ready = true;
      byId('routeTools').hidden = false;
      byId('calculateRouteButton').addEventListener('click', calculateRoute);
      ['pickupAddress', 'deliveryAddress'].forEach(id => byId(id).addEventListener('change', calculateRoute));
    } catch (error) {
      console.warn('Google Maps route lookup is unavailable.', error);
    }
  }

  function reset() {
    lines.clear();
    byId('builderForm').reset();
    byId('pickupName').value = user.name;
    byId('pickupContact').value = user.username;
    byId('builderForm').hidden = false;
    document.querySelector('.booking-ticket').hidden = false;
    byId('bookingDone').hidden = true;
    byId('routeMap').hidden = true;
    setRouteSummary('');
    renderTiles();
    renderManifest();
    updateSummary();
  }

  async function initialize() {
    user = await PP.requireRole('CUSTOMER');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('builderForm').addEventListener('submit', submit);
    byId('builderForm').addEventListener('input', updateSummary);
    byId('builderForm').addEventListener('change', updateSummary);
    byId('bookAnotherButton').addEventListener('click', reset);
    PP.setupAddressAutocomplete?.('pickupAddress');
    PP.setupAddressAutocomplete?.('deliveryAddress');
    byId('pickupName').value = user.name;
    byId('pickupContact').value = user.username;
    renderTiles();
    updateSummary();
    byId('builderView').hidden = false;
    setupRouteLookup();
    try {
      pricing = await api('/api/pricing');
      updateSummary();
    } catch (error) {
      setMessage('builderMessage', `Could not load current pricing: ${error.message}`, 'error');
    }
  }

  initialize();
})();
