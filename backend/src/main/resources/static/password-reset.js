(() => {
  const { byId, api, setMessage } = PP;
  const token = new URLSearchParams(window.location.search).get('token');

  function showResetForm() {
    byId('requestResetForm').hidden = true;
    byId('confirmResetForm').hidden = false;
    byId('resetHeading').textContent = 'Choose a new password';
    byId('resetDescription').textContent = 'Choose a password with 12 to 72 characters.';
  }

  async function requestReset(event) {
    event.preventDefault();
    const button = byId('requestResetButton');
    button.disabled = true;
    setMessage('resetMessage', 'Requesting reset instructions…');
    try {
      const result = await api('/api/auth/password-reset', {
        method: 'POST',
        body: JSON.stringify({ email: byId('resetEmail').value.trim() })
      });
      setMessage('resetMessage', result.message, 'success');
    } catch (error) {
      setMessage('resetMessage', error.message, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function confirmReset(event) {
    event.preventDefault();
    const password = byId('newPassword').value;
    if (password !== byId('confirmPassword').value) {
      setMessage('resetMessage', 'Passwords do not match.', 'error');
      return;
    }
    const button = byId('confirmResetButton');
    button.disabled = true;
    setMessage('resetMessage', 'Updating password…');
    try {
      const result = await api('/api/auth/password-reset/confirm', {
        method: 'POST',
        body: JSON.stringify({ token, password })
      });
      setMessage('resetMessage', result.message, 'success');
      byId('confirmResetForm').hidden = true;
      byId('resetDescription').textContent = 'Your password has been changed. You can now sign in.';
    } catch (error) {
      setMessage('resetMessage', error.message, 'error');
    } finally {
      byId('newPassword').value = '';
      byId('confirmPassword').value = '';
      button.disabled = false;
    }
  }

  byId('requestResetForm').addEventListener('submit', requestReset);
  byId('confirmResetForm').addEventListener('submit', confirmReset);
  if (token) showResetForm();
})();
