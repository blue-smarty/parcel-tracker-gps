window.PP = (() => {
  const keys = {
    tickets: 'parcelpilot.supportTickets',
    couriers: 'parcelpilot.courierIds',
    session: 'parcelpilot.session'
  };
  const terminalStatuses = new Set(['DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED']);
  const state = { csrfToken: null };
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
    if (identity.role !== role) {
      const home = { ADMIN: '/admin.html', CUSTOMER: '/customer.html' }[identity.role];
      window.location.replace(home || '/login.html');
      return null;
    }
    return { ...current, ...identity };
  }

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
    const current = session.get();
    if (current && current.authorization && !headers.has('Authorization')) {
      headers.set('Authorization', current.authorization);
    }
    headers.set('X-Requested-With', 'XMLHttpRequest');
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

  function detail(label, value) {
    return `<div class="detail-pair"><span>${escapeHtml(label)}</span><strong>${escapeHtml(text(value))}</strong></div>`;
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

  return {
    keys, terminalStatuses, byId, money, text, statusText, escapeHtml, readArray, saveArray,
    session, basicAuthorization, signOut, requireRole, api, setMessage, detail, drawMap
  };
})();
