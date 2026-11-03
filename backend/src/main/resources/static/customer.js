(() => {
  const { keys, terminalStatuses, byId, money, text, statusText, escapeHtml, readArray, saveArray,
    api, setMessage, detail, drawMap, connectSocket } = PP;
  const state = { customerOrders: [], selectedCustomerId: null, lastStatuses: new Map(), user: null };
  const trackedKey = () => `${keys.tracked}:${state.user.username}`;

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
      const parcel = await api('/api/parcels', { method: 'POST', body: JSON.stringify(payload) });
      const ids = readArray(trackedKey()).filter(id => id !== parcel.id);
      ids.unshift(parcel.id);
      saveArray(trackedKey(), ids);
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
    const ids = readArray(trackedKey()).filter(id => typeof id === 'string' && id);
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
      saveArray(trackedKey(), ids.filter(id => presentIds.has(id)));
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
      setMessage('ordersMessage', `Showing ${state.customerOrders.length} order(s) saved to your account on this browser.`);
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


  async function refreshAfterLiveUpdate(update) {
    if (!readArray(trackedKey()).includes(update.parcelId)) return;
    try {
      const parcel = await api(`/api/parcels/${encodeURIComponent(update.parcelId)}`);
      state.customerOrders = state.customerOrders.map(item => item.id === parcel.id ? parcel : item);
      renderCustomerOrders();
    } catch (error) {
      console.warn('Could not refresh a customer order after a live update.', error);
    }
  }

  async function initialize() {
    state.user = await PP.requireRole('CUSTOMER');
    if (!state.user) return;
    byId('accountName').textContent = state.user.name;
    byId('customerName').value = state.user.name;
    byId('customerContact').value = state.user.username;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('bookingForm').addEventListener('submit', bookParcel);
    ['distanceKm', 'weightKg'].forEach(id => byId(id).addEventListener('input', updateEstimate));
    byId('deliverySpeed').addEventListener('change', updateEstimate);
    byId('deliverySpeed').addEventListener('change', toggleSchedule);
    byId('refreshOrdersButton').addEventListener('click', refreshCustomerOrders);
    byId('receiptButton').addEventListener('click', showReceipt);
    byId('supportButton').addEventListener('click', createSupportRequest);
    toggleSchedule();
    updateEstimate();
    refreshCustomerOrders();
    connectSocket(refreshAfterLiveUpdate);
    window.setInterval(refreshCustomerOrders, 30000);
  }

  initialize();
})();
