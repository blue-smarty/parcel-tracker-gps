(() => {
  const { byId, money, api, setMessage, estimatePrice } = PP;

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
  const route = { request: 0, distanceKm: null, straightLineDistanceKm: null, origin: '', destination: '' };
  const addressBook = { entries: [], editingId: null };
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

  function updateSummary() {
    const { all, weight, valid } = totals();
    const distance = route.distanceKm;
    const list = byId('ticketItems');
    list.replaceChildren(...(all.length ? all.map(line => {
      const item = document.createElement('li');
      item.textContent = `${line.quantity} × ${catalog.find(entry => entry.id === line.id).name}`;
      return item;
    }) : [Object.assign(document.createElement('li'), { className: 'ticket-empty', textContent: 'No items yet' })]));
    byId('ticketWeight').textContent = valid ? kg(weight) : '—';
    byId('ticketDistance').textContent = positive(distance)
      ? `${distance} km route · ${Math.round((route.straightLineDistanceKm || 0) * 100) / 100} km straight-line` : '—';
    byId('ticketSpeed').textContent = speed() === 'SAME_DAY' ? 'Same-day' : 'Scheduled';
    byId('ticketPrice').textContent = valid && positive(distance) && pricing
      ? money(estimatePrice(pricing, distance, weight, speed(), route.straightLineDistanceKm)) : '$—';
    const scheduled = speed() === 'SCHEDULED';
    byId('scheduleFields').hidden = !scheduled;
    byId('scheduleDate').required = scheduled;
    byId('scheduleWindow').required = scheduled;
  }

  function buildPayload() {
    const { all, weight, valid } = totals();
    if (!all.length) throw new Error('Choose at least one item to send.');
    if (!valid) throw new Error('Enter a weight and size greater than zero for each item.');
    const distance = route.distanceKm;
    if (!positive(distance) || route.origin !== byId('pickupAddress').value.trim()
        || route.destination !== byId('deliveryAddress').value.trim()) {
      throw new Error('Calculate the driving route for the current addresses before booking.');
    }
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
      straightLineDistanceKm: route.straightLineDistanceKm,
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
      await PP.customerTracking.refresh(parcel.id);
    } catch (error) {
      setMessage('builderMessage', `Could not book delivery: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function setRouteSummary(message, kind = '') {
    const element = byId('routeSummary');
    element.textContent = message;
    element.className = `route-summary${kind ? ` ${kind}` : ''}`;
  }

  function invalidateRoute() {
    route.request++;
    route.distanceKm = null;
    route.straightLineDistanceKm = null;
    route.origin = '';
    route.destination = '';
    byId('calculateRouteButton').disabled = false;
    byId('routeMap').hidden = true;
    byId('routeDistance').textContent = 'Not calculated';
    setRouteSummary('');
    updateSummary();
  }

  async function calculateRoute() {
    const origin = byId('pickupAddress').value.trim();
    const destination = byId('deliveryAddress').value.trim();
    invalidateRoute();
    if (!origin || !destination) {
      setRouteSummary('Enter both pickup and delivery addresses.', 'error');
      return;
    }
    const requestId = ++route.request;
    byId('calculateRouteButton').disabled = true;
    setRouteSummary('Calculating route…');
    try {
      const result = await api('/api/booking-route', {
        method: 'POST', body: JSON.stringify({ origin, destination })
      });
      if (requestId !== route.request) return;
      if (!Number.isFinite(result.distanceMeters) || result.distanceMeters < 0
          || !Number.isFinite(result.durationSeconds) || result.durationSeconds < 0
          || !Number.isFinite(result.straightLineDistanceKm) || result.straightLineDistanceKm < 0
          || !Array.isArray(result.points) || result.points.length < 2) {
        throw new Error('The route provider returned an invalid route.');
      }
      const km = Math.max(0.1, Math.round(result.distanceMeters / 100) / 10);
      const minutes = Math.max(1, Math.round(result.durationSeconds / 60));
      const points = result.points;
      const map = byId('routeMap');
      map.hidden = false;
      PP.drawMap(map, [
        { id: 'start', trackingNumber: 'Pickup', currentLocation: points[0] },
        { id: 'end', trackingNumber: 'Delivery', currentLocation: points[points.length - 1] }
      ], 'start', points);
      route.distanceKm = km;
      route.straightLineDistanceKm = result.straightLineDistanceKm;
      route.origin = origin;
      route.destination = destination;
      byId('routeDistance').textContent = `${km} km`;
      updateSummary();
      setRouteSummary(`Driving distance ${km} km · straight-line ${Math.round(result.straightLineDistanceKm * 100) / 100} km · about ${minutes} min`);
    } catch (error) {
      if (requestId !== route.request) return;
      console.warn('Route lookup failed.', error);
      byId('routeMap').hidden = true;
      setRouteSummary(`Could not calculate the route: ${error.message} Check the addresses or try again later.`, 'error');
    } finally {
      if (requestId === route.request) byId('calculateRouteButton').disabled = false;
    }
  }

  function reset() {
    invalidateRoute();
    lines.clear();
    byId('builderForm').reset();
    byId('pickupName').value = user.name;
    byId('pickupContact').value = user.username;
    byId('builderForm').hidden = false;
    document.querySelector('.booking-ticket').hidden = false;
    byId('bookingDone').hidden = true;
    renderTiles();
    renderManifest();
    updateSummary();
  }

  function addressPayload() {
    const payload = {
      label: byId('addressLabel').value.trim(),
      contactName: byId('addressContactName').value.trim(),
      contactPhone: byId('addressContactPhone').value.trim(),
      address: byId('savedAddress').value.trim()
    };
    if (Object.values(payload).some(value => !value)) throw new Error('Complete every address field.');
    return payload;
  }

  function renderAddressBook() {
    const host = byId('customerAddressList');
    if (!addressBook.entries.length) {
      host.innerHTML = '<div class="empty-state">No saved addresses yet. Add one to use it on a future booking.</div>';
      return;
    }
    host.replaceChildren(...addressBook.entries.map(entry => {
      const card = document.createElement('article');
      card.className = 'address-book-card';
      const details = document.createElement('div');
      details.innerHTML = `<strong></strong><span></span><small></small>`;
      details.querySelector('strong').textContent = entry.label;
      details.querySelector('span').textContent = entry.address;
      details.querySelector('small').textContent = `${entry.contactName} · ${entry.contactPhone}`;
      const actions = document.createElement('div');
      actions.className = 'address-card-actions';
      for (const [label, action] of [['Use for pickup', 'pickup'], ['Use for delivery', 'delivery'], ['Edit', 'edit'], ['Delete', 'delete']]) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = action === 'delete' ? 'button button-quiet' : 'button button-secondary';
        button.textContent = label;
        button.addEventListener('click', () => {
          if (action === 'pickup') {
            byId('pickupName').value = entry.contactName;
            byId('pickupContact').value = entry.contactPhone;
            byId('pickupAddress').value = entry.address;
            invalidateRoute();
          } else if (action === 'delivery') {
            byId('recipientName').value = entry.contactName;
            byId('deliveryAddress').value = entry.address;
            invalidateRoute();
          } else if (action === 'edit') {
            addressBook.editingId = entry.id;
            byId('addressLabel').value = entry.label;
            byId('addressContactName').value = entry.contactName;
            byId('addressContactPhone').value = entry.contactPhone;
            byId('savedAddress').value = entry.address;
            byId('saveAddressButton').textContent = 'Update address';
            byId('cancelAddressEditButton').hidden = false;
          } else {
            deleteAddress(entry.id);
          }
        });
        actions.append(button);
      }
      card.append(details, actions);
      return card;
    }));
  }

  async function loadAddresses() {
    try {
      addressBook.entries = await api('/api/addresses');
      renderAddressBook();
    } catch (error) {
      setMessage('addressBookMessage', `Could not load saved addresses: ${error.message}`, 'error');
    }
  }

  function clearAddressForm() {
    addressBook.editingId = null;
    byId('customerAddressForm').reset();
    byId('saveAddressButton').textContent = 'Save address';
    byId('cancelAddressEditButton').hidden = true;
  }

  async function saveAddress(event) {
    event.preventDefault();
    let payload;
    try {
      payload = addressPayload();
    } catch (error) {
      setMessage('addressBookMessage', error.message, 'error');
      return;
    }
    const id = addressBook.editingId;
    try {
      const saved = await api(id ? `/api/addresses/${encodeURIComponent(id)}` : '/api/addresses', {
        method: id ? 'PUT' : 'POST',
        body: JSON.stringify(payload)
      });
      addressBook.entries = id
        ? addressBook.entries.map(entry => entry.id === id ? saved : entry)
        : [...addressBook.entries, saved];
      clearAddressForm();
      renderAddressBook();
      setMessage('addressBookMessage', 'Address saved to your account.', 'success');
    } catch (error) {
      setMessage('addressBookMessage', `Could not save address: ${error.message}`, 'error');
    }
  }

  async function deleteAddress(id) {
    if (!window.confirm('Delete this saved address?')) return;
    try {
      await api(`/api/addresses/${encodeURIComponent(id)}`, { method: 'DELETE' });
      addressBook.entries = addressBook.entries.filter(entry => entry.id !== id);
      renderAddressBook();
      setMessage('addressBookMessage', 'Address deleted.', 'success');
    } catch (error) {
      setMessage('addressBookMessage', `Could not delete address: ${error.message}`, 'error');
    }
  }

  async function initialize() {
    user = await PP.requireRole('CUSTOMER');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    PP.customerTracking.initialize();
    byId('builderForm').addEventListener('submit', submit);
    byId('builderForm').addEventListener('input', updateSummary);
    byId('builderForm').addEventListener('change', updateSummary);
    byId('bookAnotherButton').addEventListener('click', reset);
    byId('customerAddressForm').addEventListener('submit', saveAddress);
    byId('cancelAddressEditButton').addEventListener('click', clearAddressForm);
    PP.setupAddressAutocomplete?.('pickupAddress');
    PP.setupAddressAutocomplete?.('deliveryAddress');
    byId('pickupName').value = user.name;
    byId('pickupContact').value = user.username;
    renderTiles();
    updateSummary();
    byId('builderView').hidden = false;
    await loadAddresses();
    byId('calculateRouteButton').addEventListener('click', calculateRoute);
    ['pickupAddress', 'deliveryAddress'].forEach(id => {
      byId(id).addEventListener('input', invalidateRoute);
      byId(id).addEventListener('change', invalidateRoute);
    });
    try {
      pricing = await api('/api/pricing');
      updateSummary();
    } catch (error) {
      setMessage('builderMessage', `Could not load current pricing: ${error.message}`, 'error');
    }
  }

  initialize();
})();
