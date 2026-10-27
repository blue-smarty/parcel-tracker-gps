(() => {
  const keys = {
    profile: 'parcelpilot.profile',
    tracked: 'parcelpilot.trackedOrders',
    tickets: 'parcelpilot.supportTickets',
    couriers: 'parcelpilot.courierIds'
  };
  const terminalStatuses = new Set(['DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED']);
  const state = {
    parcels: [],
    customerOrders: [],
    selectedCustomerId: null,
    selectedDispatchId: null,
    socket: null,
    csrfToken: null,
    lastStatuses: new Map(),
    refreshTimer: null
  };
  const byId = id => document.getElementById(id);
  const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value) || 0);
  const text = value => value == null || value === '' ? '—' : String(value);
  const statusText = value => text(value).replaceAll('_', ' ');
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[char]);
  const readArray = key => {
    try {
      const value = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (error) {
      console.warn(`Could not read local demo data (${key}).`, error);
      return [];
    }
  };
  const saveArray = (key, value) => localStorage.setItem(key, JSON.stringify(value));

  async function getCsrfToken() {
    if (state.csrfToken) return state.csrfToken;
    const response = await fetch('/api/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error(`Could not obtain a CSRF token (HTTP ${response.status}).`);
    const body = await response.json();
    if (!body.token) throw new Error('The server did not provide a CSRF token.');
    state.csrfToken = body.token;
    return state.csrfToken;
  }

  async function api(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (method !== 'GET' && method !== 'HEAD') headers.set('X-XSRF-TOKEN', await getCsrfToken());
    const response = await fetch(path, {
      ...options, method, headers, credentials: 'same-origin'
    });
    if (!response.ok) {
      let message = `Request failed (HTTP ${response.status}).`;
      try {
        const body = await response.json();
        message = body.error || body.message || message;
        if (body.errors) message += ` ${Object.values(body.errors).join(' ')}`;
      } catch (_) {
        // Keep the HTTP status when the server does not return JSON.
      }
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    return response.json();
  }

  function setMessage(id, message, kind = '') {
    const element = byId(id);
    element.textContent = message;
    element.className = element.className.replace(/\b(error|success)\b/g, '').trim();
    if (kind) element.classList.add(kind);
  }

  function setView(viewId) {
    document.querySelectorAll('.tab').forEach(button => button.classList.toggle('active', button.dataset.view === viewId));
    document.querySelectorAll('.view').forEach(view => view.classList.toggle('active-view', view.id === viewId));
    if (viewId === 'dispatchView') refreshDispatch();
    else refreshCustomerOrders();
  }

  function profile() {
    try {
      return JSON.parse(localStorage.getItem(keys.profile) || '{}');
    } catch (_) {
      return {};
    }
  }

  function saveProfile(name, contact) {
    localStorage.setItem(keys.profile, JSON.stringify({ name, contact }));
  }

  function initializeProfile() {
    const saved = profile();
    byId('customerName').value = saved.name || '';
    byId('customerContact').value = saved.contact || '';
  }

  function updateEstimate() {
    const distance = Number(byId('distanceKm').value);
    const weight = Number(byId('weightKg').value);
    if (!Number.isFinite(distance) || distance <= 0 || !Number.isFinite(weight) || weight <= 0) {
      byId('priceEstimate').textContent = '$—';
      return;
    }
    const multiplier = byId('deliverySpeed').value === 'SAME_DAY' ? 1.75 : 1;
    byId('priceEstimate').textContent = money(Math.round((5 + distance * 1.2 + weight * 0.5) * multiplier * 100) / 100);
  }

  function toggleSchedule() {
    const scheduled = byId('deliverySpeed').value === 'SCHEDULED';
    byId('scheduleFields').hidden = !scheduled;
    byId('scheduledDate').required = scheduled;
    byId('scheduledWindow').required = scheduled;
  }

  function bookingPayload() {
    const latitude = byId('destinationLatitude').value.trim();
    const longitude = byId('destinationLongitude').value.trim();
    if (Boolean(latitude) !== Boolean(longitude)) throw new Error('Enter both destination coordinates, or leave both blank.');
    const destinationLatitude = latitude ? Number(latitude) : null;
    const destinationLongitude = longitude ? Number(longitude) : null;
    if (latitude && (!Number.isFinite(destinationLatitude) || destinationLatitude < -90 || destinationLatitude > 90
        || !Number.isFinite(destinationLongitude) || destinationLongitude < -180 || destinationLongitude > 180)) {
      throw new Error('Enter valid coordinates (latitude -90 to 90 and longitude -180 to 180).');
    }
    return {
      sender: byId('customerName').value.trim(),
      customerContact: byId('customerContact').value.trim(),
      pickupAddress: byId('pickupAddress').value.trim(),
      recipient: byId('recipient').value.trim(),
      destination: byId('destination').value.trim(),
      destinationLatitude,
      destinationLongitude,
      packageSize: byId('packageSize').value,
      widthCm: Number(byId('widthCm').value),
      lengthCm: Number(byId('lengthCm').value),
      heightCm: Number(byId('heightCm').value),
      weightKg: Number(byId('weightKg').value),
      deliverySpeed: byId('deliverySpeed').value,
      distanceKm: Number(byId('distanceKm').value),
      paymentMethod: byId('paymentMethod').value,
      scheduledDate: byId('deliverySpeed').value === 'SCHEDULED' ? byId('scheduledDate').value : null,
      scheduledWindow: byId('deliverySpeed').value === 'SCHEDULED' ? byId('scheduledWindow').value : null
    };
  }

  async function bookParcel(event) {
    event.preventDefault();
    const button = byId('bookButton');
    button.disabled = true;
    setMessage('bookingMessage', 'Creating your demo delivery…');
    try {
      const payload = bookingPayload();
      saveProfile(payload.sender, payload.customerContact);
      const parcel = await api('/api/parcels', { method: 'POST', body: JSON.stringify(payload) });
      const ids = readArray(keys.tracked).filter(id => id !== parcel.id);
      ids.unshift(parcel.id);
      saveArray(keys.tracked, ids);
      byId('recipient').value = '';
      setMessage('bookingMessage', `Delivery ${parcel.trackingNumber} booked. ${money(parcel.estimatedPrice)} is a demo estimate; no payment was collected.`, 'success');
      await refreshCustomerOrders();
      selectCustomerOrder(parcel.id);
    } catch (error) {
      setMessage('bookingMessage', `Could not create delivery: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function refreshCustomerOrders() {
    const ids = readArray(keys.tracked).filter(id => typeof id === 'string' && id);
    if (!ids.length) {
      state.customerOrders = [];
      state.selectedCustomerId = null;
      renderCustomerOrders();
      return;
    }
    try {
      const results = await Promise.all(ids.map(async id => {
        try {
          return await api(`/api/parcels/${encodeURIComponent(id)}`);
        } catch (error) {
          if (error.status === 404) return null;
          throw error;
        }
      }));
      state.customerOrders = results.filter(Boolean);
      const presentIds = new Set(state.customerOrders.map(parcel => parcel.id));
      saveArray(keys.tracked, ids.filter(id => presentIds.has(id)));
      for (const parcel of state.customerOrders) {
        const old = state.lastStatuses.get(parcel.id);
        if (old && old !== parcel.status) {
          setMessage('customerNotice', `${parcel.trackingNumber} changed from ${statusText(old)} to ${statusText(parcel.status)}.`, 'success');
        }
        state.lastStatuses.set(parcel.id, parcel.status);
      }
      if (!state.customerOrders.some(parcel => parcel.id === state.selectedCustomerId)) {
        state.selectedCustomerId = state.customerOrders[0]?.id || null;
      }
      renderCustomerOrders();
      setMessage('ordersMessage', `Showing ${state.customerOrders.length} order(s) saved in this browser.`);
    } catch (error) {
      setMessage('ordersMessage', `Could not refresh orders: ${error.message}`, 'error');
    }
  }

  function renderCustomerOrders() {
    const host = byId('ordersList');
    byId('orderCount').textContent = state.customerOrders.length;
    if (!state.customerOrders.length) {
      host.innerHTML = '<div class="empty-state">Your deliveries will appear here after you book.</div>';
      renderCustomerTracking();
      return;
    }
    host.innerHTML = state.customerOrders.map(parcel => `
      <button class="order-card ${parcel.id === state.selectedCustomerId ? 'selected' : ''}" data-order-id="${escapeHtml(parcel.id)}">
        <span><strong>${escapeHtml(parcel.trackingNumber)}</strong><small>${escapeHtml(parcel.destination || 'Delivery address not set')}</small></span>
        <span class="order-arrow">›</span>
      </button>`).join('');
    host.querySelectorAll('[data-order-id]').forEach(button =>
      button.addEventListener('click', () => selectCustomerOrder(button.dataset.orderId)));
    renderCustomerTracking();
  }

  function selectCustomerOrder(id) {
    state.selectedCustomerId = id;
    renderCustomerOrders();
  }

  function renderCustomerTracking() {
    const parcel = state.customerOrders.find(item => item.id === state.selectedCustomerId);
    const status = byId('trackingStatus');
    byId('receiptButton').disabled = !parcel;
    byId('supportButton').disabled = !parcel;
    if (!parcel) {
      byId('trackingTitle').textContent = 'Select an order';
      status.textContent = 'WAITING';
      status.className = 'status-chip';
      byId('trackingDetails').textContent = 'Select a delivery to see its route and status.';
      drawMap(byId('customerMap'), [], null, []);
      return;
    }
    byId('trackingTitle').textContent = parcel.trackingNumber || 'Delivery details';
    status.textContent = statusText(parcel.status).toUpperCase();
    status.className = `status-chip status-${String(parcel.status || '').toLowerCase()}`;
    byId('trackingDetails').innerHTML = [
      detail('PICKUP', parcel.pickupAddress),
      detail('DELIVERY', parcel.destination),
      detail('COURIER', parcel.courierId),
      detail('ESTIMATED PRICE', money(parcel.estimatedPrice))
    ].join('');
    drawMap(byId('customerMap'), [parcel], parcel.id, parcel.locationHistory || []);
  }

  function detail(label, value) {
    return `<div class="detail-pair"><span>${escapeHtml(label)}</span><strong>${escapeHtml(text(value))}</strong></div>`;
  }

  function showReceipt() {
    const parcel = state.customerOrders.find(item => item.id === state.selectedCustomerId);
    if (!parcel) return;
    window.alert(`DEMO RECEIPT — NOT PROOF OF PAYMENT\n\nTracking: ${parcel.trackingNumber}\nFrom: ${parcel.pickupAddress || '—'}\nTo: ${parcel.destination || '—'}\nEstimated total: ${money(parcel.estimatedPrice)}\nPayment: ${statusText(parcel.paymentMethod)}\n\nNo payment was collected.`);
  }

  function createSupportRequest() {
    const parcel = state.customerOrders.find(item => item.id === state.selectedCustomerId);
    const message = window.prompt('Write a message for the local demo support queue:');
    if (!message || !message.trim()) return;
    const tickets = readArray(keys.tickets);
    tickets.unshift({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      trackingNumber: parcel?.trackingNumber || '',
      message: message.trim(),
      createdAt: new Date().toISOString(),
      status: 'OPEN'
    });
    saveArray(keys.tickets, tickets);
    setMessage('customerNotice', 'Support request added to this browser’s local demo queue.', 'success');
    renderSupportTickets();
  }

  function isActive(parcel) {
    return !terminalStatuses.has(String(parcel.status || '').toUpperCase());
  }

  async function refreshDispatch() {
    try {
      state.parcels = await api('/api/parcels');
      if (!state.parcels.some(parcel => parcel.id === state.selectedDispatchId)) {
        state.selectedDispatchId = state.parcels.find(isActive)?.id || state.parcels[0]?.id || null;
      }
      renderDispatch();
      setMessage('dispatchMessage', `Loaded ${state.parcels.length} parcels.`);
    } catch (error) {
      setMessage('dispatchMessage', `Could not load operations: ${error.message}`, 'error');
    }
  }

  function filteredParcels() {
    const filter = byId('dispatchFilter').value;
    if (filter === 'ALL') return state.parcels;
    if (filter === 'UNASSIGNED') return state.parcels.filter(parcel =>
      String(parcel.status).toUpperCase() === 'CREATED' && !parcel.courierId);
    return state.parcels.filter(isActive);
  }

  function renderDispatch() {
    const active = state.parcels.filter(isActive);
    const deliveredValue = state.parcels.filter(parcel => parcel.status === 'DELIVERED')
      .reduce((sum, parcel) => sum + (Number(parcel.estimatedPrice) || 0), 0);
    byId('activeMetric').textContent = active.length;
    byId('unassignedMetric').textContent = state.parcels.filter(parcel =>
      parcel.status === 'CREATED' && !parcel.courierId).length;
    byId('valueMetric').textContent = money(deliveredValue);
    byId('escalatedMetric').textContent = state.parcels.filter(parcel => parcel.escalatedAt).length;
    const rows = filteredParcels();
    const tbody = byId('dispatchRows');
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">No parcels match this filter.</td></tr>';
    } else {
      tbody.innerHTML = rows.map(parcel => `
        <tr data-parcel-id="${escapeHtml(parcel.id)}" class="${parcel.id === state.selectedDispatchId ? 'selected-row' : ''}">
          <td><strong>${escapeHtml(parcel.trackingNumber)}</strong></td>
          <td>${escapeHtml(parcel.recipient)}</td>
          <td><span class="row-status status-${escapeHtml(String(parcel.status || '').toLowerCase())}">${escapeHtml(statusText(parcel.status))}</span></td>
          <td class="courier-cell">${escapeHtml(parcel.courierId || 'Unassigned')}</td>
          <td>${parcel.escalatedAt ? '<span title="Stale assignment">⚑</span>' : ''}</td>
        </tr>`).join('');
    }
    tbody.querySelectorAll('[data-parcel-id]').forEach(row =>
      row.addEventListener('click', () => selectDispatchParcel(row.dataset.parcelId)));
    const selected = state.parcels.find(parcel => parcel.id === state.selectedDispatchId);
    byId('assignButton').disabled = !selected;
    renderSelectedParcel(selected);
    drawMap(byId('dispatchMap'), active, state.selectedDispatchId, []);
    renderSupportTickets();
  }

  function selectDispatchParcel(id) {
    state.selectedDispatchId = id;
    renderDispatch();
  }

  function renderSelectedParcel(parcel) {
    const host = byId('selectedParcelDetails');
    if (!parcel) {
      host.innerHTML = '<span class="field-note">Select a parcel to see its details.</span>';
      return;
    }
    const location = parcel.currentLocation;
    host.innerHTML = `<div class="tracking-details">
      ${detail('TRACKING', parcel.trackingNumber)}
      ${detail('STATUS', statusText(parcel.status))}
      ${detail('RECIPIENT', parcel.recipient)}
      ${detail('COURIER', parcel.courierId)}
      ${detail('CURRENT LOCATION', location ? `${location.latitude}, ${location.longitude}` : 'Awaiting GPS')}
      ${detail('DESTINATION', parcel.destination)}
    </div>`;
  }

  async function assignSelected() {
    const parcel = state.parcels.find(item => item.id === state.selectedDispatchId);
    const courierId = byId('courierIdInput').value.trim();
    if (!parcel) return setMessage('dispatchMessage', 'Select a parcel first.', 'error');
    if (!courierId) return setMessage('dispatchMessage', 'Enter a provisioned courier ID.', 'error');
    byId('assignButton').disabled = true;
    try {
      const updated = await api(`/api/parcels/${encodeURIComponent(parcel.id)}/courier`, {
        method: 'PUT', body: JSON.stringify({ courierId })
      });
      byId('courierIdInput').value = '';
      setMessage('dispatchMessage', `Assigned ${updated.trackingNumber} to ${courierId}.`, 'success');
      await refreshDispatch();
    } catch (error) {
      setMessage('dispatchMessage', `Assignment failed: ${error.message}`, 'error');
      byId('assignButton').disabled = false;
    }
  }

  async function autoDispatch() {
    const courierIds = [...new Set(byId('courierIdsInput').value.split(',').map(id => id.trim()).filter(Boolean))];
    if (!courierIds.length) {
      setMessage('dispatchMessage', 'Enter one or more provisioned courier IDs to auto-dispatch.', 'error');
      return;
    }
    localStorage.setItem(keys.couriers, courierIds.join(', '));
    const unassigned = state.parcels.filter(parcel => parcel.status === 'CREATED' && !parcel.courierId);
    if (!unassigned.length) {
      setMessage('dispatchMessage', 'There are no unassigned new parcels.', 'error');
      return;
    }
    const loads = new Map(courierIds.map(id => [id, 0]));
    state.parcels.filter(isActive).forEach(parcel => {
      if (loads.has(parcel.courierId)) loads.set(parcel.courierId, loads.get(parcel.courierId) + 1);
    });
    byId('autoDispatchButton').disabled = true;
    let assigned = 0;
    try {
      for (const parcel of unassigned) {
        const courierId = courierIds.reduce((least, id) => loads.get(id) < loads.get(least) ? id : least);
        await api(`/api/parcels/${encodeURIComponent(parcel.id)}/courier`, {
          method: 'PUT', body: JSON.stringify({ courierId })
        });
        loads.set(courierId, loads.get(courierId) + 1);
        assigned++;
      }
      setMessage('dispatchMessage', `Auto-dispatched ${assigned} parcel(s) across ${courierIds.length} courier(s) using least-active-load balancing.`, 'success');
      await refreshDispatch();
    } catch (error) {
      setMessage('dispatchMessage', `Auto-dispatch stopped after ${assigned} assignment(s): ${error.message}`, 'error');
      await refreshDispatch();
    } finally {
      byId('autoDispatchButton').disabled = false;
    }
  }

  function renderSupportTickets() {
    const tickets = readArray(keys.tickets);
    const open = tickets.filter(ticket => ticket.status === 'OPEN');
    byId('supportCount').textContent = `${open.length} open`;
    const host = byId('supportList');
    if (!tickets.length) {
      host.innerHTML = '<div class="empty-state">Support messages from this browser will appear here.</div>';
      return;
    }
    host.innerHTML = tickets.map(ticket => `
      <div class="support-item">
        <div><strong>${escapeHtml(ticket.trackingNumber || 'General request')} · ${escapeHtml(ticket.status)}</strong>
          <small>${escapeHtml(new Date(ticket.createdAt).toLocaleString())}</small></div>
        <p>${escapeHtml(ticket.message)}</p>
        ${ticket.status === 'OPEN' ? `<button class="button button-quiet" data-ticket-id="${escapeHtml(ticket.id)}">Resolve</button>` : ''}
      </div>`).join('');
    host.querySelectorAll('[data-ticket-id]').forEach(button => button.addEventListener('click', () => {
      const updated = tickets.map(ticket => ticket.id === button.dataset.ticketId ? { ...ticket, status: 'RESOLVED' } : ticket);
      saveArray(keys.tickets, updated);
      renderSupportTickets();
    }));
  }

  function drawMap(container, parcels, selectedId, history) {
    container.innerHTML = '<div class="map-grid"></div>';
    const points = [];
    parcels.forEach(parcel => {
      const location = parcel.currentLocation;
      if (location && Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude))) {
        points.push({ latitude: Number(location.latitude), longitude: Number(location.longitude) });
      }
    });
    if (selectedId && Array.isArray(history)) {
      history.forEach(location => {
        if (location && Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude))) {
          points.push({ latitude: Number(location.latitude), longitude: Number(location.longitude) });
        }
      });
    }
    if (!points.length) {
      container.classList.add('map-empty');
      const label = document.createElement('span');
      label.className = 'map-empty-label';
      label.textContent = 'Live location will appear when GPS is available';
      container.append(label);
      return;
    }
    container.classList.remove('map-empty');
    const minLat = Math.min(...points.map(point => point.latitude));
    const maxLat = Math.max(...points.map(point => point.latitude));
    const minLon = Math.min(...points.map(point => point.longitude));
    const maxLon = Math.max(...points.map(point => point.longitude));
    const x = longitude => 12 + (maxLon === minLon ? 0.5 : (longitude - minLon) / (maxLon - minLon)) * 76;
    const y = latitude => 12 + (maxLat === minLat ? 0.5 : (maxLat - latitude) / (maxLat - minLat)) * 76;
    if (selectedId && history?.length > 1) {
      const routePoints = history.filter(point => Number.isFinite(Number(point.latitude)) && Number.isFinite(Number(point.longitude)))
        .map(point => `${x(Number(point.longitude))},${y(Number(point.latitude))}`).join(' ');
      if (routePoints) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('class', 'map-route');
        svg.setAttribute('viewBox', '0 0 100 100');
        svg.setAttribute('preserveAspectRatio', 'none');
        const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
        polyline.setAttribute('points', routePoints);
        svg.append(polyline);
        container.append(svg);
      }
    }
    parcels.forEach(parcel => {
      const location = parcel.currentLocation;
      if (!location) return;
      const marker = document.createElement('span');
      marker.className = `map-marker ${parcel.id === selectedId ? 'selected' : ''}`;
      marker.style.left = `${x(Number(location.longitude))}%`;
      marker.style.top = `${y(Number(location.latitude))}%`;
      marker.title = `${parcel.trackingNumber} · ${location.latitude}, ${location.longitude}`;
      container.append(marker);
      const label = document.createElement('span');
      label.className = 'map-marker-label';
      label.style.left = `${x(Number(location.longitude))}%`;
      label.style.top = `${y(Number(location.latitude))}%`;
      label.textContent = parcel.trackingNumber || 'Parcel';
      container.append(label);
    });
    if (minLat === maxLat && minLon === maxLon) {
      const note = document.createElement('span');
      note.className = 'map-empty-label';
      note.textContent = 'Single location — add more GPS updates to plot a route';
      container.append(note);
    }
  }

  function connectSocket() {
    const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${scheme}//${location.host}/ws/tracking/all`);
    state.socket = socket;
    socket.addEventListener('open', () => {
      byId('connectionText').textContent = 'Live tracking connected';
      byId('connectionDot').parentElement.classList.add('connected');
    });
    socket.addEventListener('message', event => {
      try {
        const update = JSON.parse(event.data);
        clearTimeout(state.refreshTimer);
        state.refreshTimer = setTimeout(() => refreshAfterLiveUpdate(update), 450);
      } catch (error) {
        console.warn('Ignoring an invalid live tracking update.', error);
      }
    });
    socket.addEventListener('close', () => {
      byId('connectionText').textContent = 'Disconnected · retrying';
      byId('connectionDot').parentElement.classList.remove('connected');
      setTimeout(connectSocket, 2500);
    });
    socket.addEventListener('error', () => socket.close());
  }

  async function refreshAfterLiveUpdate(update) {
    const dispatchParcel = state.parcels.find(parcel => parcel.id === update.parcelId);
    if (dispatchParcel) {
      dispatchParcel.status = update.status;
      if (update.location) dispatchParcel.currentLocation = update.location;
    }
    const tracked = readArray(keys.tracked);
    if (tracked.includes(update.parcelId)) {
      try {
        const parcel = await api(`/api/parcels/${encodeURIComponent(update.parcelId)}`);
        state.customerOrders = state.customerOrders.map(item => item.id === parcel.id ? parcel : item);
        renderCustomerOrders();
      } catch (error) {
        console.warn('Could not refresh a customer order after a live update.', error);
      }
    }
    if (byId('dispatchView').classList.contains('active-view')) await refreshDispatch();
    else renderDispatch();
  }

  function bindEvents() {
    document.querySelectorAll('.tab').forEach(button => button.addEventListener('click', () => setView(button.dataset.view)));
    byId('bookingForm').addEventListener('submit', bookParcel);
    ['distanceKm', 'weightKg'].forEach(id => byId(id).addEventListener('input', updateEstimate));
    byId('deliverySpeed').addEventListener('change', updateEstimate);
    byId('deliverySpeed').addEventListener('change', toggleSchedule);
    byId('refreshOrdersButton').addEventListener('click', refreshCustomerOrders);
    byId('refreshDispatchButton').addEventListener('click', refreshDispatch);
    byId('dispatchFilter').addEventListener('change', renderDispatch);
    byId('assignButton').addEventListener('click', assignSelected);
    byId('autoDispatchButton').addEventListener('click', autoDispatch);
    byId('receiptButton').addEventListener('click', showReceipt);
    byId('supportButton').addEventListener('click', createSupportRequest);
  }

  function initialize() {
    bindEvents();
    initializeProfile();
    const savedCourierIds = localStorage.getItem(keys.couriers);
    if (savedCourierIds) byId('courierIdsInput').value = savedCourierIds;
    toggleSchedule();
    updateEstimate();
    refreshCustomerOrders();
    refreshDispatch();
    renderSupportTickets();
    connectSocket();
    window.setInterval(() => {
      refreshDispatch();
      refreshCustomerOrders();
    }, 30000);
  }

  initialize();
})();
