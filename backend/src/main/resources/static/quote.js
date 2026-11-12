(() => {
  const { byId, api, money, setMessage } = PP;
  const maxPackages = 10;
  const types = ['Carton/box', 'Documents', 'Satchel', 'Pallet', 'Other'];
  let routeRequest = 0;
  let pricing = null;

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

    const quantity = numberField('Quantity', 'quantity', 1, 20, '1');
    quantity.querySelector('input').value = '1';
    row.append(
      typeLabel,
      numberField('Weight (kg) *', 'weight', 0.1, 10000),
      numberField('Length (cm) *', 'length', 0.1, 10000),
      numberField('Width (cm) *', 'width', 0.1, 10000),
      numberField('Height (cm) *', 'height', 0.1, 10000),
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

  function packageWeight() {
    const rows = [...byId('quotePackages').children];
    if (!rows.length) throw new Error('Add at least one package item.');
    let total = 0;
    for (const row of rows) {
      const values = Object.fromEntries([...row.querySelectorAll('input')].map(input => [input.name, Number(input.value)]));
      for (const field of ['weight', 'length', 'width', 'height', 'quantity']) {
        if (!Number.isFinite(values[field]) || values[field] <= 0) {
          throw new Error('Enter a quantity, weight and dimensions greater than zero for each item.');
        }
      }
      if (!Number.isInteger(values.quantity) || values.quantity > 20 || values.weight > 10000 || values.length > 10000
          || values.width > 10000 || values.height > 10000) {
        throw new Error('Check the quantity, weight and dimensions for each item.');
      }
      total += values.weight * values.quantity;
    }
    if (!Number.isFinite(total)) throw new Error('The combined package weight is too large.');
    return total;
  }

  async function calculate(event) {
    event.preventDefault();
    const origin = byId('quoteOrigin').value.trim();
    const destination = byId('quoteDestination').value.trim();
    if (!origin || !destination) {
      setMessage('quoteMessage', 'Enter both origin and destination.', 'error');
      return;
    }
    let weight;
    try {
      weight = packageWeight();
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
      const [route, currentPricing] = await Promise.all([
        api('/api/booking-route', { method: 'POST', body: JSON.stringify({ origin, destination }) }),
        pricing ? Promise.resolve(pricing) : api('/api/pricing')
      ]);
      if (requestId !== routeRequest) return;
      if (!Number.isFinite(route.distanceMeters) || route.distanceMeters < 0
          || !Number.isFinite(route.durationSeconds) || route.durationSeconds < 0
          || !Array.isArray(route.points) || route.points.length < 2) {
        throw new Error('The route provider returned an invalid route.');
      }
      if (![currentPricing.baseFee, currentPricing.perKmRate, currentPricing.perKgRate]
          .every(value => Number.isFinite(value) && value >= 0)) {
        throw new Error('Current pricing is unavailable.');
      }
      pricing = currentPricing;
      const distance = Math.max(0.1, Math.round(route.distanceMeters / 100) / 10);
      const price = Math.round((pricing.baseFee + distance * pricing.perKmRate + weight * pricing.perKgRate) * 100) / 100;
      byId('quoteDistance').textContent = `${distance} km`;
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
})();
