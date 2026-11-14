(() => {
  const { byId, api, setMessage } = PP;
  let owner = null;
  let entries = [];
  let editing = null;
  let busy = false;

  function endpoint(id = '') {
    return `/api/address-book${id ? `/${encodeURIComponent(id)}` : ''}${owner === null ? '' : `?customer=${encodeURIComponent(owner)}`}`;
  }

  function resetForm() {
    editing = null;
    byId('addressForm').reset();
    byId('saveAddress').textContent = 'Add address';
    byId('cancelEdit').hidden = true;
  }

  function setBusy(value) {
    busy = value;
    byId('customerLookup').querySelector('button').disabled = value;
    byId('saveAddress').disabled = value;
    byId('cancelEdit').disabled = value;
    byId('addressList').querySelectorAll('button').forEach(button => { button.disabled = value; });
  }

  function render() {
    const list = byId('addressList');
    list.replaceChildren();
    if (!entries.length) {
      const empty = document.createElement('p');
      empty.className = 'field-note';
      empty.textContent = 'No saved addresses yet.';
      list.append(empty);
    }
    entries.forEach(entry => {
      const row = document.createElement('div');
      row.className = 'support-item';
      const description = document.createElement('p');
      const label = document.createElement('strong');
      label.textContent = entry.label;
      description.append(label, document.createTextNode(` — ${entry.address}`));
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'button button-quiet';
      edit.textContent = 'Edit';
      edit.addEventListener('click', () => {
        editing = entry.id;
        byId('addressLabel').value = entry.label;
        byId('addressValue').value = entry.address;
        byId('saveAddress').textContent = 'Save changes';
        byId('cancelEdit').hidden = false;
        byId('addressLabel').focus();
      });
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet';
      remove.textContent = 'Delete';
      remove.addEventListener('click', async () => {
        if (busy) return;
        if (!window.confirm(`Delete "${entry.label}"?`)) return;
        setBusy(true);
        try {
          await api(endpoint(entry.id), { method: 'DELETE' });
          if (editing === entry.id) resetForm();
          entries = entries.filter(item => item.id !== entry.id);
          render();
          setMessage('addressMessage', 'Address deleted.', 'success');
        } catch (error) {
          setMessage('addressMessage', `Could not delete address: ${error.message}`, 'error');
        } finally {
          setBusy(false);
        }
      });
      row.append(description, edit, remove);
      list.append(row);
    });
  }

  async function load() {
    entries = await api(endpoint());
    render();
  }

  async function initialize() {
    const user = await PP.requireRole(['CUSTOMER', 'ADMIN', 'DISPATCHER']);
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('backLink').href = user.role === 'CUSTOMER' ? '/customer.html' : user.role === 'ADMIN' ? '/admin.html' : '/dispatch.html';
    byId('addressBookView').hidden = false;
    byId('addressBookView').classList.add('active-view');
    PP.setupAddressAutocomplete('addressValue');
    byId('cancelEdit').addEventListener('click', resetForm);
    byId('addressForm').addEventListener('submit', async event => {
      event.preventDefault();
      if (busy) return;
      setBusy(true);
      try {
        const saved = await api(endpoint(editing || ''), {
          method: editing ? 'PUT' : 'POST',
          body: JSON.stringify({ label: byId('addressLabel').value, address: byId('addressValue').value })
        });
        entries = entries.filter(item => item.id !== saved.id);
        entries.push(saved);
        entries.sort((a, b) => a.label.localeCompare(b.label));
        resetForm();
        render();
        setMessage('addressMessage', 'Address saved.', 'success');
      } catch (error) {
        setMessage('addressMessage', `Could not save address: ${error.message}`, 'error');
      } finally {
        setBusy(false);
      }
    });
    if (user.role === 'CUSTOMER') {
      byId('bookContent').hidden = false;
      setBusy(true);
      try {
        await load();
      } catch (error) {
        setMessage('addressMessage', `Could not load addresses: ${error.message}`, 'error');
      } finally {
        setBusy(false);
      }
    } else {
      byId('customerLookup').hidden = false;
      byId('customerLookup').addEventListener('submit', async event => {
        event.preventDefault();
        if (busy) return;
        byId('bookContent').hidden = true;
        resetForm();
        owner = byId('customerEmail').value.trim().toLowerCase();
        setBusy(true);
        try {
          await load();
          byId('bookTitle').textContent = `Addresses for ${owner}`;
          byId('bookContent').hidden = false;
          setMessage('addressMessage', '');
        } catch (error) {
          setMessage('addressMessage', `Could not open address book: ${error.message}`, 'error');
        } finally {
          setBusy(false);
        }
      });
    }
  }

  initialize();
})();
