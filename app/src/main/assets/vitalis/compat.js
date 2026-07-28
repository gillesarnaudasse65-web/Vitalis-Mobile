(function () {
  if (window.__vitalisNativeCompatibility) return;
  window.__vitalisNativeCompatibility = true;

  var STORE_KEY = "vitalis-native-journal-v1";
  var nativeBridge = window.VitalisAndroid || null;

  function normalize(value) {
    return String(value || "")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\s+/g, " ")
      .trim();
  }

  function journal() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY)) || []; }
    catch (_) { return []; }
  }

  function record(type, title, detail) {
    var entries = journal();
    var entry = {
      id: Date.now(),
      type: type,
      title: title,
      detail: detail || "",
      at: new Date().toISOString(),
      source: "Vitalis Android"
    };
    entries.push(entry);
    localStorage.setItem(STORE_KEY, JSON.stringify(entries.slice(-500)));
    window.dispatchEvent(new CustomEvent("vitalis-journal-entry", { detail: entry }));
    document.dispatchEvent(new CustomEvent("vitalis-journal-entry", { detail: entry }));
    return entry;
  }

  function addStyles() {
    if (document.getElementById("vitalis-native-compat-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-native-compat-style";
    style.textContent =
      ".vitalis-native-overlay{position:fixed;z-index:2147483646;inset:0;background:rgba(4,34,26,.58);display:flex;align-items:flex-end;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-native-sheet{width:100%;max-height:88vh;overflow:auto;background:#f8f6ef;border-radius:24px 24px 0 0;padding:19px 17px 28px;color:#14342b}" +
      ".vitalis-native-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}" +
      ".vitalis-native-head h3{margin:0;font-size:19px}.vitalis-native-close{border:0;border-radius:50%;width:35px;height:35px;font-size:22px;background:#e2ebe6}" +
      ".vitalis-native-field{margin-top:13px}.vitalis-native-field label{display:block;font-size:12px;color:#667a73;margin-bottom:6px}" +
      ".vitalis-native-field input,.vitalis-native-field select{width:100%;padding:12px;border:1px solid #cad8d1;border-radius:12px;background:#fff;color:#14342b;font:inherit}" +
      ".vitalis-native-save{width:100%;border:0;border-radius:13px;padding:13px;margin-top:17px;background:#063c30;color:#fff;font-weight:700}" +
      ".vitalis-native-toast{position:fixed;z-index:2147483647;left:16px;right:16px;bottom:90px;background:#063c30;color:#fff;border-radius:14px;padding:13px 15px;text-align:center;box-shadow:0 10px 30px rgba(0,0,0,.25);font-family:system-ui,-apple-system,sans-serif}";
    document.head.appendChild(style);
  }

  function toast(message) {
    addStyles();
    var old = document.querySelector(".vitalis-native-toast");
    if (old) old.remove();
    var el = document.createElement("div");
    el.className = "vitalis-native-toast";
    el.textContent = message;
    document.body.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.remove(); }, 2600);
  }

  function sheet(title, fields, onSave) {
    addStyles();
    var overlay = document.createElement("div");
    overlay.className = "vitalis-native-overlay";
    overlay.innerHTML =
      '<div class="vitalis-native-sheet"><div class="vitalis-native-head"><h3>' +
      title +
      '</h3><button class="vitalis-native-close" aria-label="Fermer">×</button></div>' +
      fields +
      '<button class="vitalis-native-save">Enregistrer</button></div>';
    document.body.appendChild(overlay);
    function close() { if (overlay.parentNode) overlay.remove(); }
    overlay.querySelector(".vitalis-native-close").onclick = close;
    overlay.addEventListener("click", function (event) {
      if (event.target === overlay) close();
    });
    overlay.querySelector(".vitalis-native-save").onclick = function () {
      if (onSave(overlay) !== false) close();
    };
  }

  function scanMeal() {
    var input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.setAttribute("capture", "environment");
    input.style.display = "none";
    input.onchange = function () {
      var file = input.files && input.files[0];
      if (!file) { input.remove(); return; }
      var entry = record("meal", "Repas photographié", file.name || "Photo du repas");
      var reader = new FileReader();
      reader.onload = function () {
        var detail = {
          entry: entry,
          fileName: file.name,
          mimeType: file.type,
          size: file.size,
          preview: reader.result
        };
        window.dispatchEvent(new CustomEvent("vitalis-meal-photo-selected", { detail: detail }));
        document.dispatchEvent(new CustomEvent("vitalis-meal-photo-selected", { detail: detail }));
        toast("Photo du repas enregistrée.");
        input.remove();
      };
      reader.onerror = function () {
        toast("Photo enregistrée, aperçu indisponible.");
        input.remove();
      };
      reader.readAsDataURL(file);
    };
    document.body.appendChild(input);
    input.click();
  }

  function logActivity() {
    sheet(
      "Enregistrer une activité",
      '<div class="vitalis-native-field"><label>Activité</label><select data-field="activity"><option>Marche</option><option>Course</option><option>Football</option><option>Musculation</option><option>Vélo</option><option>Natation</option><option>Autre</option></select></div>' +
      '<div class="vitalis-native-field"><label>Durée en minutes</label><input data-field="duration" type="number" min="1" value="30"></div>' +
      '<div class="vitalis-native-field"><label>Intensité</label><select data-field="intensity"><option>Modérée</option><option>Légère</option><option>Élevée</option></select></div>',
      function (root) {
        var name = root.querySelector('[data-field="activity"]').value;
        var duration = Number(root.querySelector('[data-field="duration"]').value);
        var intensity = root.querySelector('[data-field="intensity"]').value;
        if (!duration || duration < 1) { toast("Indiquez une durée valide."); return false; }
        var entry = record("activity", name, duration + " min • " + intensity);
        window.dispatchEvent(new CustomEvent("vitalis-activity-added", { detail: entry }));
        toast("Activité enregistrée.");
      }
    );
  }

  function addWater() {
    sheet(
      "Ajouter de l’eau",
      '<div class="vitalis-native-field"><label>Quantité</label><select data-field="water"><option value="0.25">250 ml</option><option value="0.33">330 ml</option><option value="0.5">500 ml</option><option value="0.75">750 ml</option><option value="1">1 litre</option></select></div>',
      function (root) {
        var litres = Number(root.querySelector('[data-field="water"]').value);
        var entry = record("water", "Hydratation", Math.round(litres * 1000) + " ml");
        window.dispatchEvent(new CustomEvent("vitalis-manual-health-data", {
          detail: { hydrationLitres: litres, entry: entry }
        }));
        toast("Hydratation enregistrée.");
      }
    );
  }

  function addMeasure() {
    sheet(
      "Ajouter une mesure",
      '<div class="vitalis-native-field"><label>Type</label><select data-field="measure"><option>Poids</option><option>Tension artérielle</option><option>Glycémie</option><option>Température</option><option>SpO₂</option><option>Fréquence cardiaque</option></select></div>' +
      '<div class="vitalis-native-field"><label>Valeur</label><input data-field="value" placeholder="Ex. 78 kg ou 120/80"></div>',
      function (root) {
        var type = root.querySelector('[data-field="measure"]').value;
        var value = root.querySelector('[data-field="value"]').value.trim();
        if (!value) { toast("Indiquez la valeur mesurée."); return false; }
        var entry = record("measure", type, value);
        window.dispatchEvent(new CustomEvent("vitalis-measure-added", { detail: entry }));
        toast("Mesure enregistrée.");
      }
    );
  }

  function healthConnect() {
    if (nativeBridge && nativeBridge.requestHealthConnectPermissions) {
      nativeBridge.requestHealthConnectPermissions();
    } else {
      toast("Health Connect nécessite l’application Android Vitalis.");
    }
  }

  function actionFor(label) {
    if (/scanner.*repas|scan.*repas|scanner.*meal|scan.*meal|photo.*repas/.test(label)) return scanMeal;
    if (/enregistrer.*activite|ajouter.*activite|log.*activity|record.*activity/.test(label)) return logActivity;
    if (/ajouter.*eau|hydratation|add.*water/.test(label)) return addWater;
    if (/ajouter.*mesure|enregistrer.*mesure|add.*measure/.test(label)) return addMeasure;
    if (/autoriser.*health connect|connecter.*health connect|health connect.*autoriser/.test(label)) return healthConnect;
    return null;
  }

  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest
      ? event.target.closest("button,a,[role='button']")
      : null;
    if (!target) return;
    var action = actionFor(normalize(target.innerText || target.textContent || target.getAttribute("aria-label")));
    if (!action) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    action();
  }, true);

  window.VitalisNativeActions = {
    scanMeal: scanMeal,
    logActivity: logActivity,
    addWater: addWater,
    addMeasure: addMeasure,
    requestHealthConnectPermissions: healthConnect,
    refreshHealthData: function () {
      if (nativeBridge && nativeBridge.refreshHealthData) nativeBridge.refreshHealthData();
    },
    openOfflineMode: function () {
      if (nativeBridge && nativeBridge.openOfflineMode) nativeBridge.openOfflineMode();
    },
    getJournal: journal
  };

  window.dispatchEvent(new CustomEvent("vitalis-native-compat-ready", {
    detail: { platform: "android", version: "3.6" }
  }));

  setTimeout(function () {
    var text = normalize(document.body && document.body.innerText);
    var shortErrorPage = text.length > 0 && text.length < 1500 &&
      /momentanement inaccessible|site inaccessible|something went wrong|service unavailable/.test(text);
    if (shortErrorPage && nativeBridge && nativeBridge.openOfflineMode) {
      nativeBridge.openOfflineMode();
    }
  }, 1400);
})();

