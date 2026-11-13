(() => {
  const { byId, api, setMessage } = PP;
  let savedServiceArea = null;
  let polygons = [];
  let polygonCouriers = [];
  let savedPolygonCouriers = [];
  let couriers = [];
  let couriersLoaded = false;
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
      row.className = 'settings-actions coverage-area-row';
      const label = document.createElement('span');
      label.textContent = `Custom area ${index + 1} (${polygon.length} points)`;
      const courier = document.createElement('select');
      courier.setAttribute('aria-label', `Courier for custom area ${index + 1}`);
      const unassigned = document.createElement('option');
      unassigned.value = '';
      unassigned.textContent = 'No auto-assignment';
      courier.append(unassigned);
      couriers.forEach(account => {
        const option = document.createElement('option');
        option.value = account.courierId;
        option.textContent = account.courierId;
        courier.append(option);
      });
      if (polygonCouriers[index] && !couriers.some(account => account.courierId === polygonCouriers[index])) {
        const option = document.createElement('option');
        option.value = polygonCouriers[index];
        option.textContent = `Unavailable courier (${polygonCouriers[index]})`;
        courier.append(option);
      }
      courier.value = polygonCouriers[index] || '';
      courier.addEventListener('change', () => { polygonCouriers[index] = courier.value || null; });
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet';
      remove.textContent = 'Remove';
      remove.addEventListener('click', () => {
        polygons.splice(index, 1);
        polygonCouriers.splice(index, 1);
        renderPolygons();
      });
      row.append(label, courier, remove);
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
        maxZoom: 19, referrerPolicy: 'origin',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
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

  async function loadCouriers() {
    try {
      couriers = await api('/api/admin/couriers');
      couriersLoaded = true;
      setMessage('courierMessage', 'Choose which courier receives parcels in each drawn area.');
    } catch (error) {
      setMessage('courierMessage', `Could not load couriers: ${error.message}`, 'error');
    }
    renderPolygons();
  }

  async function loadServiceArea() {
    try {
      const settings = await api('/api/admin/service-area');
      savedServiceArea = settings.coverageMode;
      byId('serviceAreaMode').value = savedServiceArea;
      polygons = settings.polygons || [];
      polygonCouriers = polygons.map((_, index) => settings.polygonCouriers?.[index] || null);
      savedPolygonCouriers = [...polygonCouriers];
      savedCouncilCodes = settings.councils || [];
      [...byId('coverageStates').options].forEach(option => {
        option.selected = (settings.states || []).includes(option.value);
      });
      updateCoverageMode();
      await Promise.all([loadCouncils(), loadCouriers()]);
      byId('saveServiceAreaButton').disabled = false;
      setMessage('serviceAreaMessage', 'Current delivery coverage loaded.');
    } catch (error) {
      setMessage('serviceAreaMessage', `Could not load delivery coverage: ${error.message}`, 'error');
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
          polygons,
          polygonCouriers: couriersLoaded ? polygonCouriers : savedPolygonCouriers
        })
      });
      savedServiceArea = settings.coverageMode;
      savedCouncilCodes = settings.councils;
      polygonCouriers = settings.polygonCouriers || polygons.map(() => null);
      savedPolygonCouriers = [...polygonCouriers];
      renderPolygons();
      byId('serviceAreaMode').value = savedServiceArea;
      setMessage('serviceAreaMessage', 'Delivery coverage saved.', 'success');
    } catch (error) {
      setMessage('serviceAreaMessage', `Could not save delivery coverage: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function initialize() {
    const user = await PP.requireRole('ADMIN');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
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
      polygonCouriers.push(null);
      stopDrawing();
    });
    byId('cancelCoverageButton').addEventListener('click', stopDrawing);
    byId('coverageView').hidden = false;
    byId('coverageView').classList.add('active-view');
    await loadServiceArea();
  }

  initialize();
})();
