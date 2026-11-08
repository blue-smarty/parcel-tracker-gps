(() => {
  const { byId, api, money, setMessage } = PP;
  const fields = ['baseFee', 'perKmRate', 'perKgRate', 'sameDayMultiplier'];

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
    byId('reloadPricingButton').addEventListener('click', loadPricing);
    byId('dispatcherForm').addEventListener('submit', createDispatcher);
    fields.forEach(field => byId(field).addEventListener('input', renderPreview));
    byId('settingsView').hidden = false;
    byId('settingsView').classList.add('active-view');
    await Promise.all([loadPricing(), loadDispatchers()]);
  }

  initialize();
})();
