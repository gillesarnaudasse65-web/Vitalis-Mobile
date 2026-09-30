(function () {
  if (window.__vitalisSelectedDateRun2 === "ready" || window.__vitalisSelectedDateRun2 === "initializing") return;
  window.__vitalisSelectedDateRun2 = "initializing";
  var bridge = window.VitalisAndroid || null;
  var key = 'vitalis-selected-date-v1';
  var active = '';

  function valid(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    var parts = value.split('-').map(Number);
    var d = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
    return d.getUTCFullYear() === parts[0] && d.getUTCMonth() + 1 === parts[1] &&
      d.getUTCDate() === parts[2];
  }
  function localToday() {
    var now = new Date();
    return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') +
      '-' + String(now.getDate()).padStart(2, '0');
  }
  function nativeValue(method) {
    try { return bridge && bridge[method] ? bridge[method]() : ''; } catch (_) { return ''; }
  }
  function display() {
    document.body.setAttribute('data-vitalis-selected-date', active);
    document.querySelectorAll('[data-vitalis-date-label]').forEach(function (node) {
      node.textContent = active;
    });
    document.querySelectorAll('input[type="date"]').forEach(function (input) {
      if (input.value !== active) input.value = active;
    });
  }
  function set(value) {
    if (!valid(value)) return false;
    active = value;
    try { localStorage.setItem(key, value); } catch (_) {}
    if (bridge && bridge.selectHealthDate) bridge.selectHealthDate(value);
    display();
    return true;
  }
  function get() {
    var nativeDate = nativeValue('getSelectedHealthDate');
    if (valid(nativeDate)) return nativeDate;
    return active;
  }
  function refresh() {
    var date = get();
    if (!valid(date)) date = localToday();
    if (bridge && bridge.refreshHealthDataForDate) bridge.refreshHealthDataForDate(date);
    else if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
    return date;
  }
  function select(value) {
    if (!set(value)) return false;
    refresh();
    return true;
  }
  function today() {
    var date = nativeValue('getTodayHealthDate');
    select(valid(date) ? date : localToday());
  }

  window.VitalisDate = {get:get, set:set, select:select, refresh:refresh, today:today};
  var stored = nativeValue('getSelectedHealthDate');
  if (!valid(stored)) {
    try { stored = localStorage.getItem(key); } catch (_) {}
  }
  set(valid(stored) ? stored : localToday());

  document.addEventListener('change', function (event) {
    var target = event.target;
    if (target && target.matches && target.matches('input[type="date"]')) select(target.value);
  });
  document.addEventListener('click', function (event) {
    var target = event.target && event.target.closest && event.target.closest('button,[role="button"]');
    if (!target) return;
    var label = (target.getAttribute('aria-label') || target.textContent || '').trim().toLowerCase();
    if (target.matches('[data-vitalis-today]') || label === "aujourd'hui" ||
        label === 'aujourd’hui' || label === 'today') today();
  });
  window.__vitalisSelectedDateRun2 = "ready";
})();
