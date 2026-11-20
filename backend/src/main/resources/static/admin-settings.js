(() => {
  const { byId, api, money, setMessage } = PP;
  const fields = ['baseFee', 'perKmRate', 'perKgRate', 'sameDayMultiplier'];
  let radiusBands = [];

  function applyServiceLevels(settings) {
    ['generalMinutes', 'expressMinutes', 'targetStart'].forEach(field => { byId(field).value = settings[field]; });
  }

  async function loadServiceLevels() {
    try {
      applyServiceLevels(await api('/api/service-levels'));
      setMessage('serviceLevelsMessage', 'Current service levels loaded.');
    } catch (error) {
      setMessage('serviceLevelsMessage', `Could not load service levels: ${error.message}`, 'error');
    }
  }

  async function saveServiceLevels(event) {
    event.preventDefault();
    const button = byId('saveServiceLevelsButton');
    if (button.disabled) return;
    button.disabled = true;
    try {
      const settings = await api('/api/admin/service-levels', {
        method: 'PUT',
        body: JSON.stringify({
          generalMinutes: Number(byId('generalMinutes').value),
          expressMinutes: Number(byId('expressMinutes').value),
          targetStart: byId('targetStart').value
        })
      });
      applyServiceLevels(settings);
      setMessage('serviceLevelsMessage', 'Service levels saved for new bookings.', 'success');
    } catch (error) {
      setMessage('serviceLevelsMessage', `Could not save service levels: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function renderRadiusBands() {
    const list = byId('radiusBandsList');
    list.replaceChildren();
    radiusBands.forEach((band, index) => {
      const row = document.createElement('div');
      row.className = 'radius-band-row';
      const range = document.createElement('label');
      range.textContent = index === radiusBands.length - 1 ? 'Maximum distance (km; blank = no limit)' : 'Up to (km)';
      const maximum = document.createElement('input');
      maximum.type = 'number';
      maximum.min = '0.001';
      maximum.max = '20000';
      maximum.step = '0.1';
      maximum.value = band.upToKm ?? '';
      maximum.placeholder = index === radiusBands.length - 1 ? 'Open-ended' : 'e.g. 5';
      maximum.required = index < radiusBands.length - 1;
      maximum.disabled = index === radiusBands.length - 1;
      const fee = document.createElement('label');
      fee.textContent = 'Distance charge ($)';
      const amount = document.createElement('input');
      amount.type = 'number';
      amount.min = '0';
      amount.max = '100000';
      amount.step = '0.01';
      amount.value = band.distanceFee ?? '';
      amount.required = true;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet';
      remove.textContent = 'Remove';
      const sync = () => {
        band.upToKm = maximum.value === '' ? null : Number(maximum.value);
        band.distanceFee = amount.value === '' ? null : Number(amount.value);
        renderPreview();
      };
      maximum.addEventListener('input', sync);
      amount.addEventListener('input', sync);
      remove.addEventListener('click', () => {
        radiusBands.splice(index, 1);
        if (radiusBands.length) radiusBands[radiusBands.length - 1].upToKm = null;
        renderRadiusBands();
        renderPreview();
      });
      range.append(maximum);
      fee.append(amount);
      row.append(range, fee, remove);
      list.append(row);
    });
  }

  function updatePricingMode() {
    const radius = byId('pricingMode').value === 'RADIUS';
    byId('perKmRateField').hidden = radius;
    byId('perKmRate').disabled = radius;
    byId('radiusBandsSection').hidden = !radius;
    renderPreview();
  }

  function readForm() {
    return {
      ...Object.fromEntries(fields.map(field => [field, Number(byId(field).value)])),
      pricingMode: byId('pricingMode').value,
      weightMode: byId('weightMode').value,
      cubicDivisor: Number(byId('cubicDivisor').value),
      radiusBands
    };
  }

  function renderPreview() {
    const settings = readForm();
    if (fields.some(field => !Number.isFinite(settings[field])) || settings.perKmRate < 0
        || settings.perKgRate < 0 || settings.baseFee < 0 || settings.sameDayMultiplier < 1) {
      byId('pricingPreview').textContent = 'Enter valid rates to preview example prices.';
      return;
    }
    try {
      const chargeable = PP.chargeableWeight(settings, [{ weight: 2, length: 50, width: 40, height: 25, quantity: 1 }]);
      const standard = PP.estimatePrice(settings, 10, chargeable, 'SCHEDULED', 10);
      const sameDay = PP.estimatePrice(settings, 10, chargeable, 'SAME_DAY', 10);
      byId('pricingPreview').textContent =
        `Example (10 km, 2 kg, 50 × 40 × 25 cm): scheduled ${money(standard)} · same-day ${money(sameDay)}`;
    } catch (error) {
      byId('pricingPreview').textContent = 'Enter a valid cubic divisor and radius bands to preview example prices.';
    }
  }

  function applySettings(settings) {
    fields.forEach(field => { byId(field).value = settings[field]; });
    byId('pricingMode').value = settings.pricingMode || 'ROUTE';
    byId('weightMode').value = settings.weightMode || 'ACTUAL';
    byId('cubicDivisor').value = settings.cubicDivisor ?? 5000;
    radiusBands = (settings.radiusBands || []).map(band => ({ ...band }));
    renderRadiusBands();
    updatePricingMode();
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

  function applySmtp(settings) {
    byId('smtpHost').value = settings?.host || '';
    byId('smtpPort').value = settings?.port || 587;
    byId('smtpFrom').value = settings?.fromAddress || '';
    byId('smtpSecurity').value = settings?.security || 'STARTTLS';
    byId('smtpAuth').checked = settings?.authentication || false;
    byId('smtpUsername').value = settings?.username || '';
  }

  async function loadSmtp() {
    try {
      const settings = await api('/api/admin/smtp');
      applySmtp(settings);
      setMessage('smtpMessage', settings ? 'Current SMTP settings loaded.' : 'Using deployment SMTP settings until saved here.');
    } catch (error) {
      setMessage('smtpMessage', `Could not load SMTP settings: ${error.message}`, 'error');
    }
  }

  async function saveSmtp(event) {
    event.preventDefault();
    const button = byId('saveSmtpButton');
    if (button.disabled) return;
    button.disabled = true;
    try {
      const settings = await api('/api/admin/smtp', { method: 'PUT', body: JSON.stringify({
        host: byId('smtpHost').value.trim(), port: Number(byId('smtpPort').value),
        fromAddress: byId('smtpFrom').value.trim(), security: byId('smtpSecurity').value,
        authentication: byId('smtpAuth').checked, username: byId('smtpUsername').value.trim()
      }) });
      applySmtp(settings);
      setMessage('smtpMessage', 'SMTP settings saved.', 'success');
    } catch (error) {
      setMessage('smtpMessage', `Could not save SMTP settings: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function clearSmtp() {
    if (!window.confirm('Remove saved SMTP settings and use deployment settings instead?')) return;
    const button = byId('clearSmtpButton');
    button.disabled = true;
    try {
      await api('/api/admin/smtp', { method: 'DELETE' });
      applySmtp(null);
      setMessage('smtpMessage', 'Saved SMTP settings removed; using deployment settings.', 'success');
    } catch (error) {
      setMessage('smtpMessage', `Could not remove SMTP settings: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
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
    byId('serviceLevelsForm').addEventListener('submit', saveServiceLevels);
    byId('reloadServiceLevelsButton').addEventListener('click', loadServiceLevels);
    byId('pricingMode').addEventListener('change', updatePricingMode);
    byId('addRadiusBandButton').addEventListener('click', () => {
      if (radiusBands.length >= 20) {
        setMessage('radiusBandsMessage', 'You can add up to 20 distance bands.', 'error');
        return;
      }
      if (radiusBands.length) {
        const last = radiusBands[radiusBands.length - 1];
        if (last.upToKm == null) last.upToKm = radiusBands.length > 1
          ? Number(radiusBands[radiusBands.length - 2].upToKm || 0) + 5 : 5;
      }
      radiusBands.push({ upToKm: null, distanceFee: null });
      renderRadiusBands();
      renderPreview();
      setMessage('radiusBandsMessage', '');
    });
    byId('reloadPricingButton').addEventListener('click', loadPricing);
    byId('dispatcherForm').addEventListener('submit', createDispatcher);
    byId('smtpForm').addEventListener('submit', saveSmtp);
    byId('reloadSmtpButton').addEventListener('click', loadSmtp);
    byId('clearSmtpButton').addEventListener('click', clearSmtp);
    byId('weightMode').addEventListener('change', renderPreview);
    byId('cubicDivisor').addEventListener('input', renderPreview);
    fields.forEach(field => byId(field).addEventListener('input', renderPreview));
    byId('settingsView').hidden = false;
    byId('settingsView').classList.add('active-view');
    await Promise.all([loadPricing(), loadDispatchers(), loadServiceLevels(), loadSmtp()]);
  }

  initialize();
})();
