(() => {
  const { keys, byId, money, text, statusText, escapeHtml, readArray, saveArray,
    api, setMessage, detail, drawMap } = PP;
  const state = { orders: [], selectedId: null, lastStatuses: new Map() };

  async function refresh(selectedId = null) {
    try {
      state.orders = await api('/api/parcels/mine');
      for (const parcel of state.orders) {
        const oldStatus = state.lastStatuses.get(parcel.id);
        if (oldStatus && oldStatus !== parcel.status) {
          setMessage('customerNotice', `${parcel.trackingNumber} changed from ${statusText(oldStatus)} to ${statusText(parcel.status)}.`, 'success');
        }
        state.lastStatuses.set(parcel.id, parcel.status);
      }
      if (selectedId && state.orders.some(parcel => parcel.id === selectedId)) {
        state.selectedId = selectedId;
      } else if (!state.orders.some(parcel => parcel.id === state.selectedId)) {
        state.selectedId = state.orders[0]?.id || null;
      }
      renderOrders();
      setMessage('ordersMessage', `Showing ${state.orders.length} order(s) linked to your account.`);
    } catch (error) {
      setMessage('ordersMessage', `Could not refresh orders: ${error.message}`, 'error');
    }
  }

  function renderOrders() {
    const host = byId('ordersList');
    byId('orderCount').textContent = state.orders.length;
    if (!state.orders.length) {
      host.innerHTML = '<div class="empty-state">Your deliveries will appear here after you book.</div>';
      renderTracking();
      return;
    }
    host.innerHTML = state.orders.map(parcel => `
      <button class="order-card ${parcel.id === state.selectedId ? 'selected' : ''}" data-order-id="${escapeHtml(parcel.id)}" type="button">
        <span><strong>${escapeHtml(parcel.trackingNumber)}</strong><small>${escapeHtml(parcel.destination || 'Delivery address not set')}</small></span>
        <span class="order-arrow">›</span>
      </button>`).join('');
    host.querySelectorAll('[data-order-id]').forEach(button =>
      button.addEventListener('click', () => {
        state.selectedId = button.dataset.orderId;
        renderOrders();
      }));
    renderTracking();
  }

  function renderTracking() {
    const parcel = state.orders.find(item => item.id === state.selectedId);
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
    const parcel = state.orders.find(item => item.id === state.selectedId);
    if (!parcel) return;
    window.alert(`DEMO RECEIPT — NOT PROOF OF PAYMENT\n\nTracking: ${parcel.trackingNumber}\nFrom: ${parcel.pickupAddress || '—'}\nTo: ${parcel.destination || '—'}\nEstimated total: ${money(parcel.estimatedPrice)}\nPayment: ${statusText(parcel.paymentMethod)}\n\nNo payment was collected.`);
  }

  function createSupportRequest() {
    const parcel = state.orders.find(item => item.id === state.selectedId);
    if (!parcel) return;
    const message = window.prompt('Write a message for the local demo support queue:');
    if (!message || !message.trim()) return;
    const tickets = readArray(keys.tickets);
    tickets.unshift({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      trackingNumber: parcel.trackingNumber,
      message: message.trim(),
      createdAt: new Date().toISOString(),
      status: 'OPEN'
    });
    saveArray(keys.tickets, tickets);
    setMessage('customerNotice', 'Support request added to this browser’s local demo queue.', 'success');
  }

  function initialize() {
    byId('connectionText').textContent = 'Updates every 30 seconds';
    byId('trackingSection').hidden = false;
    byId('refreshOrdersButton').addEventListener('click', () => refresh());
    byId('receiptButton').addEventListener('click', showReceipt);
    byId('supportButton').addEventListener('click', createSupportRequest);
    refresh();
    window.setInterval(refresh, 30000);
  }

  PP.customerTracking = { initialize, refresh };
})();
