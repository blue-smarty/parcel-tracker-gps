(() => {
  const { byId, api, setMessage } = PP;
  byId('courierMessageForm').addEventListener('submit', async event => {
    event.preventDefault();
    const courierId = byId('messageCourierId').value.trim();
    const message = byId('courierMessageText').value.trim();
    if (!courierId || !message) {
      setMessage('courierMessageStatus', 'Enter a courier ID and message.', 'error');
      return;
    }
    const button = event.submitter;
    button.disabled = true;
    try {
      const result = await api(`/api/admin/couriers/${encodeURIComponent(courierId)}/push-message`, {
        method: 'POST', body: JSON.stringify({ message })
      });
      setMessage('courierMessageStatus', result.accepted
        ? `Accepted by ${result.accepted} push service(s); delivery is not guaranteed.`
        : 'No push service accepted the message. The courier may not be subscribed.', result.accepted ? 'success' : 'error');
      if (result.accepted) byId('courierMessageText').value = '';
    } catch (error) {
      setMessage('courierMessageStatus', error.message, 'error');
    } finally {
      button.disabled = false;
    }
  });
})();
