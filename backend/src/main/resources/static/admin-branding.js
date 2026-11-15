(() => {
  const { byId, api, setMessage } = PP;
  let currentLogo = { logoContentType: null, logoBase64: null };

  function fileData(file, maxBytes) {
    if (!['image/png', 'image/jpeg'].includes(file.type)) throw new Error('Choose a PNG or JPEG image.');
    if (!file.size || file.size > maxBytes) throw new Error(`Image must be no larger than ${maxBytes / 1024} KB.`);
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('Could not read the image.'));
      reader.onload = () => resolve(reader.result.split(',')[1]);
      reader.readAsDataURL(file);
    });
  }

  async function loadIdentity() {
    const site = await api('/api/branding/website');
    const branding = site.branding;
    byId('appName').value = site.configured ? branding.appName : 'ParcelPilot';
    byId('primaryColor').value = site.configured ? branding.primaryColor : '#102a43';
    byId('accentColor').value = site.configured ? branding.accentColor : '#087e83';
    currentLogo = { logoContentType: branding.logoContentType, logoBase64: branding.logoBase64 };
  }

  async function saveIdentity(event) {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      const file = byId('logo').files[0];
      let logo = currentLogo;
      if (byId('removeLogo').checked) logo = { logoContentType: null, logoBase64: null };
      if (file) logo = { logoContentType: file.type, logoBase64: await fileData(file, 256 * 1024) };
      const saved = await api('/api/admin/branding', {
        method: 'PUT',
        body: JSON.stringify({
          appName: byId('appName').value.trim(), primaryColor: byId('primaryColor').value,
          accentColor: byId('accentColor').value, ...logo
        })
      });
      currentLogo = { logoContentType: saved.logoContentType, logoBase64: saved.logoBase64 };
      byId('logo').value = '';
      byId('removeLogo').checked = false;
      setMessage('identityMessage', 'Identity saved. Refresh other pages to see the changes.', 'success');
    } catch (error) {
      setMessage('identityMessage', `Could not save identity: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function loadTheme() {
    const theme = await api('/api/admin/website-theme');
    byId('css').value = theme.css;
    byId('fontFamily').value = theme.fontFamily;
  }

  async function saveTheme(event) {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      await api('/api/admin/website-theme', {
        method: 'PUT', body: JSON.stringify({ css: byId('css').value, fontFamily: byId('fontFamily').value })
      });
      setMessage('themeMessage', 'Stylesheet saved. Refresh other pages to see the changes.', 'success');
    } catch (error) {
      setMessage('themeMessage', `Could not save stylesheet: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function loadImages() {
    const images = await api('/api/admin/branding/images');
    const list = byId('imageList');
    list.replaceChildren();
    if (!images.length) {
      list.textContent = 'No images uploaded.';
      return;
    }
    images.forEach(image => {
      const row = document.createElement('div');
      row.className = 'support-item';
      const name = document.createElement('strong');
      name.textContent = image.name;
      const url = document.createElement('input');
      url.value = image.url;
      url.readOnly = true;
      url.setAttribute('aria-label', `URL for ${image.name}`);
      url.addEventListener('focus', () => url.select());
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-quiet';
      remove.textContent = 'Delete';
      remove.addEventListener('click', async () => {
        if (!window.confirm(`Delete "${image.name}"? Any CSS using it will no longer display it.`)) return;
        try {
          await api(`/api/admin/branding/images/${encodeURIComponent(image.id)}`, { method: 'DELETE' });
          await loadImages();
          setMessage('imageMessage', 'Image deleted.', 'success');
        } catch (error) {
          setMessage('imageMessage', `Could not delete image: ${error.message}`, 'error');
        }
      });
      row.append(name, url, remove);
      list.append(row);
    });
  }

  async function uploadImage(event) {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      const file = byId('imageFile').files[0];
      await api('/api/admin/branding/images', {
        method: 'POST',
        body: JSON.stringify({ name: byId('imageName').value.trim(), contentType: file.type,
          data: await fileData(file, 1024 * 1024) })
      });
      byId('imageForm').reset();
      await loadImages();
      setMessage('imageMessage', 'Image uploaded.', 'success');
    } catch (error) {
      setMessage('imageMessage', `Could not upload image: ${error.message}`, 'error');
    } finally {
      button.disabled = false;
    }
  }

  async function initialize() {
    const user = await PP.requireRole('ADMIN');
    if (!user) return;
    byId('accountName').textContent = user.name;
    byId('signOutButton').addEventListener('click', PP.signOut);
    byId('brandingView').hidden = false;
    byId('brandingView').classList.add('active-view');
    byId('imageForm').addEventListener('submit', uploadImage);
    try {
      await loadIdentity();
      byId('identityForm').addEventListener('submit', saveIdentity);
    } catch (error) {
      byId('identityForm').querySelector('button').disabled = true;
      setMessage('identityMessage', `Could not load identity: ${error.message}`, 'error');
    }
    try {
      await loadTheme();
      byId('themeForm').addEventListener('submit', saveTheme);
      byId('defaultCss').addEventListener('click', async () => {
        if (!window.confirm('Replace the editor contents with the default CSS? Save to apply it.')) return;
        try {
          const defaults = await api('/api/admin/website-theme/default');
          byId('css').value = defaults.css;
          setMessage('themeMessage', 'Default CSS loaded. Save to apply it.');
        } catch (error) {
          setMessage('themeMessage', `Could not load default CSS: ${error.message}`, 'error');
        }
      });
    } catch (error) {
      byId('themeForm').querySelector('button').disabled = true;
      setMessage('themeMessage', `Could not load stylesheet: ${error.message}`, 'error');
    }
    try {
      await loadImages();
    } catch (error) {
      setMessage('imageMessage', `Could not load images: ${error.message}`, 'error');
    }
  }
  initialize();
})();
