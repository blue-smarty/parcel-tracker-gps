(() => {
  const { byId, api, money, setMessage } = PP;
  const fields = ['baseFee', 'perKmRate', 'perKgRate', 'sameDayMultiplier'];
  let savedServiceArea = null;
  let polygons = [];
  let savedCouncilCodes = [];
  let councilsLoaded = false;
  let coverageMap = null;
  let coverageLayer = null;
  let drawing = null;
  const stateNames = { 1: 'NSW', 2: 'VIC', 3: 'QLD', 4: 'SA', 5: 'WA', 6: 'TAS', 7: 'NT', 8: 'ACT', 9: 'Other territories' };

  function selectedValues(id) {
    return [...byId(id).selectedOptions].map(option => option.value);
  }

  function renderPolygons() {
    const list = byId('coveragePolygonList');
    list.replaceChildren();
    if (coverageLayer) coverageLayer.clearLayers();
    polygons.forEach((polygon, index) => {
      if (coverageLayer) L.polygon(polygon, { color: '#087e83' }).addTo(coverageLayer);
      const row = document.createElement('div');
      row.className = 'settings-actions';
      const label = document.createElement('span');
      label.textContent = `Custom area ${index + 1} (${polygon.length} points)`;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet';
      remove.textContent = 'Remove';
      remove.addEventListener('click', () => { polygons.splice(index, 1); renderPolygons(); });
      row.append(label, remove);
      list.append(row);
    });
    if (coverageLayer && drawing?.length) {
      L.polyline(drawing, { color: '#e9904b' }).addTo(coverageLayer);
      drawing.forEach(point => L.circleMarker(point, { radius: 4 }).addTo(coverageLayer));
    }
  }

  function updateCoverageMode() {
    const custom = byId('serviceAreaMode').value === 'CUSTOM';
    if (!custom && drawing) {
      byId('serviceAreaMode').value = 'CUSTOM';
      setMessage('drawingMessage', 'Finish or cancel the area before changing coverage.', 'error');
      return;
    }
    byId('customCoverage').hidden = !custom;
    if (!custom) return;
    if (!window.L) {
      setMessage('drawingMessage', 'The map is unavailable; existing drawn areas are preserved.', 'error');
      byId('drawCoverageButton').disabled = true;
      return;
    }
    if (!coverageMap) {
      coverageMap = L.map('coverageMap').setView([-37.8136, 144.9631], 9);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
      }).addTo(coverageMap);
      coverageLayer = L.layerGroup().addTo(coverageMap);
      coverageMap.on('click', event => {
        if (!drawing) return;
        if (drawing.length >= 100) {
          setMessage('drawingMessage', 'An area can have at most 100 points.', 'error');
          return;
        }
        drawing.push([event.latlng.lat, event.latlng.lng]);
        byId('finishCoverageButton').disabled = drawing.length < 3;
        setMessage('drawingMessage', `${drawing.length} points placed.`);
        renderPolygons();
      });
      renderPolygons();
    }
    window.setTimeout(() => coverageMap.invalidateSize(), 0);
  }

  function stopDrawing() {
    drawing = null;
    byId('drawCoverageButton').disabled = false;
    byId('finishCoverageButton').disabled = true;
    byId('cancelCoverageButton').disabled = true;
    setMessage('drawingMessage', '');
    renderPolygons();
  }

  async function loadCouncils() {
    try {
      const councils = await api('/api/admin/service-area/councils');
      const select = byId('coverageCouncils');
      select.replaceChildren(...councils.sort((a, b) => a.name.localeCompare(b.name)).map(council => {
        const option = document.createElement('option');
        option.value = council.code;
        option.textContent = `${council.name} (${stateNames[council.state] || council.state})`;
        option.selected = savedCouncilCodes.includes(council.code);
        return option;
      }));
      councilsLoaded = true;
      setMessage('councilMessage', 'ABS 2025 statistical council boundaries.');
    } catch (error) {
      setMessage('councilMessage', `Could not load council areas: ${error.message}`, 'error');
    }
  }

  async function loadServiceArea() {
    try {
      const settings = await api('/api/admin/service-area');
      savedServiceArea = settings.coverageMode;
      byId('serviceAreaMode').value = savedServiceArea;
      polygons = settings.polygons || [];
      savedCouncilCodes = settings.councils || [];
      [...byId('coverageStates').options].forEach(option => {
        option.selected = (settings.states || []).includes(option.value);
      });
      updateCoverageMode();
      await loadCouncils();
      byId('saveServiceAreaButton').disabled = false;
      setMessage('serviceAreaMessage', 'Current service area loaded.');
    } catch (error) {
      setMessage('serviceAreaMessage', `Could not load service area: ${error.message}`, 'error');
    }
  }

  async function saveServiceArea(event) {
    event.preventDefault();
    const mode = byId('serviceAreaMode').value;
    if (drawing) {
      setMessage('serviceAreaMessage', 'Finish or cancel the area being drawn before saving.', 'error');
      return;
    }
    if (savedServiceArea !== 'UNRESTRICTED' && mode === 'UNRESTRICTED'
        && !window.confirm('Disable coverage restrictions? New deliveries can be booked outside the service area.')) return;
    const button = byId('saveServiceAreaButton');
    button.disabled = true;
    try {
      const settings = await api('/api/admin/service-area', {
        method: 'PUT', body: JSON.stringify({
          coverageMode: mode,
          states: selectedValues('coverageStates'),
          councils: councilsLoaded ? selectedValues('coverageCouncils') : savedCouncilCodes,
          polygons
        })
      });
      savedServiceArea = settings.coverageMode;
      savedCouncilCodes = settings.councils;
      byId('serviceAreaMode').value = savedServiceArea;
      setMessage('serviceAreaMessage', 'Service area saved.', 'success');
    } catch (error) {
      setMessage('serviceAreaMessage', `Could not save service area: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function readForm() {
    return Object.fromEntries(fields.map(field => [field, Number(byId(field).value)]));
  }

  function renderPreview() {
    const settings = readForm();
    if (fields.some(field => !Number.isFinite(settings[field])) || settings.perKmRate < 0
        || settings.perKgRate < 0 || settings.baseFee < 0 || settings.sameDayMultiplier < 1) {
      byId('pricingPreview').textContent = 'Enter valid rates to preview example prices.';
      return;
    }
    const standard = settings.baseFee + 10 * settings.perKmRate + 2 * settings.perKgRate;
    const sameDay = standard * settings.sameDayMultiplier;
    byId('pricingPreview').textContent =
      `Example (10 km, 2 kg): scheduled ${money(standard)} · same-day ${money(sameDay)}`;
  }

  function applySettings(settings) {
    fields.forEach(field => { byId(field).value = settings[field]; });
    renderPreview();
  }

  async function loadPricing() {
    byId('reloadPricingButton').disabled = true;
    try {
      applySettings(await api('/api/pricing'));
      setMessage('pricingMessage', 'Current pricing loaded.');
    } catch (error) {
      setMessage('pricingMessage', `Could not load pricing: ${error.message}`, 'error');
    } finally {
      byId('reloadPricingButton').disabled = false;
    }
  }

  async function savePricing(event) {
    event.preventDefault();
    const button = byId('savePricingButton');
    if (button.disabled) return;
    button.disabled = true;
    try {
      const settings = await api('/api/admin/pricing', {
        method: 'PUT',
        body: JSON.stringify(readForm())
      });
      applySettings(settings);
      setMessage('pricingMessage', 'Pricing saved. New parcel estimates will use these rates.', 'success');
    } catch (error) {
      setMessage('pricingMessage', `Could not save pricing: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function loadDispatchers() {
    try {
      const accounts = await api('/api/admin/dispatchers');
      const list = byId('dispatcherList');
      list.replaceChildren();
      if (!accounts.length) {
        list.textContent = 'No dispatcher accounts yet.';
        return;
      }
      accounts.forEach(account => {
        const row = document.createElement('div');
        row.className = 'support-item';
        const name = document.createElement('strong');
        name.textContent = account.username;
        const password = document.createElement('input');
        password.type = 'password';
        password.autocomplete = 'new-password';
        password.placeholder = 'New password (12+ characters)';
        password.setAttribute('aria-label', `New password for ${account.username}`);
        const reset = document.createElement('button');
        reset.type = 'button';
        reset.className = 'button button-quiet';
        reset.textContent = 'Reset password';
        reset.addEventListener('click', () => resetPassword(account.username, password));
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button button-quiet';
        remove.textContent = 'Revoke';
        remove.addEventListener('click', () => revokeDispatcher(account.username));
        row.append(name, password, reset, remove);
        list.append(row);
      });
    } catch (error) {
      setMessage('dispatcherMessage', `Could not load dispatchers: ${error.message}`, 'error');
    }
  }

  async function createDispatcher(event) {
    event.preventDefault();
    const button = byId('createDispatcherButton');
    button.disabled = true;
    try {
      await api('/api/admin/dispatchers', { method: 'POST', body: JSON.stringify({
        username: byId('dispatcherUsername').value.trim(),
        password: byId('dispatcherPassword').value
      }) });
      byId('dispatcherUsername').value = '';
      byId('dispatcherPassword').value = '';
      setMessage('dispatcherMessage', 'Dispatcher account created.', 'success');
      await loadDispatchers();
    } catch (error) {
      setMessage('dispatcherMessage', `Could not create dispatcher: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function resetPassword(username, field) {
    try {
      await api(`/api/admin/dispatchers/${encodeURIComponent(username)}/password`, {
        method: 'PUT', body: JSON.stringify({ password: field.value })
      });
      field.value = '';
      setMessage('dispatcherMessage', `Password updated for ${username}.`, 'success');
    } catch (error) {
      setMessage('dispatcherMessage', `Could not reset password: ${error.message}`, 'error');
    }
  }

  async function revokeDispatcher(username) {
    if (!window.confirm(`Revoke access for ${username}?`)) return;
    try {
      await api(`/api/admin/dispatchers/${encodeURIComponent(username)}`, { method: 'DELETE' });
      setMessage('dispatcherMessage', `Access revoked for ${username}.`, 'success');
      await loadDispatchers();
    } catch (error) {
      setMessage('dispatcherMessage', `Could not revoke access: ${error.message}`, 'error');
    }
  }

  async function initialize() {
    const user = await PP.requireRole('ADMIN');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('pricingForm').addEventListener('submit', savePricing);
    byId('serviceAreaForm').addEventListener('submit', saveServiceArea);
    byId('serviceAreaMode').addEventListener('change', updateCoverageMode);
    byId('drawCoverageButton').addEventListener('click', () => {
      if (!coverageMap || drawing || polygons.length >= 20) return;
      drawing = [];
      byId('drawCoverageButton').disabled = true;
      byId('cancelCoverageButton').disabled = false;
      setMessage('drawingMessage', 'Click the map to add at least three boundary points.');
    });
    byId('finishCoverageButton').addEventListener('click', () => {
      if (!drawing || drawing.length < 3) return;
      polygons.push(drawing);
      stopDrawing();
    });
    byId('cancelCoverageButton').addEventListener('click', stopDrawing);
    byId('reloadPricingButton').addEventListener('click', loadPricing);
    byId('dispatcherForm').addEventListener('submit', createDispatcher);
    fields.forEach(field => byId(field).addEventListener('input', renderPreview));
    byId('settingsView').hidden = false;
    byId('settingsView').classList.add('active-view');
    await Promise.all([loadPricing(), loadDispatchers(), loadServiceArea()]);
  }

  initialize();
})();
