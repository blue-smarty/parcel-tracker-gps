(() => {
  const { byId, api, escapeHtml, setMessage } = PP;

  async function load() {
    try {
      const [applications, accounts] = await Promise.all([
        api('/api/admin/credit/applications'), api('/api/admin/credit/accounts')
      ]);
      const accountHost = byId('accounts');
      accountHost.innerHTML = accounts.length ? accounts.map(email => `<div class="support-item">
        <strong>${escapeHtml(email)}</strong>
        <button class="button button-quiet" data-revoke="${escapeHtml(email)}" type="button">Revoke credit</button>
        </div>`).join('') : '<p class="field-note">No approved credit accounts.</p>';
      accountHost.querySelectorAll('[data-revoke]').forEach(button => button.addEventListener('click', () =>
        revoke(button.dataset.revoke)));
      const host = byId('applications');
      host.innerHTML = applications.length ? applications.map(item => `
        <div class="support-item">
          <div><strong>${escapeHtml(item.customerEmail)} · ${escapeHtml(item.status)}</strong>
            <small>Invited ${escapeHtml(new Date(item.invitedAt).toLocaleString())}${item.status === 'INVITED'
              ? ` · expires ${escapeHtml(new Date(item.expiresAt).toLocaleString())}` : ''}</small></div>
          ${item.submittedAt ? `<p>Business: ${escapeHtml(item.businessName)}<br>
            Contact: ${escapeHtml(item.contactName)} · ${escapeHtml(item.contactEmail)} · ${escapeHtml(item.contactPhone)}<br>
            Requested limit: ${escapeHtml(item.requestedLimit)}</p>` : ''}
          ${item.reviewedAt ? `<p>Reviewed by ${escapeHtml(item.reviewedBy)} on ${escapeHtml(new Date(item.reviewedAt).toLocaleString())}</p>` : ''}
          ${item.status === 'SUBMITTED' ? `<button class="button button-primary" data-approve="${escapeHtml(item.id)}" type="button">Approve</button>
            <button class="button button-quiet" data-reject="${escapeHtml(item.id)}" type="button">Reject</button>` : ''}
        </div>`).join('') : '<p class="field-note">No applications yet.</p>';
      host.querySelectorAll('[data-approve], [data-reject]').forEach(button => button.addEventListener('click', () =>
        review(button.dataset.approve || button.dataset.reject, !!button.dataset.approve)));
      setMessage('applicationsMessage', '');
    } catch (error) {
      setMessage('applicationsMessage', `Could not load applications: ${error.message}`, 'error');
    }
  }

  async function revoke(email) {
    if (!window.confirm(`Revoke the credit classification for ${email}?`)) return;
    try {
      await api('/api/admin/credit/accounts/revoke', { method: 'POST', body: JSON.stringify({ email }) });
      await load();
    } catch (error) {
      setMessage('applicationsMessage', `Could not revoke credit: ${error.message}`, 'error');
    }
  }

  async function review(id, approve) {
    if (!window.confirm(`${approve ? 'Approve' : 'Reject'} this credit application?`)) return;
    try {
      await api(`/api/admin/credit/applications/${encodeURIComponent(id)}/review`, {
        method: 'POST', body: JSON.stringify({ approve })
      });
      await load();
    } catch (error) {
      setMessage('applicationsMessage', `Could not review application: ${error.message}`, 'error');
    }
  }

  async function invite(event) {
    event.preventDefault();
    const button = byId('inviteButton');
    button.disabled = true;
    byId('inviteResult').hidden = true;
    byId('inviteLink').value = '';
    try {
      const result = await api('/api/admin/credit/invitations', {
        method: 'POST', body: JSON.stringify({ email: byId('inviteEmail').value.trim() })
      });
      byId('inviteLink').value = `${window.location.origin}/credit-application.html#${result.token}`;
      byId('inviteExpiry').textContent = `Expires ${new Date(result.expiresAt).toLocaleString()}.`;
      byId('inviteResult').hidden = false;
      setMessage('inviteMessage', 'Invitation created. Copy and share the link securely.', 'success');
      await load();
    } catch (error) {
      setMessage('inviteMessage', `Could not create invitation: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function initialize() {
    const user = await PP.requireRole('ADMIN');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('inviteForm').addEventListener('submit', invite);
    byId('refreshButton').addEventListener('click', load);
    byId('creditView').hidden = false;
    byId('creditView').classList.add('active-view');
    await load();
  }
  initialize();
})();
