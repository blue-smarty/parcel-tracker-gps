self.addEventListener('push', event => {
  const body = event.data ? event.data.text() : 'Open ParcelPilot for an update.';
  event.waitUntil(self.registration.showNotification('ParcelPilot', { body }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(clients.openWindow('/courier.html'));
});