(function () {
  if (window.__vitalisConnectorVoiceControls) return;
  window.__vitalisConnectorVoiceControls = true;

  var bridge = window.VitalisAndroid || null;
  var lastData = {};
  var connectorState = { connectors: [], connectorCount: 0 };
  var refreshRequested = false;
  var microphoneOn = bridge && bridge.isMicrophoneEnabled ? bridge.isMicrophoneEnabled() : false;
  var speaking = bridge && bridge.isSpeaking ? bridge.isSpeaking() : false;

  function parseBridge(method, fallback) {
    try { return JSON.parse(method.call(bridge)); }
    catch (_) { return fallback; }
  }

  if (bridge && bridge.getLastHealthData) lastData = parseBridge(bridge.getLastHealthData, {});
  if (bridge && bridge.getConnectorStatus) connectorState = parseBridge(bridge.getConnectorStatus, connectorState);

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char];
    });
  }

  function installStyles() {
    if (document.getElementById("vitalis-connector-controls-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-connector-controls-style";
    style.textContent =
      ".vitalis-control-dock{position:fixed;z-index:2147483600;right:12px;bottom:86px;display:flex;flex-direction:column;gap:8px;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-control-button{border:0;border-radius:999px;background:#063c30;color:white;min-width:48px;height:48px;padding:0 13px;display:flex;align-items:center;justify-content:center;gap:7px;box-shadow:0 7px 22px rgba(6,60,48,.28);font-weight:700}" +
      ".vitalis-control-button span{font-size:11px}.vitalis-control-button.active{background:#b52727}.vitalis-control-button.loading{opacity:.7}" +
      ".vitalis-source-overlay{position:fixed;z-index:2147483646;inset:0;background:rgba(4,34,26,.58);display:flex;align-items:flex-end;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-source-sheet{width:100%;max-height:90vh;overflow:auto;background:#f8f6ef;border-radius:24px 24px 0 0;padding:18px 16px 30px;color:#14342b}" +
      ".vitalis-source-head{display:flex;justify-content:space-between;align-items:center;position:sticky;top:-18px;background:#f8f6ef;padding:12px 0;z-index:2}.vitalis-source-head h3{margin:0}.vitalis-source-close{border:0;border-radius:50%;width:36px;height:36px;font-size:22px;background:#e0ebe5}" +
      ".vitalis-source-summary{font-size:12px;color:#667b73;margin:2px 0 12px}.vitalis-source-row{background:#fff;border:1px solid #dbe6e0;border-radius:15px;padding:12px;margin:9px 0}" +
      ".vitalis-source-top{display:flex;justify-content:space-between;gap:12px}.vitalis-source-top b{font-size:14px}.vitalis-source-value{font-weight:800;color:#063c30}.vitalis-source-meta{font-size:11px;color:#677b73;margin-top:6px;line-height:1.45}" +
      ".vitalis-source-section{font-size:15px;margin:19px 2px 8px}.vitalis-source-badge{display:inline-block;border-radius:999px;background:#e2f3e9;color:#063c30;padding:6px 9px;font-size:11px;margin:4px 4px 0 0}" +
      "@media(max-width:420px){.vitalis-control-button span{display:none}.vitalis-control-button{width:48px;padding:0}}";
    document.head.appendChild(style);
  }

  function formatDate(value) {
    if (!value) return "Date indisponible";
    try { return new Intl.DateTimeFormat("fr-FR", {dateStyle:"short",timeStyle:"short"}).format(new Date(value)); }
    catch (_) { return value; }
  }

  function metricDefinitions() {
    return [
      ["steps","Pas",function(v){return Number(v || 0).toLocaleString("fr-FR");}],
      ["sleepMinutes","Sommeil",function(v){return v == null ? "—" : (Number(v)/60).toFixed(1)+" h";}],
      ["exerciseMinutes","Activité",function(v){return v == null ? "—" : Math.round(v)+" min";}],
      ["averageHeartRate","Fréquence cardiaque",function(v){return v == null ? "—" : Math.round(v)+" bpm";}],
      ["hydrationLitres","Hydratation",function(v){return v == null ? "—" : Number(v).toFixed(2)+" L";}],
      ["distanceKm","Distance",function(v){return v == null ? "—" : Number(v).toFixed(2)+" km";}],
      ["activeCalories","Calories actives",function(v){return v == null ? "—" : Math.round(v)+" kcal";}],
      ["oxygenPercent","Oxygène sanguin",function(v){return v == null ? "—" : Number(v).toFixed(1)+" %";}],
      ["weightKg","Poids",function(v){return v == null ? "—" : Number(v).toFixed(1)+" kg";}]
    ];
  }

  function showSourceReport() {
    installStyles();
    var old = document.querySelector(".vitalis-source-overlay");
    if (old) old.remove();
    var attribution = lastData.attribution || {};
    var metrics = metricDefinitions().map(function (definition) {
      var key = definition[0], title = definition[1], formatter = definition[2];
      var source = attribution[key] || {};
      var contributors = source.contributors || [];
      return '<div class="vitalis-source-row"><div class="vitalis-source-top"><b>' +
        escapeHtml(title) + '</b><span class="vitalis-source-value">' +
        escapeHtml(formatter(lastData[key])) + '</span></div><div class="vitalis-source-meta">Dernière source : <b>' +
        escapeHtml(source.lastConnector || "Aucune source") + '</b><br>Dernière donnée : ' +
        escapeHtml(formatDate(source.lastRecordAt)) + '<br>Contributeurs : ' +
        escapeHtml(contributors.length ? contributors.join(", ") : "Aucun") + '</div></div>';
    }).join("");

    var connectors = (connectorState.connectors || []).map(function (item) {
      return '<span class="vitalis-source-badge">' + escapeHtml(item.name || item.packageName) + '</span>';
    }).join("");

    var overlay = document.createElement("div");
    overlay.className = "vitalis-source-overlay";
    overlay.innerHTML =
      '<div class="vitalis-source-sheet"><div class="vitalis-source-head"><div><h3>Données et sources</h3><div class="vitalis-source-summary">Dernière synchronisation : ' +
      escapeHtml(formatDate(lastData.syncedAt)) + ' • ' + Number(connectorState.connectorCount || lastData.connectorCount || 0) +
      ' connecteur(s) détecté(s)</div></div><button class="vitalis-source-close" aria-label="Fermer">×</button></div>' +
      metrics + '<h4 class="vitalis-source-section">Connecteurs détectés sans limite fixe</h4><div>' +
      (connectors || '<span class="vitalis-source-summary">Aucune source n’a encore transmis de données via Health Connect.</span>') +
      '</div></div>';
    document.body.appendChild(overlay);
    overlay.querySelector(".vitalis-source-close").onclick = function () { overlay.remove(); };
    overlay.addEventListener("click", function (event) { if (event.target === overlay) overlay.remove(); });
  }

  function createControls() {
    installStyles();
    if (document.getElementById("vitalis-native-control-dock")) return;
    var dock = document.createElement("div");
    dock.id = "vitalis-native-control-dock";
    dock.className = "vitalis-control-dock";
    dock.innerHTML =
      '<button class="vitalis-control-button" data-vitalis-control="refresh" aria-label="Actualiser les données" title="Actualiser les données">↻ <span>Actualiser</span></button>' +
      '<button class="vitalis-control-button" data-vitalis-control="voice" aria-label="Activer ou arrêter la voix" title="Voix du coach">🔊 <span>Voix</span></button>' +
      '<button class="vitalis-control-button" data-vitalis-control="microphone" aria-label="Activer ou couper le micro" title="Microphone">🎙️ <span>Micro</span></button>';
    document.body.appendChild(dock);

    dock.querySelector('[data-vitalis-control="refresh"]').onclick = function () {
      refreshRequested = true;
      this.classList.add("loading");
      this.innerHTML = '⟳ <span>Sync…</span>';
      if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
      else showSourceReport();
    };
    dock.querySelector('[data-vitalis-control="voice"]').onclick = function () {
      if (!bridge || !bridge.speakText) return;
      if (speaking || (bridge.isSpeaking && bridge.isSpeaking())) {
        bridge.stopSpeaking();
        speaking = false;
        updateControlStates();
        return;
      }
      var message = buildHealthSummary();
      bridge.speakText(message, "fr-FR");
    };
    dock.querySelector('[data-vitalis-control="microphone"]').onclick = function () {
      microphoneOn = !microphoneOn;
      if (bridge && bridge.setMicrophoneEnabled) bridge.setMicrophoneEnabled(microphoneOn);
      updateControlStates();
    };
    updateControlStates();
  }

  function buildHealthSummary() {
    if (!lastData || !lastData.syncedAt) return "Aucune donnée récente. Appuyez sur Actualiser et autorisez Health Connect.";
    var parts = ["Voici votre résumé Vitalis."];
    if (lastData.steps != null) parts.push(Math.round(lastData.steps) + " pas.");
    if (lastData.sleepMinutes != null) parts.push((Number(lastData.sleepMinutes)/60).toFixed(1) + " heures de sommeil.");
    if (lastData.averageHeartRate != null) parts.push("Fréquence cardiaque moyenne " + Math.round(lastData.averageHeartRate) + " battements par minute.");
    if (lastData.hydrationLitres != null) parts.push("Hydratation " + Number(lastData.hydrationLitres).toFixed(1) + " litres.");
    return parts.join(" ");
  }

  function updateControlStates() {
    var mic = document.querySelector('[data-vitalis-control="microphone"]');
    var voice = document.querySelector('[data-vitalis-control="voice"]');
    if (mic) {
      mic.classList.toggle("active", microphoneOn);
      mic.innerHTML = microphoneOn ? '⏹ <span>Couper micro</span>' : '🎙️ <span>Micro</span>';
      mic.setAttribute("aria-pressed", microphoneOn ? "true" : "false");
    }
    if (voice) {
      voice.classList.toggle("active", speaking);
      voice.innerHTML = speaking ? '⏹ <span>Arrêter voix</span>' : '🔊 <span>Voix</span>';
      voice.setAttribute("aria-pressed", speaking ? "true" : "false");
    }
  }

  window.addEventListener("vitalis-health-data", function (event) {
    lastData = event.detail || {};
    if (refreshRequested) {
      refreshRequested = false;
      var button = document.querySelector('[data-vitalis-control="refresh"]');
      if (button) {
        button.classList.remove("loading");
        button.innerHTML = '↻ <span>Actualiser</span>';
      }
      showSourceReport();
    }
  });

  window.addEventListener("vitalis-connectors", function (event) {
    connectorState = event.detail || connectorState;
  });

  window.addEventListener("vitalis-sync-state", function (event) {
    var status = event.detail && event.detail.status;
    var button = document.querySelector('[data-vitalis-control="refresh"]');
    if (button && (status === "error" || status === "permission_required" || status === "unavailable")) {
      button.classList.remove("loading");
      button.innerHTML = '↻ <span>Actualiser</span>';
      refreshRequested = false;
    }
  });

  window.addEventListener("vitalis-voice-state", function (event) {
    var detail = event.detail || {};
    microphoneOn = !!detail.microphoneEnabled;
    speaking = !!detail.speaking;
    updateControlStates();
  });

  window.addEventListener("vitalis-voice-input", function (event) {
    window.dispatchEvent(new CustomEvent("vitalis-coach-voice-input", { detail: event.detail }));
  });

  window.VitalisConnectorControls = {
    refresh: function () { if (bridge && bridge.refreshHealthData) bridge.refreshHealthData(); },
    showSources: showSourceReport,
    microphoneOn: function () { if (bridge && bridge.setMicrophoneEnabled) bridge.setMicrophoneEnabled(true); },
    microphoneOff: function () { if (bridge && bridge.setMicrophoneEnabled) bridge.setMicrophoneEnabled(false); },
    speak: function (text, language) { if (bridge && bridge.speakText) bridge.speakText(text, language || "fr-FR"); },
    stopSpeaking: function () { if (bridge && bridge.stopSpeaking) bridge.stopSpeaking(); }
  };

  createControls();
})();

