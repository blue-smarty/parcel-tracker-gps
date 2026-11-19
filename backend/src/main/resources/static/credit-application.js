(() => {
  const { byId, api, session, basicAuthorization, setMessage } = PP;
  let token = window.location.hash.slice(1);
  history.replaceState(null, '', window.location.pathname);

  function showIdentity(identity) {
    if (identity.role !== 'CUSTOMER') throw new Error('Sign in with the invited customer account.');
    byId('signedIn').textContent = `Signed in as ${identity.username}`;
    byId('loginForm').hidden = true;
    byId('applicationForm').hidden = false;
  }

  async function signIn(event) {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    session.set({ authorization: basicAuthorization(byId('email').value.trim(), byId('password').value) });
    try {
      const identity = await api('/api/auth/me');
      showIdentity(identity);
      setMessage('message', '');
    } catch (error) {
      session.clear();
      setMessage('message', `Could not sign in: ${error.message}`, 'error');
    } finally {
      byId('password').value = '';
      button.disabled = false;
    }
  }

  async function submit(event) {
    event.preventDefault();
    const button = byId('submitButton');
    button.disabled = true;
    try {
      await api('/api/credit/applications', {
        method: 'POST', body: JSON.stringify({
          token, businessName: byId('businessName').value.trim(), contactName: byId('contactName').value.trim(),
          contactEmail: byId('contactEmail').value.trim(), contactPhone: byId('contactPhone').value.trim(),
          requestedLimit: byId('requestedLimit').value
        })
      });
      token = '';
      byId('applicationForm').hidden = true;
      setMessage('message', 'Application submitted for administrator review.', 'success');
    } catch (error) {
      setMessage('message', `Could not submit application: ${error.message}`, 'error');
      button.disabled = false;
    }
  }

  async function initialize() {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
      byId('loginForm').hidden = true;
      setMessage('message', 'Open the original invitation link to apply.', 'error');
      return;
    }
    byId('loginForm').addEventListener('submit', signIn);
    byId('applicationForm').addEventListener('submit', submit);
    byId('changeAccount').addEventListener('click', () => {
      session.clear();
      byId('applicationForm').hidden = true;
      byId('loginForm').hidden = false;
      setMessage('message', '');
    });
    if (session.get()?.authorization) {
      try {
        showIdentity(await api('/api/auth/me'));
      } catch (error) {
        setMessage('message', 'Sign in with the invited customer account.', 'error');
      }
    }
  }
  initialize();
})();
