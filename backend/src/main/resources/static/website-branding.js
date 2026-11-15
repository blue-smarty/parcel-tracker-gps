(() => {
  async function applyBranding() {
    try {
      const response = await fetch('/api/branding/website', { cache: 'no-store' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const { configured, branding, fontFamily } = await response.json();
      if (['Arial', 'Georgia', 'Verdana', 'Tahoma'].includes(fontFamily)) {
        const font = document.createElement('style');
        font.textContent = `body, h1, h2, h3 { font-family: ${fontFamily}, sans-serif; }`;
        document.head.append(font);
      }
      if (!configured) return;
      document.documentElement.style.setProperty('--navy', branding.primaryColor);
      document.documentElement.style.setProperty('--teal', branding.accentColor);
      document.title = document.title.replace('ParcelPilot', () => branding.appName);
      document.querySelectorAll('.brand').forEach(brand => {
        brand.setAttribute('aria-label', `${branding.appName} home`);
        const name = document.createElement('span');
        name.textContent = branding.appName;
        if (branding.logoBase64 && branding.logoContentType) {
          const logo = document.createElement('img');
          logo.src = `data:${branding.logoContentType};base64,${branding.logoBase64}`;
          logo.alt = '';
          logo.style.cssText = 'max-width:160px;max-height:48px;object-fit:contain';
          brand.replaceChildren(logo, name);
        } else {
          const mark = document.createElement('span');
          mark.className = 'brand-mark';
          mark.textContent = branding.appName.charAt(0).toUpperCase();
          brand.replaceChildren(mark, name);
        }
      });
      document.querySelectorAll('footer span').forEach(span => {
        span.childNodes.forEach(node => {
          if (node.nodeType === Node.TEXT_NODE) node.textContent = node.textContent.replace('PARCELPILOT', () => branding.appName);
        });
      });
    } catch (error) {
      console.error('Could not load website branding:', error);
    }
  }
  applyBranding();
})();
