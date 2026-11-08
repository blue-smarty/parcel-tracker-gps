(() => {
  const { byId, api, setMessage } = PP;

  async function loadCouriers() {
    const select = byId('adhocCourier');
    const current = select.value;
    byId('refreshCouriersButton').disabled = true;
    try {
      const couriers = await api('/api/dispatch/couriers');
      select.replaceChildren(new Option(couriers.length ? 'Choose a courier' : 'No couriers provisioned', ''));
      couriers.forEach(courier => select.add(new Option(courier.courierId, courier.courierId)));
      if (couriers.some(courier => courier.courierId === current)) select.value = current;
      setMessage('courierMessage', couriers.length ? '' : 'No couriers are provisioned yet.', couriers.length ? '' : 'error');
    } catch (error) {
      if (!current) select.replaceChildren(new Option('Could not load couriers', ''));
      setMessage('courierMessage', `Could not load couriers: ${error.message}`, 'error');
    } finally {
      byId('refreshCouriersButton').disabled = false;
    }
  }

  async function createAdhoc(event) {
    event.preventDefault();
    const button = byId('adhocButton');
    if (button.disabled) return;
    const fields = {
      sender: byId('adhocSender').value.trim(),
      customerContact: byId('adhocContact').value.trim(),
      pickupAddress: byId('adhocPickup').value.trim(),
      recipient: byId('adhocRecipient').value.trim(),
      destination: byId('adhocDestination').value.trim(),
      courierId: byId('adhocCourier').value
    };
    if (Object.values(fields).some(value => !value)) {
      setMessage('adhocMessage', 'Complete all fields and choose a courier.', 'error');
      return;
    }
    button.disabled = true;
    byId('viewDeliveryLink').hidden = true;
    setMessage('adhocMessage', 'Creating and assigning delivery…');
    try {
      const parcel = await api('/api/parcels/adhoc', { method: 'POST', body: JSON.stringify(fields) });
      byId('adhocForm').reset();
      const link = byId('viewDeliveryLink');
      link.href = `/dispatch.html?parcel=${encodeURIComponent(parcel.id)}`;
      link.hidden = false;
      setMessage('adhocMessage', `Created ${parcel.trackingNumber} and assigned it to ${fields.courierId}.`, 'success');
    } catch (error) {
      setMessage('adhocMessage', `Could not create delivery: ${error.message} Check the parcel queue before retrying if the connection was interrupted.`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function initialize() {
    const user = await PP.requireRole(['ADMIN', 'DISPATCHER']);
    if (!user) return;
    byId('adhocAdminLink').hidden = user.role !== 'ADMIN';
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('adhocForm').addEventListener('submit', createAdhoc);
    byId('refreshCouriersButton').addEventListener('click', loadCouriers);
    PP.setupAddressAutocomplete?.('adhocPickup');
    PP.setupAddressAutocomplete?.('adhocDestination');
    byId('adhocView').hidden = false;
    await loadCouriers();
  }

  initialize();
})();
