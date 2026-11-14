(() => {
  const { byId, money, api, setMessage, drawMap, estimatePrice } = PP;
  const catalog = [
    { id: 'documents', name: 'Documents', hint: 'Letters, contracts, A4 envelopes', size: 'SMALL', weight: 0.5, dims: [32, 23, 2], icon: '✉️' },
    { id: 'satchel', name: 'Satchel', hint: 'Laptops, bags, briefcases', size: 'SMALL', weight: 3, dims: [50, 30, 20], icon: '💼' },
    { id: 'box', name: 'Small box', hint: 'Clothing, small appliances, gifts', size: 'MEDIUM', weight: 5, dims: [40, 30, 30], icon: '📦' },
    { id: 'fragile', name: 'Fragile', hint: 'Cakes, flowers, pottery', size: 'MEDIUM', weight: 4, dims: [40, 40, 40], icon: '🎂' },
    { id: 'large', name: 'Large box', hint: 'Frames, chairs, flat-pack items', size: 'LARGE', weight: 15, dims: [80, 60, 50], icon: '🗃️' },
    { id: 'oversized', name: 'Oversized', hint: 'Pallets, plants, bulky goods', size: 'LARGE', weight: 30, dims: [120, 80, 80], icon: '🛋️' }
  ];
  const sizeRank = { SMALL: 0, MEDIUM: 1, LARGE: 2 };
  const lines = new Map();
  const route = { request: 0, distanceKm: null, straightLineDistanceKm: null, origin: '', destination: '' };
  const addressBook = { email: '', entries: [] };
  const speed = () => document.querySelector('input[name="speed"]:checked').value;
  const positive = value => Number.isFinite(value) && value > 0;
  let pricing = { baseFee: 5, perKmRate: 1.2, perKgRate: 0.5, sameDayMultiplier: 1.75 };

  function addItem(id, change) {
    const item = catalog.find(entry => entry.id === id);
    const line = lines.get(id) || { id, quantity: 0, weight: item.weight, length: item.dims[0], width: item.dims[1], height: item.dims[2] };
    line.quantity = Math.min(20, Math.max(0, line.quantity + change));
    if (line.quantity) lines.set(id, line);
    else lines.delete(id);
    renderTiles();
    renderManifest();
    updateSummary();
  }

  function renderTiles() {
    byId('itemTiles').replaceChildren(...catalog.map(item => {
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
      for (const [label, key] of [['Weight each (kg)', 'weight'], ['Length (cm)', 'length'], ['Width (cm)', 'width'], ['Height (cm)', 'height']]) {
        const wrapper = document.createElement('label');
        wrapper.textContent = label;
        const input = document.createElement('input');
        input.type = 'number';
        input.min = '0.1';
        input.step = '0.1';
        input.required = true;
        input.value = line[key];
        input.addEventListener('input', () => {
          line[key] = Number(input.value);
          updateSummary();
        });
        wrapper.append(input);
        fields.append(wrapper);
      }
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
    byId('ticketItems').replaceChildren(...(all.length
      ? all.map(line => Object.assign(document.createElement('li'), {
        textContent: `${line.quantity} × ${catalog.find(entry => entry.id === line.id).name}`
      }))
      : [Object.assign(document.createElement('li'), { className: 'ticket-empty', textContent: 'No items yet' })]));
    const distance = route.distanceKm;
    const selectedSpeed = speed();
    byId('ticketWeight').textContent = valid ? `${(Math.round(weight * 100) / 100).toLocaleString()} kg` : '—';
    byId('ticketDistance').textContent = positive(distance) ? `${distance} km` : '—';
    byId('ticketSpeed').textContent = selectedSpeed === 'SAME_DAY' ? 'Same-day' : 'Scheduled';
    const amount = valid && positive(distance) && route.straightLineDistanceKm != null
      ? estimatePrice(pricing, distance, weight, selectedSpeed, route.straightLineDistanceKm)
      : null;
    byId('ticketPrice').textContent = amount === null ? '$—' : money(Math.round(amount * 100) / 100);
    const scheduled = selectedSpeed === 'SCHEDULED';
    byId('scheduleFields').hidden = !scheduled;
    byId('scheduleDate').required = scheduled;
    byId('scheduleWindow').required = scheduled;
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
    const origin = byId('adhocPickup').value.trim();
    const destination = byId('adhocDestination').value.trim();
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
      drawMap(map, [
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

  function buildPayload() {
    const { all, weight, valid } = totals();
    if (!all.length) throw new Error('Choose at least one item to send.');
    if (!valid) throw new Error('Enter a weight and size greater than zero for each item.');
    const origin = byId('adhocPickup').value.trim();
    const destination = byId('adhocDestination').value.trim();
    if (!positive(route.distanceKm) || route.origin !== origin || route.destination !== destination) {
      throw new Error('Calculate the driving route for the current addresses before assigning.');
    }
    const fields = {
      sender: byId('adhocSender').value.trim(),
      customerContact: byId('adhocContact').value.trim(),
      pickupAddress: origin,
      recipient: byId('adhocRecipient').value.trim(),
      destination,
      courierId: byId('adhocCourier').value,
      packageSize: all.map(line => catalog.find(entry => entry.id === line.id).size)
        .reduce((best, size) => sizeRank[size] > sizeRank[best] ? size : best, 'SMALL'),
      widthCm: Math.max(...all.map(line => line.width)),
      lengthCm: Math.max(...all.map(line => line.length)),
      heightCm: all.reduce((sum, line) => sum + line.quantity * line.height, 0),
      weightKg: weight,
      distanceKm: route.distanceKm,
      straightLineDistanceKm: route.straightLineDistanceKm,
      deliverySpeed: speed(),
      paymentMethod: byId('paymentChoice').value,
      scheduledDate: speed() === 'SCHEDULED' ? byId('scheduleDate').value : null,
      scheduledWindow: speed() === 'SCHEDULED' ? byId('scheduleWindow').value : null
    };
    if (!fields.sender || !fields.customerContact || !fields.pickupAddress || !fields.recipient
        || !fields.destination || !fields.courierId) {
      throw new Error('Complete the sender, recipient, address, and courier details.');
    }
    if (speed() === 'SCHEDULED' && (!fields.scheduledDate || !fields.scheduledWindow)) {
      throw new Error('Choose a delivery date and time window.');
    }
    return fields;
  }

  async function loadCouriers() {
    const select = byId('adhocCourier');
    const current = select.value;
    byId('refreshCouriersButton').disabled = true;
    try {
      const couriers = await api('/api/dispatch/couriers');
      select.replaceChildren(new Option(couriers.length ? 'Choose a courier' : 'No couriers provisioned', ''));
      couriers.forEach(courier => select.add(new Option(courier.courierId, courier.courierId)));
      if (couriers.some(courier => courier.courierId === current)) select.value = current;
      setMessage('courierMessage', couriers.length ? '' : 'No couriers are provisioned yet.', couriers.length ? '' : 'error');
    } catch (error) {
      if (!current) select.replaceChildren(new Option('Could not load couriers', ''));
      setMessage('courierMessage', `Could not load couriers: ${error.message}`, 'error');
    } finally {
      byId('refreshCouriersButton').disabled = false;
    }
  }

  function populateSavedAddresses(select) {
    select.replaceChildren(new Option('Choose a saved address', ''));
    addressBook.entries.forEach(entry => select.add(new Option(`${entry.label} — ${entry.address}`, entry.id)));
    select.disabled = addressBook.entries.length === 0;
  }

  async function loadSavedAddresses() {
    const email = byId('adhocCustomerEmail').value.trim().toLowerCase();
    if (!email) {
      setMessage('adhocAddressBookMessage', 'Enter the customer’s registered email.', 'error');
      return;
    }
    byId('loadSavedAddressesButton').disabled = true;
    try {
      addressBook.entries = await api(`/api/dispatch/customers/${encodeURIComponent(email)}/addresses`);
      addressBook.email = email;
      populateSavedAddresses(byId('adhocPickupSavedAddress'));
      populateSavedAddresses(byId('adhocDeliverySavedAddress'));
      byId('adhocSavedAddressChoices').hidden = false;
      setMessage('adhocAddressBookMessage', addressBook.entries.length
        ? `Loaded ${addressBook.entries.length} saved address(es).`
        : 'This customer has no saved addresses yet.');
    } catch (error) {
      addressBook.email = '';
      addressBook.entries = [];
      byId('adhocSavedAddressChoices').hidden = true;
      setMessage('adhocAddressBookMessage', `Could not load customer address book: ${error.message}`, 'error');
    } finally {
      byId('loadSavedAddressesButton').disabled = false;
    }
  }

  function applySavedAddress(id, destination) {
    const entry = addressBook.entries.find(item => item.id === id);
    if (!entry || !addressBook.email) return;
    if (destination === 'pickup') {
      byId('adhocSender').value = entry.contactName;
      byId('adhocContact').value = entry.contactPhone;
      byId('adhocPickup').value = entry.address;
    } else {
      byId('adhocRecipient').value = entry.contactName;
      byId('adhocDestination').value = entry.address;
    }
    invalidateRoute();
  }

  async function createAdhoc(event) {
    event.preventDefault();
    const button = byId('adhocButton');
    if (button.disabled) return;
    let fields;
    try {
      fields = buildPayload();
    } catch (error) {
      setMessage('adhocMessage', error.message, 'error');
      return;
    }
    button.disabled = true;
    byId('viewDeliveryLink').hidden = true;
    setMessage('adhocMessage', 'Creating and assigning delivery…');
    try {
      const parcel = await api('/api/parcels/adhoc', { method: 'POST', body: JSON.stringify(fields) });
      lines.clear();
      invalidateRoute();
      byId('adhocForm').reset();
      renderTiles();
      renderManifest();
      updateSummary();
      const link = byId('viewDeliveryLink');
      link.href = `/dispatch.html?parcel=${encodeURIComponent(parcel.id)}`;
      link.hidden = false;
      setMessage('adhocMessage', `Created ${parcel.trackingNumber} and assigned it to ${fields.courierId}.`, 'success');
    } catch (error) {
      setMessage('adhocMessage', `Could not create delivery: ${error.message} Check the parcel queue before retrying if the connection was interrupted.`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function initialize() {
    const user = await PP.requireRole(['ADMIN', 'DISPATCHER']);
    if (!user) return;
    byId('adhocAdminLink').hidden = user.role !== 'ADMIN';
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('adhocForm').addEventListener('submit', createAdhoc);
    byId('adhocForm').addEventListener('change', updateSummary);
    byId('refreshCouriersButton').addEventListener('click', loadCouriers);
    byId('loadSavedAddressesButton').addEventListener('click', loadSavedAddresses);
    byId('adhocCustomerEmail').addEventListener('input', () => {
      addressBook.email = '';
      addressBook.entries = [];
      byId('adhocSavedAddressChoices').hidden = true;
    });
    byId('adhocPickupSavedAddress').addEventListener('change', event =>
      applySavedAddress(event.target.value, 'pickup'));
    byId('adhocDeliverySavedAddress').addEventListener('change', event =>
      applySavedAddress(event.target.value, 'delivery'));
    byId('calculateRouteButton').addEventListener('click', calculateRoute);
    ['adhocPickup', 'adhocDestination'].forEach(id => {
      byId(id).addEventListener('input', invalidateRoute);
      byId(id).addEventListener('change', invalidateRoute);
      PP.setupAddressAutocomplete?.(id);
    });
    byId('adhocView').hidden = false;
    renderTiles();
    renderManifest();
    updateSummary();
    await Promise.all([
      loadCouriers(),
      api('/api/pricing').then(value => {
        pricing = value;
        updateSummary();
      }).catch(error => {
        setMessage('adhocMessage', `Could not load current pricing: ${error.message}`, 'error');
      })
    ]);
  }

  initialize();
})();
