(() => {
  const { byId, api, money, setMessage, estimatePrice } = PP;
  let maxPackages = 10;
  let limits = { maxPackageLines: 10, maxItemsPerBooking: 200, maxQuantityPerItem: 20,
    maxItemWeightKg: 10000, maxDimensionCm: 10000 };
  const types = ['Carton/box', 'Documents', 'Satchel', 'Pallet', 'Other'];
  let routeRequest = 0;

  function numberField(label, name, min, max, step = '0.1') {
    const wrapper = document.createElement('label');
    wrapper.textContent = label;
    const input = document.createElement('input');
    input.type = 'number';
    input.name = name;
    input.min = String(min);
    input.max = String(max);
    input.step = step;
    input.required = true;
    wrapper.append(input);
    return wrapper;
  }

  function addPackage() {
    const host = byId('quotePackages');
    if (host.children.length >= maxPackages) {
      setMessage('quoteMessage', `You can add up to ${maxPackages} items.`, 'error');
      return;
    }

    const row = document.createElement('div');
    row.className = 'quote-package';
    const typeLabel = document.createElement('label');
    typeLabel.textContent = 'Package type';
    const type = document.createElement('select');
    type.name = 'packageType';
    type.setAttribute('aria-label', 'Package type');
    for (const name of types) {
      const option = document.createElement('option');
      option.value = name;
      option.textContent = name;
      type.append(option);
    }
    typeLabel.append(type);

    const quantity = numberField('Quantity', 'quantity', 1, limits.maxQuantityPerItem, '1');
    quantity.querySelector('input').value = '1';
    row.append(
      typeLabel,
      numberField('Weight (kg) *', 'weight', 0.1, limits.maxItemWeightKg),
      numberField('Length (cm) *', 'length', 0.1, limits.maxDimensionCm),
      numberField('Width (cm) *', 'width', 0.1, limits.maxDimensionCm),
      numberField('Height (cm) *', 'height', 0.1, limits.maxDimensionCm),
      quantity
    );
    if (host.children.length > 0) {
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet quote-remove';
      remove.textContent = 'Remove';
      remove.setAttribute('aria-label', 'Remove package item');
      remove.addEventListener('click', () => {
        row.remove();
        invalidateQuote();
      });
      row.append(remove);
    }
    host.append(row);
    invalidateQuote();
  }

  function invalidateQuote() {
    routeRequest++;
    byId('quoteResult').hidden = true;
    byId('calculateQuote').disabled = false;
  }

  async function loadLimits() {
    try {
      limits = await api('/api/operational-settings');
      maxPackages = limits.maxPackageLines;
      [...byId('quotePackages').children].forEach(row => {
        row.querySelectorAll('input').forEach(input => {
          input.max = String(input.name === 'quantity' ? limits.maxQuantityPerItem
            : input.name === 'weight' ? limits.maxItemWeightKg : limits.maxDimensionCm);
        });
      });
    } catch (error) {
      setMessage('quoteMessage', `Could not load current booking limits: ${error.message}`, 'error');
    }
  }

  function packageItems() {
    const rows = [...byId('quotePackages').children];
    if (!rows.length) throw new Error('Add at least one package item.');
    const items = [];
    for (const row of rows) {
      const values = Object.fromEntries([...row.querySelectorAll('input')].map(input => [input.name, Number(input.value)]));
      for (const field of ['weight', 'length', 'width', 'height', 'quantity']) {
        const maximum = field === 'quantity' ? limits.maxQuantityPerItem
          : field === 'weight' ? limits.maxItemWeightKg : limits.maxDimensionCm;
        if (!Number.isFinite(values[field]) || values[field] <= 0 || values[field] > maximum) {
          throw new Error('Enter a quantity, weight and dimensions greater than zero for each item.');
        }
      }
      if (!Number.isInteger(values.quantity)) {
        throw new Error('Check the quantity, weight and dimensions for each item.');
      }
      items.push(values);
    }
    return items;
  }

  async function calculate(event) {
    event.preventDefault();
    const origin = byId('quoteOrigin').value.trim();
    const destination = byId('quoteDestination').value.trim();
    if (!origin || !destination) {
      setMessage('quoteMessage', 'Enter both origin and destination.', 'error');
      return;
    }
    let items;
    try {
      items = packageItems();
    } catch (error) {
      setMessage('quoteMessage', error.message, 'error');
      return;
    }

    const requestId = ++routeRequest;
    const button = byId('calculateQuote');
    button.disabled = true;
    byId('quoteResult').hidden = true;
    setMessage('quoteMessage', 'Calculating your quote…');
    try {
      const [route, currentPricing, currentLimits] = await Promise.all([
        api('/api/booking-route', { method: 'POST', body: JSON.stringify({ origin, destination }) }),
        api('/api/pricing'),
        api('/api/operational-settings')
      ]);
      if (requestId !== routeRequest) return;
      if (!Number.isFinite(route.distanceMeters) || route.distanceMeters < 0
          || !Number.isFinite(route.durationSeconds) || route.durationSeconds < 0
          || !Number.isFinite(route.straightLineDistanceKm) || route.straightLineDistanceKm < 0
          || !Array.isArray(route.points) || route.points.length < 2) {
        throw new Error('The route provider returned an invalid route.');
      }
      if (![currentPricing.baseFee, currentPricing.perKmRate, currentPricing.perKgRate]
          .every(value => Number.isFinite(value) && value >= 0)) {
        throw new Error('Current pricing is unavailable.');
      }
      const weight = PP.chargeableWeight(currentPricing, items);
      limits = currentLimits;
      maxPackages = limits.maxPackageLines;
      if (items.length > currentLimits.maxPackageLines
          || items.reduce((sum, item) => sum + item.quantity, 0) > currentLimits.maxItemsPerBooking) {
        throw new Error('The package count exceeds current booking limits.');
      }
      const distance = Math.max(0.1, Math.round(route.distanceMeters / 100) / 10);
      const price = estimatePrice(currentPricing, distance, weight, 'SCHEDULED', route.straightLineDistanceKm);
      byId('quoteDistance').textContent = `${distance} km`;
      byId('quoteRadiusDistance').textContent = `${Math.round(route.straightLineDistanceKm * 100) / 100} km`;
      byId('quoteWeight').textContent = `${Math.round(weight * 100) / 100} kg`;
      byId('quotePrice').textContent = money(price);
      byId('quoteResult').hidden = false;
      setMessage('quoteMessage', '');
    } catch (error) {
      if (requestId === routeRequest) {
        const message = error.status === 503
          ? 'Route lookup is unavailable. Check that the backend has a valid TOMTOM_API_KEY with Search and Routing access and can connect to TomTom.'
          : error.status === 429
            ? 'Too many quote requests. Please wait a minute and try again.'
            : `Could not calculate the quote: ${error.message}`;
        setMessage('quoteMessage', message, 'error');
      }
    } finally {
      if (requestId === routeRequest) button.disabled = false;
    }
  }

  byId('quoteForm').addEventListener('submit', calculate);
  byId('addQuotePackage').addEventListener('click', addPackage);
  ['quoteOrigin', 'quoteDestination'].forEach(id => byId(id).addEventListener('input', invalidateQuote));
  byId('quotePackages').addEventListener('input', invalidateQuote);
  byId('quotePackages').addEventListener('change', invalidateQuote);
  addPackage();
  loadLimits();
})();
