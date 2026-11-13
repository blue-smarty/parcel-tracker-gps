window.PP = (() => {
  const keys = {
    tickets: 'parcelpilot.supportTickets',
    couriers: 'parcelpilot.courierIds',
    session: 'parcelpilot.session'
  };
  const terminalStatuses = new Set(['DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED']);
  const state = { csrfToken: null, csrfRequest: null };
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

  // The session lives in sessionStorage: it survives page navigation but is cleared when the tab closes.
  const session = {
    get() {
      try {
        return JSON.parse(sessionStorage.getItem(keys.session) || 'null');
      } catch (_) {
        return null;
      }
    },
    set(value) { sessionStorage.setItem(keys.session, JSON.stringify(value)); },
    clear() { sessionStorage.removeItem(keys.session); }
  };

  function basicAuthorization(username, password) {
    const bytes = new TextEncoder().encode(`${username}:${password}`);
    return `Basic ${btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''))}`;
  }

  function signOut() {
    session.clear();
    window.location.href = '/login.html';
  }

  // Redirects to the sign-in page unless a verified session with the given role exists.
  async function requireRole(role) {
    const current = session.get();
    if (!current) {
      window.location.replace('/login.html');
      return null;
    }
    let identity;
    try {
      identity = await api('/api/auth/me');
    } catch (_) {
      session.clear();
      window.location.replace('/login.html');
      return null;
    }
    if (!(Array.isArray(role) ? role : [role]).includes(identity.role)) {
      const home = { ADMIN: '/admin.html', DISPATCHER: '/dispatch.html', CUSTOMER: '/customer.html' }[identity.role];
      window.location.replace(home || '/login.html');
      return null;
    }
    return { ...current, ...identity };
  }

  async function getCsrfToken() {
    if (state.csrfToken) return state.csrfToken;
    if (!state.csrfRequest) {
      state.csrfRequest = (async () => {
        const response = await fetch('/api/csrf', { credentials: 'same-origin', cache: 'no-store' });
        if (!response.ok) throw new Error(`Could not obtain a CSRF token (HTTP ${response.status}).`);
        const body = await response.json();
        if (!body.token) throw new Error('The server did not provide a CSRF token.');
        state.csrfToken = body.token;
        return state.csrfToken;
      })().finally(() => { state.csrfRequest = null; });
    }
    return state.csrfRequest;
  }


  async function api(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    const current = session.get();
    if (current && current.authorization && !headers.has('Authorization')) {
      headers.set('Authorization', current.authorization);
    }
    headers.set('X-Requested-With', 'XMLHttpRequest');
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const needsCsrf = method !== 'GET' && method !== 'HEAD';
    if (needsCsrf) headers.set('X-XSRF-TOKEN', await getCsrfToken());
    let response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
    if (needsCsrf && response.status === 403) {
      let body;
      try {
        body = await response.clone().json();
      } catch (_) {
        // The normal error handler below will report the HTTP status.
      }
      if (body?.code === 'CSRF_INVALID') {
        if (state.csrfToken === headers.get('X-XSRF-TOKEN')) state.csrfToken = null;
        headers.set('X-XSRF-TOKEN', await getCsrfToken());
        response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
      }
    }
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
      if (response.status === 401) error.message = 'Session not authorized. Sign out and sign in again.';
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

  function detail(label, value) {
    return `<div class="detail-pair"><span>${escapeHtml(label)}</span><strong>${escapeHtml(text(value))}</strong></div>`;
  }

  function setupAddressAutocomplete(input, options = {}) {
    if (typeof document === 'undefined' || typeof fetch !== 'function') return () => {};
    const field = typeof input === 'string' ? byId(input) : input;
    if (!field || typeof field.addEventListener !== 'function') return () => {};
    const minimumLength = Number.isInteger(options.minimumLength) ? options.minimumLength : 3;
    const debounceMs = Number.isInteger(options.debounceMs) ? Math.max(options.debounceMs, 0) : 400;
    const wrapper = document.createElement('div');
    wrapper.className = 'address-autocomplete';
    field.parentNode.insertBefore(wrapper, field);
    wrapper.appendChild(field);
    const status = document.createElement('div');
    status.className = 'address-suggestion-message';
    status.setAttribute('role', 'status');
    status.hidden = true;
    wrapper.appendChild(status);
    const list = document.createElement('div');
    list.className = 'address-suggestions';
    list.id = `${field.id || `address-${Date.now()}`}-suggestions`;
    list.setAttribute('role', 'listbox');
    list.hidden = true;
    wrapper.appendChild(list);
    field.setAttribute('autocomplete', 'off');
    field.setAttribute('aria-autocomplete', 'list');
    field.setAttribute('aria-controls', list.id);
    field.setAttribute('aria-expanded', 'false');

    let timer = null;
    let abortController = null;
    let requestNumber = 0;
    let activeIndex = -1;

    function renderMessage(message) {
      list.replaceChildren();
      activeIndex = -1;
      field.removeAttribute('aria-activedescendant');
      status.textContent = message;
      status.hidden = !message;
      list.hidden = true;
      field.setAttribute('aria-expanded', 'false');
    }

    async function querySuggestions(value, requestId) {
      const query = value.trim();
      if (query.length < minimumLength) {
        renderMessage('');
        return;
      }
      renderMessage('Searching addresses…');
      abortController = new AbortController();
      const requestController = abortController;
      try {
        const results = await api(`/api/address-suggestions?q=${encodeURIComponent(query)}`, {
          signal: requestController.signal,
          headers: { Accept: 'application/json' }
        });
        if (requestId !== requestNumber) return;
        if (!Array.isArray(results) || results.length === 0) {
          renderMessage('No matching addresses. You can enter the address manually.');
          return;
        }
        list.replaceChildren(...results.filter(result => typeof result === 'string' && result.trim()).map((result, index) => {
          const option = document.createElement('button');
          option.type = 'button';
          option.className = 'address-suggestion';
          option.id = `${list.id}-option-${index}`;
          option.setAttribute('role', 'option');
          option.setAttribute('aria-selected', 'false');
          option.textContent = result;
          option.addEventListener('pointerdown', event => event.preventDefault());
          option.addEventListener('click', () => selectSuggestion(result));
          return option;
        }));
        status.hidden = true;
        activeIndex = -1;
        list.hidden = list.childElementCount === 0;
        field.setAttribute('aria-expanded', String(!list.hidden));
        if (list.hidden) renderMessage('No matching addresses. You can enter the address manually.');
      } catch (error) {
        if (error.name === 'AbortError' || requestId !== requestNumber) return;
        console.warn('Address suggestions request failed.', error);
        renderMessage('Address lookup is unavailable. You can enter the address manually.');
      }
    }

    function selectSuggestion(value) {
      field.value = value;
      field.dispatchEvent(new Event('change', { bubbles: true }));
      renderMessage('');
    }

    function setActive(index) {
      const optionsList = [...list.querySelectorAll('[role="option"]')];
      if (!optionsList.length) return;
      activeIndex = (index + optionsList.length) % optionsList.length;
      optionsList.forEach((option, optionIndex) => {
        const active = optionIndex === activeIndex;
        option.setAttribute('aria-selected', String(active));
        option.classList.toggle('active', active);
      });
      field.setAttribute('aria-activedescendant', optionsList[activeIndex].id);
      optionsList[activeIndex].scrollIntoView?.({ block: 'nearest' });
    }

    function onInput(event) {
      if (timer) clearTimeout(timer);
      if (abortController) abortController.abort();
      const value = event?.target?.value ?? field.value ?? '';
      requestNumber += 1;
      const current = requestNumber;
      if (value.trim().length < minimumLength) {
        renderMessage('');
        return;
      }
      renderMessage('Searching addresses…');
      timer = setTimeout(() => {
        querySuggestions(value, current);
      }, debounceMs);
    }

    function onKeydown(event) {
      if (event.key === 'ArrowDown' && !list.hidden) {
        event.preventDefault();
        setActive(activeIndex + 1);
      } else if (event.key === 'ArrowUp' && !list.hidden) {
        event.preventDefault();
        setActive(activeIndex - 1);
      } else if (event.key === 'Enter' && activeIndex >= 0 && !list.hidden) {
        event.preventDefault();
        const option = list.querySelectorAll('[role="option"]')[activeIndex];
        if (option) selectSuggestion(option.textContent);
      } else if (event.key === 'Escape') {
        renderMessage('');
      }
    }

    function onBlur() {
      window.setTimeout(() => renderMessage(''), 150);
    }

    field.addEventListener('input', onInput);
    field.addEventListener('keydown', onKeydown);
    field.addEventListener('blur', onBlur);

    return () => {
      field.removeEventListener('input', onInput);
      field.removeEventListener('keydown', onKeydown);
      field.removeEventListener('blur', onBlur);
      if (timer) clearTimeout(timer);
      if (abortController) abortController.abort();
      wrapper.parentNode?.insertBefore(field, wrapper);
      wrapper.remove();
    };
  }

  const leafletMaps = new WeakMap();

  function validPoint(location) {
    return location && Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude));
  }

  function drawMap(container, parcels, selectedId, history) {
    if (!window.L) return drawPlotMap(container, parcels, selectedId, history);
    let entry = leafletMaps.get(container);
    if (!entry) {
      container.innerHTML = '';
      container.classList.remove('map-empty');
      const map = L.map(container, { zoomControl: true }).setView([20, 0], 2);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        referrerPolicy: 'origin',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(map);
      entry = { map, layer: L.layerGroup().addTo(map) };
      leafletMaps.set(container, entry);
    }
    const { map, layer } = entry;
    layer.clearLayers();
    const bounds = [];
    if (selectedId && Array.isArray(history)) {
      const route = history.filter(validPoint).map(p => [Number(p.latitude), Number(p.longitude)]);
      if (route.length > 1) L.polyline(route, { color: '#e9904b', weight: 3, dashArray: '6 5' }).addTo(layer);
      bounds.push(...route);
    }
    parcels.forEach(parcel => {
      const location = parcel.currentLocation;
      if (!validPoint(location)) return;
      const latLng = [Number(location.latitude), Number(location.longitude)];
      const selected = parcel.id === selectedId;
      L.circleMarker(latLng, {
        radius: selected ? 9 : 7, color: '#fff', weight: 2, fillOpacity: 1,
        fillColor: selected ? '#e9904b' : '#0c8b87'
      }).bindTooltip(String(parcel.trackingNumber || 'Parcel'), { permanent: true, direction: 'right', offset: [8, 0] })
        .addTo(layer);
      bounds.push(latLng);
    });
    // Containers may be hidden when drawn; recompute size before fitting.
    setTimeout(() => {
      map.invalidateSize();
      if (bounds.length === 1) map.setView(bounds[0], 15);
      else if (bounds.length) map.fitBounds(bounds, { padding: [30, 30], maxZoom: 16 });
    }, 0);
  }

  function drawPlotMap(container, parcels, selectedId, history) {
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

  function estimatePrice(pricing, routeDistanceKm, weightKg, deliverySpeed, straightLineDistanceKm) {
    let distanceCharge = routeDistanceKm * pricing.perKmRate;
    if (pricing.pricingMode === 'RADIUS') {
      if (!Number.isFinite(straightLineDistanceKm) || straightLineDistanceKm < 0
          || !Array.isArray(pricing.radiusBands)) {
        throw new Error('Straight-line distance pricing data is unavailable.');
      }
      const band = pricing.radiusBands.find(candidate => candidate.upToKm == null
        || straightLineDistanceKm <= candidate.upToKm);
      if (!band || !Number.isFinite(band.distanceFee)) {
        throw new Error('No radius pricing band covers this trip.');
      }
      distanceCharge = band.distanceFee;
    }
    const multiplier = deliverySpeed === 'SAME_DAY' ? pricing.sameDayMultiplier || 1 : 1;
    return Math.round((pricing.baseFee + distanceCharge + weightKg * pricing.perKgRate) * multiplier * 100) / 100;
  }

  return {
    keys, terminalStatuses, byId, money, text, statusText, escapeHtml, readArray, saveArray,
    session, basicAuthorization, signOut, requireRole, api, setMessage, detail, setupAddressAutocomplete, drawMap,
    estimatePrice
  };
})();