(function () {
  if (window.__vitalisDeepDetails) return;
  window.__vitalisDeepDetails = true;

  var bridge = window.VitalisAndroid || null;
  var healthData = {};
  var pendingPanel = null;

  function readNativeData() {
    try { return bridge && bridge.getLastHealthData ? JSON.parse(bridge.getLastHealthData()) : {}; }
    catch (_) { return {}; }
  }
  healthData = readNativeData();

  function norm(value) {
    return String(value || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim();
  }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];
    });
  }
  function date(value) {
    if (!value) return "Date indisponible";
    try { return new Intl.DateTimeFormat("fr-FR",{dateStyle:"short",timeStyle:"short"}).format(new Date(value)); }
    catch (_) { return String(value); }
  }
  function number(value, decimals) {
    var n = Number(value);
    return isFinite(n) ? n.toLocaleString("fr-FR",{minimumFractionDigits:decimals||0,maximumFractionDigits:decimals||0}) : "—";
  }

  function styles() {
    if (document.getElementById("vitalis-deep-details-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-deep-details-style";
    style.textContent =
      ".vitalis-deep-overlay{position:fixed;z-index:2147483647;inset:0;background:rgba(20,30,27,.58);display:flex;align-items:flex-end;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-deep-sheet{width:100%;max-height:92vh;overflow:auto;background:#f7f7f7;border-radius:26px 26px 0 0;padding:18px 17px 32px;color:#171b19}" +
      ".vitalis-deep-head{position:sticky;top:-18px;background:#f7f7f7;z-index:3;padding:12px 0;display:flex;align-items:center;justify-content:space-between}.vitalis-deep-head h3{margin:0;font-size:20px}.vitalis-deep-close{border:0;border-radius:50%;width:36px;height:36px;background:#e6e8e7;font-size:22px}" +
      ".vitalis-deep-score{text-align:center;background:#fff;border-radius:20px;padding:18px;margin-bottom:12px}.vitalis-deep-score strong{font-size:42px}.vitalis-deep-score small{display:block;color:#747b78;margin-top:4px}" +
      ".vitalis-deep-card{background:#fff;border-radius:17px;padding:14px;margin:9px 0;border:1px solid #eceeed}.vitalis-deep-top{display:flex;align-items:center;justify-content:space-between;gap:10px}.vitalis-deep-top b{font-size:15px}.vitalis-deep-points{font-weight:800;color:#d84d60}" +
      ".vitalis-deep-bar{height:7px;background:#edf0ee;border-radius:8px;overflow:hidden;margin:10px 0}.vitalis-deep-bar i{display:block;height:100%;background:#d95568;border-radius:8px}.vitalis-deep-meta{font-size:12px;line-height:1.5;color:#6f7773}.vitalis-deep-action{border:0;background:transparent;color:#d84d60;font-weight:700;padding:9px 0 0}" +
      ".vitalis-deep-section{font-size:16px;margin:20px 2px 8px}.vitalis-deep-macro{display:grid;grid-template-columns:1fr auto;gap:7px;padding:8px 0;border-bottom:1px solid #eff1f0}.vitalis-deep-macro:last-child{border:0}" +
      ".vitalis-deep-empty{text-align:center;color:#777f7b;background:#fff;border-radius:17px;padding:26px 14px}.vitalis-deep-note{font-size:11px;color:#7a817e;line-height:1.45;margin-top:14px}";
    document.head.appendChild(style);
  }

  function panel(title, body) {
    styles();
    var old = document.querySelector(".vitalis-deep-overlay");
    if (old) old.remove();
    var overlay = document.createElement("div");
    overlay.className = "vitalis-deep-overlay";
    overlay.innerHTML = '<div class="vitalis-deep-sheet"><div class="vitalis-deep-head"><h3>' +
      esc(title) + '</h3><button class="vitalis-deep-close" aria-label="Fermer">×</button></div>' + body + '</div>';
    document.body.appendChild(overlay);
    overlay.querySelector(".vitalis-deep-close").onclick = function () { overlay.remove(); };
    overlay.addEventListener("click", function (event) { if (event.target === overlay) overlay.remove(); });
    overlay.querySelectorAll("[data-vitalis-deep-category]").forEach(function (button) {
      button.onclick = function () { showCategory(this.getAttribute("data-vitalis-deep-category")); };
    });
  }

  function ensureData(nextPanel) {
    healthData = readNativeData();
    if (healthData && healthData.syncedAt) { nextPanel(); return; }
    pendingPanel = nextPanel;
    panel("Synchronisation", '<div class="vitalis-deep-empty">Récupération des données Health Connect…</div>');
    if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
  }

  function showScore() {
    ensureData(function () {
      var score = healthData.scoreBreakdown || {overall:0,maximum:100,components:[]};
      var components = (score.components || []).map(function (item) {
        return '<div class="vitalis-deep-card"><div class="vitalis-deep-top"><b>' + esc(item.label) +
          '</b><span class="vitalis-deep-points">' + number(item.earnedPoints) + ' / ' + number(item.maxPoints) +
          ' pts</span></div><div class="vitalis-deep-bar"><i style="width:' + Math.max(2,Number(item.percentage)||0) +
          '%"></i></div><div class="vitalis-deep-meta">Actuel : <b>' + esc(item.current) +
          '</b><br>Objectif : ' + esc(item.target) + '<br>' + esc(item.explanation) +
          (item.available ? "" : "<br><b>Données manquantes : actualisez ou autorisez la source.</b>") +
          '</div><button class="vitalis-deep-action" data-vitalis-deep-category="' + esc(item.key) +
          '">Voir les détails →</button></div>';
      }).join("");
      panel("Comprendre mon score",
        '<div class="vitalis-deep-score"><strong>' + number(score.overall) + '</strong> / ' +
        number(score.maximum || 100) + '<small>' + esc(score.method || "") + '</small></div>' +
        components + '<p class="vitalis-deep-note">' + esc(score.medicalDisclaimer || "") + '</p>');
    });
  }

  function nutritionBody() {
    var n = healthData.nutrition || {}, goals = n.goals || {};
    var macros = [
      ["Glucides","carbohydratesGrams","g",0],
      ["Protéines","proteinGrams","g",0],
      ["Graisses","fatGrams","g",0],
      ["Fibre","fiberGrams","g",0],
      ["Sucre","sugarGrams","g",0],
      ["Sodium","sodiumMilligrams","mg",0],
      ["Calories","caloriesKcal","kcal",0]
    ];
    var summary = macros.map(function (m) {
      return '<div class="vitalis-deep-macro"><span>' + m[0] + '</span><b>' +
        number(n[m[1]],m[3]) + ' ' + m[2] + ' / ' + number(goals[m[1]],m[3]) + ' ' + m[2] + '</b></div>';
    }).join("");
    var meals = ((healthData.details || {}).nutrition || []);
    var mealHtml = meals.map(function (meal) {
      return '<div class="vitalis-deep-card"><div class="vitalis-deep-top"><b>' + esc(meal.name || "Repas") +
        '</b><span>' + number(meal.caloriesKcal) + ' kcal</span></div><div class="vitalis-deep-meta">' +
        'Glucides ' + number(meal.carbohydratesGrams,1) + ' g • Protéines ' + number(meal.proteinGrams,1) +
        ' g • Graisses ' + number(meal.fatGrams,1) + ' g<br>Fibre ' + number(meal.fiberGrams,1) +
        ' g • Sucre ' + number(meal.sugarGrams,1) + ' g • Sodium ' + number(meal.sodiumMilligrams) +
        ' mg<br>Source : <b>' + esc(meal.connector || "Non précisée") + '</b> • ' +
        esc(date(meal.endTime)) + '</div></div>';
    }).join("");
    return '<div class="vitalis-deep-card">' + summary + '</div><h4 class="vitalis-deep-section">Détail des repas</h4>' +
      (mealHtml || '<div class="vitalis-deep-empty">Aucun repas détaillé reçu. Utilisez le bouton + ou synchronisez une application nutrition.</div>');
  }

  function recordBody(category, title) {
    var records = ((healthData.details || {})[category] || []);
    var html = records.map(function (r) {
      var lines = [];
      Object.keys(r).forEach(function (key) {
        if (["connector","packageName","startTime","endTime","time","title","type","notes"].indexOf(key)>=0) return;
        var labels = {durationMinutes:"Durée",count:"Pas",averageBpm:"Moyenne",minimumBpm:"Minimum",maximumBpm:"Maximum",sampleCount:"Mesures",litres:"Volume",kilometres:"Distance",kilocalories:"Calories",percentage:"Valeur",kilograms:"Poids"};
        var units = {durationMinutes:" min",count:"",averageBpm:" bpm",minimumBpm:" bpm",maximumBpm:" bpm",sampleCount:"",litres:" L",kilometres:" km",kilocalories:" kcal",percentage:" %",kilograms:" kg"};
        lines.push((labels[key] || key) + " : " + number(r[key],key==="litres"||key==="kilometres"||key==="kilograms"?2:0) + (units[key] || ""));
      });
      return '<div class="vitalis-deep-card"><div class="vitalis-deep-top"><b>' +
        esc(r.title || r.type || title) + '</b><span>' + esc(date(r.endTime || r.time)) +
        '</span></div><div class="vitalis-deep-meta">' + esc(lines.join(" • ")) +
        (r.notes ? "<br>Notes : " + esc(r.notes) : "") + '<br>Source : <b>' +
        esc(r.connector || "Non précisée") + '</b></div></div>';
    }).join("");
    return html || '<div class="vitalis-deep-empty">Aucun détail reçu pour cette catégorie. Actualisez les données ou vérifiez les autorisations Health Connect.</div>';
  }

  function showCategory(category) {
    ensureData(function () {
      var map = {
        activity:["Détail des activités","activity"],
        nutrition:["Détail nutritionnel","nutrition"],
        sleep:["Détail du sommeil","sleep"],
        hydration:["Détail de l’hydratation","hydration"],
        recovery:["Fréquence cardiaque","heartRate"],
        steps:["Détail des pas","steps"],
        distance:["Détail des distances","distance"],
        activeCalories:["Calories actives","activeCalories"],
        oxygen:["Oxygène sanguin","oxygen"],
        weight:["Historique du poids","weight"]
      };
      var item = map[category] || [category,category];
      panel(item[0], category === "nutrition" ? nutritionBody() : recordBody(item[1],item[0]));
    });
  }

  function contextText(target) {
    var node = target, best = "";
    for (var i=0; node && i<7; i++,node=node.parentElement) {
      var text = norm(node.innerText || node.textContent);
      if (text.length>best.length && text.length<1800) best=text;
      if (node.matches && node.matches("article,section,[role='button'],button,a")) break;
    }
    return best;
  }

  document.addEventListener("click", function (event) {
    var target = event.target;
    if (!target || !target.closest) return;
    if (target.closest(".vitalis-deep-overlay")) return;
    var direct = norm(target.innerText || target.textContent || target.getAttribute && target.getAttribute("aria-label"));
    if (direct === "+" || /ajouter|enregistrer|scanner/.test(direct)) return;
    var context = contextText(target);
    if (/comprendre mon score|understand my score/.test(direct + " " + context)) {
      event.preventDefault(); event.stopImmediatePropagation(); showScore(); return;
    }
    var category = null;
    if (/score nutritionnel|glucides.*proteines|nutrition.*fibre/.test(context)) category="nutrition";
    else if (/activite/.test(context) && /pas|min|kcal|score|duree/.test(context)) category="activity";
    else if (/sommeil/.test(context) && /h|min|score|heure/.test(context)) category="sleep";
    else if (/(eau|hydratation)/.test(context) && /(ml|litre| l |score)/.test(" "+context+" ")) category="hydration";
    else if (/frequence cardiaque|bpm|recuperation/.test(context)) category="recovery";
    else if (/oxygene|spo2/.test(context)) category="oxygen";
    else if (/poids/.test(context) && /kg/.test(context)) category="weight";
    if (category) {
      event.preventDefault(); event.stopImmediatePropagation(); showCategory(category);
    }
  }, true);

  window.addEventListener("vitalis-health-data", function (event) {
    healthData = event.detail || {};
    if (pendingPanel) {
      var next = pendingPanel; pendingPanel = null;
      setTimeout(next,50);
    }
  });

  window.VitalisDeepDetails = {
    explainScore: showScore,
    open: showCategory,
    refresh: function () { if (bridge && bridge.refreshHealthData) bridge.refreshHealthData(); }
  };
})();