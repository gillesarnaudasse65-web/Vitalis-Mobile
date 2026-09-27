(function (root, factory) {
  var api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.VitalisUxCore = api;
})(typeof window !== "undefined" ? window : null, function () {
  "use strict";

  var THEMES = ["classic", "ocean", "dark", "amoled", "aurora", "system"];
  var ACCENTS = ["green", "ocean", "aqua", "indigo", "violet", "coral"];
  var WIDGETS = [
    {id:"score", title:"Score Vitalis", group:"hero", detail:"score"},
    {id:"activity", title:"Activité", group:"primary", detail:"activity"},
    {id:"heart", title:"Cœur", group:"primary", detail:"heart"},
    {id:"sleep", title:"Sommeil", group:"primary", detail:"sleep"},
    {id:"hydration", title:"Hydratation", group:"primary", detail:"hydration"},
    {id:"nutrition", title:"Nutrition", group:"secondary", detail:"nutrition"},
    {id:"recovery", title:"Récupération", group:"secondary", detail:"recovery"},
    {id:"mental", title:"Bien-être mental", group:"secondary", detail:"mental"},
    {id:"body", title:"Corps", group:"secondary", detail:"body"},
    {id:"coach", title:"Coach", group:"guidance", detail:"coach"},
    {id:"quick", title:"Actions rapides", group:"actions", detail:null},
    {id:"sources", title:"Sources santé", group:"status", detail:"sources"}
  ];
  var DEFAULT_ORDER = WIDGETS.map(function (item) { return item.id; });
  var PRESETS = {
    balanced: DEFAULT_ORDER.slice(),
    fitness: ["score","activity","heart","recovery","hydration","quick","coach","sources"],
    recovery: ["score","sleep","recovery","heart","mental","hydration","coach","sources","quick"],
    nutrition: ["score","nutrition","hydration","body","coach","quick","activity","sources"],
    minimal: ["score","activity","sleep","quick","sources"]
  };

  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function defaults() {
    return {
      version:1,
      theme:"classic",
      accent:"green",
      reducedMotion:false,
      density:"comfortable",
      order:DEFAULT_ORDER.slice(),
      hidden:[],
      preset:"balanced",
      manualHydration:{}
    };
  }
  function uniqueAllowed(values) {
    var seen = {};
    return (Array.isArray(values) ? values : []).filter(function (value) {
      if (DEFAULT_ORDER.indexOf(value) < 0 || seen[value]) return false;
      seen[value] = true;
      return true;
    });
  }
  function sanitize(input) {
    var value = input && typeof input === "object" ? input : {};
    var result = defaults();
    if (THEMES.indexOf(value.theme) >= 0) result.theme = value.theme;
    if (ACCENTS.indexOf(value.accent) >= 0) result.accent = value.accent;
    result.reducedMotion = value.reducedMotion === true;
    result.density = value.density === "compact" ? "compact" : "comfortable";
    var order = uniqueAllowed(value.order);
    result.order = order.concat(DEFAULT_ORDER.filter(function (id) { return order.indexOf(id) < 0; }));
    result.hidden = uniqueAllowed(value.hidden);
    result.preset = Object.prototype.hasOwnProperty.call(PRESETS, value.preset) ? value.preset : "custom";
    result.manualHydration = {};
    if (value.manualHydration && typeof value.manualHydration === "object") {
      Object.keys(value.manualHydration).slice(-90).forEach(function (date) {
        var amount = Number(value.manualHydration[date]);
        if (/^\d{4}-\d{2}-\d{2}$/.test(date) && isFinite(amount) && amount >= 0 && amount <= 20) {
          result.manualHydration[date] = Math.round(amount * 100) / 100;
        }
      });
    }
    return result;
  }
  function visible(settings) {
    var safe = sanitize(settings);
    return safe.order.filter(function (id) { return safe.hidden.indexOf(id) < 0; });
  }
  function toggle(settings, id, shown) {
    var safe = sanitize(settings);
    if (DEFAULT_ORDER.indexOf(id) < 0) return safe;
    safe.hidden = safe.hidden.filter(function (item) { return item !== id; });
    if (!shown) safe.hidden.push(id);
    safe.preset = "custom";
    return safe;
  }
  function move(settings, id, delta) {
    var safe = sanitize(settings);
    var index = safe.order.indexOf(id);
    if (index < 0) return safe;
    var target = Math.max(0, Math.min(safe.order.length - 1, index + delta));
    if (target === index) return safe;
    safe.order.splice(index, 1);
    safe.order.splice(target, 0, id);
    safe.preset = "custom";
    return safe;
  }
  function reorder(settings, sourceId, targetId) {
    var safe = sanitize(settings);
    var source = safe.order.indexOf(sourceId);
    var target = safe.order.indexOf(targetId);
    if (source < 0 || target < 0 || source === target) return safe;
    safe.order.splice(source, 1);
    safe.order.splice(target, 0, sourceId);
    safe.preset = "custom";
    return safe;
  }
  function applyPreset(settings, name) {
    var safe = sanitize(settings);
    if (!Object.prototype.hasOwnProperty.call(PRESETS, name)) return safe;
    safe.order = PRESETS[name].concat(DEFAULT_ORDER.filter(function (id) {
      return PRESETS[name].indexOf(id) < 0;
    }));
    safe.hidden = DEFAULT_ORDER.filter(function (id) { return PRESETS[name].indexOf(id) < 0; });
    safe.preset = name;
    return safe;
  }
  function metric(value, status) {
    var resolved = String(status || (value === null || value === undefined ? "NO_DATA" : "DATA")).toUpperCase();
    if (["DATA","NO_DATA","ERROR","PERMISSION_REQUIRED","UNSUPPORTED","LOADING"].indexOf(resolved) < 0) {
      resolved = resolved === "NOT_AUTHORIZED" ? "PERMISSION_REQUIRED" : "ERROR";
    }
    return {status:resolved, value:resolved === "DATA" ? value : null};
  }

  return {
    themes:THEMES.slice(),
    accents:ACCENTS.slice(),
    widgets:clone(WIDGETS),
    presets:clone(PRESETS),
    defaults:defaults,
    sanitize:sanitize,
    visible:visible,
    toggle:toggle,
    move:move,
    reorder:reorder,
    applyPreset:applyPreset,
    metric:metric
  };
});
