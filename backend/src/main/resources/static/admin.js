(() => {
  const { keys, terminalStatuses, byId, money, text, statusText, escapeHtml, readArray, saveArray,
    api, setMessage, detail, drawMap } = PP;
  const state = { parcels: [], selectedDispatchId: new URLSearchParams(window.location.search).get('parcel') };

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
      ${detail('PICKUP', parcel.pickupAddress)}
      ${detail('SENDER CONTACT', parcel.customerContact)}
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

  async function initialize() {
    const user = await PP.requireRole('ADMIN');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('refreshDispatchButton').addEventListener('click', refreshDispatch);
    byId('dispatchFilter').addEventListener('change', renderDispatch);
    byId('assignButton').addEventListener('click', assignSelected);
    byId('autoDispatchButton').addEventListener('click', autoDispatch);
    const savedCourierIds = localStorage.getItem(keys.couriers);
    if (savedCourierIds) byId('courierIdsInput').value = savedCourierIds;
    await refreshDispatch();
    renderSupportTickets();
    byId('connectionText').textContent = 'Updates every 30 seconds';
    window.setInterval(refreshDispatch, 30000);
  }

  initialize();
})();
