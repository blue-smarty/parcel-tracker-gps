(() => {
  const { byId, api, session, basicAuthorization, setMessage } = PP;
  const destinations = { ADMIN: '/admin.html', CUSTOMER: '/customer.html' };

  function setMode(mode) {
    const registering = mode === 'register';
    byId('signInForm').hidden = registering;
    byId('registerForm').hidden = !registering;
    byId('signInTab').classList.toggle('active', !registering);
    byId('registerTab').classList.toggle('active', registering);
    setMessage('authMessage', '');
  }

  async function signIn(username, password) {
    const authorization = basicAuthorization(username, password);
    session.set({ authorization });
    try {
      const identity = await api('/api/auth/me');
      const destination = destinations[identity.role];
      if (!destination) {
        throw new Error('Courier accounts sign in with the mobile courier app.');
      }
      session.set({ authorization, username: identity.username, name: identity.name, role: identity.role });
      window.location.href = destination;
    } catch (error) {
      session.clear();
      throw error.status === 401 ? new Error('Incorrect email/username or password.') : error;
    }
  }

  async function onSignIn(event) {
    event.preventDefault();
    const button = byId('signInButton');
    button.disabled = true;
    setMessage('authMessage', 'Signing in…');
    try {
      await signIn(byId('signInUsername').value.trim(), byId('signInPassword').value);
    } catch (error) {
      setMessage('authMessage', error.message, 'error');
      byId('signInPassword').value = '';
    } finally {
      button.disabled = false;
    }
  }

  async function onRegister(event) {
    event.preventDefault();
    const password = byId('registerPassword').value;
    if (password !== byId('registerConfirm').value) {
      setMessage('authMessage', 'Passwords do not match.', 'error');
      return;
    }
    const email = byId('registerEmail').value.trim();
    const button = byId('registerButton');
    button.disabled = true;
    setMessage('authMessage', 'Creating your account…');
    try {
      session.clear();
      await api('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ name: byId('registerName').value.trim(), email, password })
      });
      await signIn(email, password);
    } catch (error) {
      setMessage('authMessage', error.message, 'error');
    } finally {
      byId('registerPassword').value = '';
      byId('registerConfirm').value = '';
      button.disabled = false;
    }
  }

  async function initialize() {
    byId('signInTab').addEventListener('click', () => setMode('signIn'));
    byId('registerTab').addEventListener('click', () => setMode('register'));
    byId('signInForm').addEventListener('submit', onSignIn);
    byId('registerForm').addEventListener('submit', onRegister);
    if (new URLSearchParams(window.location.search).get('mode') === 'register') setMode('register');
    const current = session.get();
    if (current && destinations[current.role]) {
      try {
        const identity = await api('/api/auth/me');
        if (destinations[identity.role]) window.location.replace(destinations[identity.role]);
      } catch (_) {
        session.clear();
      }
    }
  }

  initialize();
})();
