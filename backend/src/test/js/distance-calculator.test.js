const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function setup() {
  const ids = [
    'distanceForm', 'startLatitude', 'startLongitude', 'destinationLatitude', 'destinationLongitude',
    'distanceKilometers', 'distanceMiles', 'openDirectionsLink', 'distanceResult', 'calculatorMessage',
    'swapPointsButton', 'useLocationButton', 'clearPointsButton'
  ];
  const elements = new Map(ids.map(id => [id, {
    value: '', textContent: '', hidden: false, href: '', disabled: false, className: '',
    listeners: {}, addEventListener(type, callback) { this.listeners[type] = callback; }
  }]));
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../../main/resources/static/distance-calculator.js'), 'utf8'),
    {
      document: { getElementById: id => elements.get(id) },
      navigator: {},
      URLSearchParams
    }
  );
  return elements;
}

test('calculates great-circle distance and provides a driving-directions link', () => {
  const elements = setup();
  elements.get('startLatitude').value = '0';
  elements.get('startLongitude').value = '0';
  elements.get('destinationLatitude').value = '0';
  elements.get('destinationLongitude').value = '1';
  elements.get('distanceForm').listeners.submit({ preventDefault() {} });

  assert.equal(elements.get('distanceKilometers').textContent, '111.2');
  assert.equal(elements.get('distanceMiles').textContent, '69.09');
  assert.equal(elements.get('distanceResult').hidden, false);
  assert.match(elements.get('openDirectionsLink').href, /origin=0%2C0/);
  assert.match(elements.get('openDirectionsLink').href, /destination=0%2C1/);
});

test('rejects coordinates outside valid latitude and longitude ranges', () => {
  const elements = setup();
  elements.get('startLatitude').value = '91';
  elements.get('startLongitude').value = '0';
  elements.get('destinationLatitude').value = '0';
  elements.get('destinationLongitude').value = '1';
  elements.get('distanceForm').listeners.submit({ preventDefault() {} });

  assert.equal(elements.get('distanceResult').hidden, true);
  assert.match(elements.get('calculatorMessage').textContent, /valid coordinates/);
  assert.match(elements.get('calculatorMessage').className, /error/);
});
