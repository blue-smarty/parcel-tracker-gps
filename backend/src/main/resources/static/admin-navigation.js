(() => {
  const groups = {
    'delivery-pricing': ['Delivery & Pricing', 'Configure service targets, pricing metrics, and rate estimates.'],
    'booking-limits': ['Booking & Limits', 'Manage booking availability, parcel limits, and assignment timeouts.'],
    'fleet-access': ['Fleet & Access', 'Manage courier capacity and dispatcher accounts.'],
    'system-comms': ['System & Comms', 'Manage email, notifications, and configuration history.']
  };
  const panels = [...document.querySelectorAll('[data-admin-group]')];
  const links = [...document.querySelectorAll('[data-admin-nav]')];

  function showGroup() {
    const hash = window.location.hash.slice(1);
    const group = Object.hasOwn(groups, hash) ? hash : panels.find(panel => panel.id === hash)?.dataset.adminGroup || 'delivery-pricing';
    panels.forEach(panel => { panel.hidden = panel.dataset.adminGroup !== group; });
    links.forEach(link => {
      if (link.dataset.adminNav === group) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    document.getElementById('adminSectionTitle').textContent = groups[group][0];
    document.getElementById('adminSectionDescription').textContent = groups[group][1];
  }

  window.addEventListener('hashchange', showGroup);
  showGroup();
})();
