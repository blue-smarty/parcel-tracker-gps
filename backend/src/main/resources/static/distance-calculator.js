(() => {
  const byId = id => document.getElementById(id);
  const form = byId('distanceForm');
  const fields = ['startLatitude', 'startLongitude', 'destinationLatitude', 'destinationLongitude'];
  const earthRadiusKm = 6371.0088;

  function readPoint(latitudeId, longitudeId) {
    const latitudeValue = byId(latitudeId).value.trim();
    const longitudeValue = byId(longitudeId).value.trim();
    if (!latitudeValue || !longitudeValue) {
      throw new Error('Enter both latitude and longitude for each location.');
    }
    const latitude = Number(latitudeValue);
    const longitude = Number(longitudeValue);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new Error('Enter valid coordinates (latitude -90 to 90 and longitude -180 to 180).');
    }
    return { latitude, longitude };
  }

  function calculateDistanceKm(start, destination) {
    const toRadians = degrees => degrees * Math.PI / 180;
    const latitudeDifference = toRadians(destination.latitude - start.latitude);
    const longitudeDifference = toRadians(destination.longitude - start.longitude);
    const haversine = Math.sin(latitudeDifference / 2) ** 2
      + Math.cos(toRadians(start.latitude)) * Math.cos(toRadians(destination.latitude))
      * Math.sin(longitudeDifference / 2) ** 2;
    return 2 * earthRadiusKm * Math.asin(Math.sqrt(Math.min(1, haversine)));
  }

  function setMessage(message, kind = '') {
    const element = byId('calculatorMessage');
    element.textContent = message;
    element.className = `form-message${kind ? ` ${kind}` : ''}`;
  }

  function calculate(event) {
    event.preventDefault();
    try {
      const start = readPoint('startLatitude', 'startLongitude');
      const destination = readPoint('destinationLatitude', 'destinationLongitude');
      const distanceKm = calculateDistanceKm(start, destination);
      byId('distanceKilometers').textContent = distanceKm.toLocaleString(undefined, { maximumFractionDigits: 2 });
      byId('distanceMiles').textContent = (distanceKm * 0.621371).toLocaleString(undefined, { maximumFractionDigits: 2 });
      const directions = new URLSearchParams({
        api: '1',
        origin: `${start.latitude},${start.longitude}`,
        destination: `${destination.latitude},${destination.longitude}`,
        travelmode: 'driving'
      });
      byId('openDirectionsLink').href = `https://www.google.com/maps/dir/?${directions}`;
      byId('distanceResult').hidden = false;
      setMessage('');
    } catch (error) {
      byId('distanceResult').hidden = true;
      setMessage(error.message, 'error');
    }
  }

  function swapPoints() {
    const startLatitude = byId('startLatitude').value;
    const startLongitude = byId('startLongitude').value;
    byId('startLatitude').value = byId('destinationLatitude').value;
    byId('startLongitude').value = byId('destinationLongitude').value;
    byId('destinationLatitude').value = startLatitude;
    byId('destinationLongitude').value = startLongitude;
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setMessage('Location is not available in this browser.', 'error');
      return;
    }
    byId('useLocationButton').disabled = true;
    setMessage('Getting your location…');
    navigator.geolocation.getCurrentPosition(position => {
      byId('startLatitude').value = position.coords.latitude;
      byId('startLongitude').value = position.coords.longitude;
      byId('useLocationButton').disabled = false;
      setMessage('Current location added as the start point.', 'success');
    }, () => {
      byId('useLocationButton').disabled = false;
      setMessage('Could not get your location. Check browser location permissions and try again.', 'error');
    }, { enableHighAccuracy: false, timeout: 10000 });
  }

  form.addEventListener('submit', calculate);
  byId('swapPointsButton').addEventListener('click', swapPoints);
  byId('useLocationButton').addEventListener('click', useCurrentLocation);
  form.addEventListener('reset', () => {
    byId('distanceResult').hidden = true;
    setMessage('');
  });
  fields.forEach(id => byId(id).addEventListener('input', () => {
    byId('distanceResult').hidden = true;
  }));
})();
