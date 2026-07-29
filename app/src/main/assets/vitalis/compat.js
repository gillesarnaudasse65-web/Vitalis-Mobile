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
        function complete(preview) {
          var detail = {
            entry: entry,
            fileName: file.name,
            mimeType: "image/jpeg",
            size: file.size,
            preview: preview
          };
          window.dispatchEvent(new CustomEvent("vitalis-meal-photo-selected", { detail: detail }));
          document.dispatchEvent(new CustomEvent("vitalis-meal-photo-selected", { detail: detail }));
          if (window.VitalisAI && window.VitalisAI.analyzeMeal) {
            window.VitalisAI.analyzeMeal(detail.preview, entry);
          }
          toast("Photo du repas enregistrée.");
          input.remove();
        }
        var image = new Image();
        image.onload = function () {
          var scale = Math.min(1, 1280 / Math.max(image.width, image.height));
          var canvas = document.createElement("canvas");
          canvas.width = Math.max(1, Math.round(image.width * scale));
          canvas.height = Math.max(1, Math.round(image.height * scale));
          canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
          complete(canvas.toDataURL("image/jpeg", 0.82));
        };
        image.onerror = function () { complete(reader.result); };
        image.src = reader.result;
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
    if (/actualiser.*donnee|synchroniser.*donnee|refresh.*data/.test(label)) {
      return function () {
        if (nativeBridge && nativeBridge.refreshHealthData) nativeBridge.refreshHealthData();
      };
    }
    if (/gerer.*source|voir.*source|source.*donnee/.test(label)) {
      return function () {
        if (window.VitalisConnectorControls) window.VitalisConnectorControls.showSources();
      };
    }
    if (/^nutrition$|voir.*detail.*nutrition|detail.*repas/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("nutrition");
      };
    }
    if (/^activite$|voir.*detail.*activite/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("activity");
      };
    }
    if (/^sommeil$|voir.*detail.*sommeil/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("sleep");
      };
    }
    if (/signes.*vitaux|frequence.*cardiaque|voir.*detail.*oxygene|pression.*arterielle/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("recovery");
      };
    }
    if (/voir.*detail.*pas/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("steps");
      };
    }
    if (/voir.*detail.*calorie/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("activeCalories");
      };
    }
    if (/voir.*detail.*hydratation/.test(label)) {
      return function () {
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("hydration");
      };
    }
    if (/bien-etre.*mental|gestion.*stress/.test(label)) {
      return function () {
        if (window.VitalisAI) window.VitalisAI.openFeature("gestion du stress et bien-être mental");
      };
    }
    if (/dossier.*sante/.test(label)) {
      return function () {
        if (window.VitalisAI) window.VitalisAI.openFeature("bilan et rapport santé");
      };
    }
    if (/^coach$|ouvrir.*coach|actualiser.*conseil|coach.*ia|ia.*coach|coach.*ai|ai.*coach|demander.*kofi|parler.*kofi|parler.*coach|demarrer.*direct|analyse.*ia|conseil.*ia|analyse.*sante|bilan.*sante|rapport.*sante|plan.*nutrition|nutrition.*personnalis|analyse.*sommeil|programme.*activite|programme.*sport|plan.*entrainement|analyse.*recuperation|gestion.*stress|recommandation.*personnalis|insight.*sante/.test(label)) {
      return function () {
        if (window.VitalisAI) window.VitalisAI.openFeature(label);
      };
    }
    return null;
  }

  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest
      ? event.target.closest("button,a,[role='button']")
      : null;
    if (!target) return;
    var action = actionFor(normalize(
      (target.innerText || "") + " " +
      (target.textContent || "") + " " +
      (target.getAttribute("aria-label") || "") + " " +
      (target.getAttribute("title") || "")
    ));
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
    detail: { platform: "android", version: "3.9" }
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
    var oldDock = document.getElementById("vitalis-native-control-dock");
    if (oldDock) oldDock.remove();
    document.documentElement.setAttribute("data-vitalis-native-controls", "ready");
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

(function () {
  if (window.__vitalisSelectedDayAndNutrition) return;
  window.__vitalisSelectedDayAndNutrition = true;

  var bridge = window.VitalisAndroid || null;
  var currentData = {};
  var lastRequestedDate = "";
  var renderTimer = null;
  var months = {
    janvier:1, fevrier:2, mars:3, avril:4, mai:5, juin:6,
    juillet:7, aout:8, septembre:9, octobre:10, novembre:11, decembre:12
  };

  function plain(value) {
    return String(value || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim();
  }

  function selectedDateIso() {
    var button = document.querySelector('[aria-label="Ouvrir le calendrier"]');
    var buttonText = plain(button && (button.innerText || button.textContent));
    var pageText = plain(document.body && document.body.innerText);
    var match = buttonText.match(/(\d{1,2})\s+([a-z]+)/);
    if (!match) match = pageText.match(/(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\s+(\d{1,2})\s+([a-z]+)\s+(20\d{2})/);
    if (!match) return "";
    var day = Number(match[1]);
    var month = months[match[2]];
    var yearMatch = pageText.match(/(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\s+\d{1,2}\s+[a-z]+\s+(20\d{2})/);
    var year = yearMatch ? Number(yearMatch[1]) : new Date().getFullYear();
    if (!day || !month || !year) return "";
    return year + "-" + String(month).padStart(2, "0") + "-" + String(day).padStart(2, "0");
  }

  function requestSelectedDate(force) {
    var iso = selectedDateIso();
    if (!iso || !bridge || !bridge.refreshHealthDataForDate) return;
    if (!force && iso === lastRequestedDate) return;
    lastRequestedDate = iso;
    bridge.refreshHealthDataForDate(iso);
  }

  function formatSleep(minutes) {
    var value = Math.max(0, Number(minutes || 0));
    return Math.floor(value / 60) + "h " + String(Math.round(value % 60)).padStart(2, "0");
  }

  function setMetric(selector, value) {
    var card = document.querySelector(selector);
    var strong = card && card.querySelector("strong");
    if (strong && strong.textContent !== String(value)) strong.textContent = value;
  }

  function updateVisibleMetrics() {
    var d = currentData || {};
    setMetric('[aria-label="Voir le détail : Pas"]', Number(d.steps || 0).toLocaleString("fr-FR"));
    setMetric('[aria-label="Voir le détail : Activité"]', Math.round(Number(d.exerciseMinutes || 0)));
    setMetric('[aria-label="Voir le détail : Calories"]', Math.round(Number(d.activeCalories || 0)).toLocaleString("fr-FR"));
    setMetric('[aria-label="Voir le détail : Sommeil"]', formatSleep(d.sleepMinutes));
    setMetric('[aria-label="Voir le détail : Fréquence cardiaque"]', d.averageHeartRate == null ? "—" : Math.round(d.averageHeartRate));
    setMetric('[aria-label="Voir le détail : Oxygène sanguin"]', d.oxygenPercent == null ? "—" : Number(d.oxygenPercent).toFixed(0) + "%");
    setMetric('[aria-label="Voir le détail : Hydratation"]', Number(d.hydrationLitres || 0).toFixed(2) + " L");
    setMetric('[aria-label="Voir le détail : Composition"]', d.weightKg == null ? "—" : Number(d.weightKg).toFixed(1) + " kg");
  }

  function nutritionMarkup() {
    var n = currentData.nutrition || {};
    var calories = Math.round(Number(n.caloriesKcal || 0));
    var meals = Number(n.mealCount || 0);
    var carbs = Math.round(Number(n.carbohydratesGrams || 0));
    var protein = Math.round(Number(n.proteinGrams || 0));
    var fat = Math.round(Number(n.fatGrams || 0));
    var fiber = Math.round(Number(n.fiberGrams || 0));
    return '<div class="coach-top"><span class="ai-badge">Nutrition du jour</span><span class="coach-time">' +
      meals + ' repas</span></div><h2>' + calories + ' kcal enregistrées</h2><p>Glucides ' + carbs +
      ' g · Protéines ' + protein + ' g · Lipides ' + fat + ' g · Fibres ' + fiber +
      ' g</p><div class="coach-plan"><span><strong>Source</strong><small>Health Connect · ' +
      (currentData.selectedDate || selectedDateIso() || "aujourd’hui") +
      '</small></span></div><button class="coach-action" data-vitalis-open-nutrition>Voir toute la nutrition →</button>';
  }

  function enhanceClassicInterface() {
    updateVisibleMetrics();
    var duplicateBrief = document.querySelector(".proactive-brief");
    if (duplicateBrief) duplicateBrief.style.display = "none";
    var floatingCoach = document.querySelector(".agent-fab");
    if (floatingCoach) floatingCoach.style.display = "none";
    document.querySelectorAll("button").forEach(function (button) {
      if (/changer de coach/.test(plain(button.innerText || button.textContent))) button.style.display = "none";
    });
    var nutritionCard = document.querySelector("article.coach-card");
    var signature = JSON.stringify(currentData.nutrition || {}) + "|" + (currentData.selectedDate || "");
    if (nutritionCard && nutritionCard.getAttribute("data-vitalis-nutrition-signature") !== signature) {
      nutritionCard.setAttribute("data-vitalis-nutrition-signature", signature);
      nutritionCard.innerHTML = nutritionMarkup();
      var open = nutritionCard.querySelector("[data-vitalis-open-nutrition]");
      if (open) open.onclick = function (event) {
        event.preventDefault();
        event.stopPropagation();
        if (window.VitalisDeepDetails) window.VitalisDeepDetails.open("nutrition");
      };
    }
  }

  function scheduleEnhancement() {
    clearTimeout(renderTimer);
    renderTimer = setTimeout(enhanceClassicInterface, 80);
  }

  document.addEventListener("click", function (event) {
    var button = event.target && event.target.closest ? event.target.closest("button") : null;
    if (!button) return;
    var label = plain(
      (button.getAttribute("aria-label") || "") + " " +
      (button.getAttribute("title") || "") + " " +
      (button.innerText || button.textContent || "")
    );
    var calendar = button.closest('[role="dialog"],[class*="calendar"],[class*="date-picker"]');
    if (/jour precedent|jour suivant|ouvrir le calendrier|aujourd'hui|semaine|mois/.test(label) || calendar) {
      setTimeout(function () { requestSelectedDate(true); }, 220);
      setTimeout(function () { requestSelectedDate(true); }, 850);
    }
  }, false);

  window.addEventListener("vitalis-health-data", function (event) {
    currentData = event.detail || {};
    scheduleEnhancement();
  });

  if (document.body && window.MutationObserver) {
    new MutationObserver(scheduleEnhancement).observe(document.body, {childList:true,subtree:true});
  }
  setTimeout(function () {
    requestSelectedDate(false);
    enhanceClassicInterface();
  }, 650);
})();

(function () {
  if (window.__vitalisRealAiCoach) return;
  window.__vitalisRealAiCoach = true;

  var bridge = window.VitalisAndroid || null;
  var pending = {};
  var voiceOn = true;
  var conversation = [];

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];
    });
  }

  function styles() {
    if (document.getElementById("vitalis-ai-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-ai-style";
    style.textContent =
      ".vitalis-ai-overlay{position:fixed;z-index:2147483647;inset:0;background:rgba(16,25,22,.62);display:flex;align-items:flex-end;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-ai-sheet{width:100%;max-height:93vh;overflow:auto;background:#f7f7f7;border-radius:26px 26px 0 0;padding:17px 16px 28px;color:#17221e}" +
      ".vitalis-ai-head{position:sticky;top:-17px;background:#f7f7f7;z-index:3;display:flex;align-items:center;gap:11px;padding:10px 0 13px}" +
      ".vitalis-ai-avatar{width:44px;height:44px;border-radius:50%;display:grid;place-items:center;background:#063c30;color:#fff;font-size:24px}.vitalis-ai-title{flex:1}.vitalis-ai-title b{display:block}.vitalis-ai-title small{color:#6e7974}" +
      ".vitalis-ai-icon{border:0;border-radius:50%;width:37px;height:37px;background:#e5eae7;font-size:18px}.vitalis-ai-chat{min-height:220px;max-height:52vh;overflow:auto;padding:4px 0 10px}" +
      ".vitalis-ai-message{max-width:88%;padding:11px 13px;border-radius:16px;margin:8px 0;white-space:pre-wrap;line-height:1.46;font-size:14px}.vitalis-ai-message.kofi{background:#fff;border:1px solid #e3e9e6}.vitalis-ai-message.user{background:#063c30;color:#fff;margin-left:auto}.vitalis-ai-message.error{background:#fff1f1;border:1px solid #f1caca;color:#8d2424}" +
      ".vitalis-ai-compose{position:sticky;bottom:-28px;background:#f7f7f7;padding:10px 0 0;display:grid;grid-template-columns:auto 1fr auto;gap:8px}.vitalis-ai-compose textarea{resize:none;border:1px solid #ccd8d2;border-radius:15px;padding:11px;background:#fff;font:inherit;min-height:46px}.vitalis-ai-send{border:0;border-radius:15px;background:#063c30;color:#fff;padding:0 15px;font-weight:800}.vitalis-ai-send:disabled{opacity:.5}" +
      ".vitalis-ai-config{background:#fff;border-radius:18px;padding:15px}.vitalis-ai-config h4{margin:0 0 8px}.vitalis-ai-config p{font-size:12px;color:#68746f;line-height:1.45}.vitalis-ai-config input[type=password]{width:100%;box-sizing:border-box;border:1px solid #cad6d0;border-radius:12px;padding:12px;font:inherit}.vitalis-ai-check{display:flex;gap:9px;align-items:flex-start;margin:13px 0;font-size:12px;line-height:1.4}.vitalis-ai-primary{width:100%;border:0;border-radius:13px;padding:13px;background:#063c30;color:#fff;font-weight:800}.vitalis-ai-secondary{width:100%;border:1px solid #cbd7d1;border-radius:13px;padding:11px;background:#fff;color:#18372e;font-weight:700;margin-top:9px}.vitalis-ai-note{font-size:11px;color:#74807a;line-height:1.4;margin-top:9px}";
    document.head.appendChild(style);
  }

  function configured() {
    try {
      return !!(bridge && bridge.hasOpenAiKey && bridge.hasOpenAiKey() &&
        bridge.hasAiHealthConsent && bridge.hasAiHealthConsent());
    } catch (_) { return false; }
  }

  function healthData() {
    try { return bridge && bridge.getLastHealthData ? JSON.parse(bridge.getLastHealthData()) : {}; }
    catch (_) { return {}; }
  }

  function sourceFor(data, key) {
    var item = data.attribution && data.attribution[key];
    return item && item.lastConnector ? item.lastConnector : "source non disponible";
  }

  function overlay(body) {
    styles();
    var old = document.querySelector(".vitalis-ai-overlay");
    if (old) old.remove();
    var root = document.createElement("div");
    root.className = "vitalis-ai-overlay";
    root.innerHTML = '<div class="vitalis-ai-sheet">' + body + "</div>";
    document.body.appendChild(root);
    root.addEventListener("click", function (event) {
      if (event.target === root) root.remove();
    });
    var close = root.querySelector("[data-ai-close]");
    if (close) close.onclick = function () { root.remove(); };
    return root;
  }

  function setup() {
    var root = overlay(
      '<div class="vitalis-ai-head"><div class="vitalis-ai-avatar">🧠</div><div class="vitalis-ai-title"><b>Activer Kofi IA</b><small>Configuration sécurisée</small></div><button class="vitalis-ai-icon" data-ai-close>×</button></div>' +
      '<div class="vitalis-ai-config"><h4>Clé OpenAI dédiée</h4><p>Collez la clé « Vitalis AI » créée sur OpenAI Platform. Elle sera chiffrée par Android Keystore et ne sera jamais ajoutée au code ni à GitHub.</p>' +
      '<input type="password" autocomplete="off" spellcheck="false" data-ai-key placeholder="sk-proj-…">' +
      '<label class="vitalis-ai-check"><input type="checkbox" data-ai-consent><span>J’autorise Vitalis à transmettre à OpenAI les données santé nécessaires à mes demandes et les photos de repas que je choisis d’analyser.</span></label>' +
      '<button class="vitalis-ai-primary" data-ai-save>Activer l’IA</button>' +
      (bridge && bridge.hasOpenAiKey && bridge.hasOpenAiKey() ? '<button class="vitalis-ai-secondary" data-ai-clear>Désactiver et effacer la clé</button>' : "") +
      '<p class="vitalis-ai-note">Kofi fournit des conseils de bien-être informatifs et ne remplace pas un professionnel de santé.</p></div>'
    );
    root.querySelector("[data-ai-save]").onclick = function () {
      var key = root.querySelector("[data-ai-key]").value.trim();
      var consent = root.querySelector("[data-ai-consent]").checked;
      if (!bridge || !bridge.saveOpenAiKey) {
        alert("La configuration sécurisée est disponible dans l’application Android Vitalis.");
        return;
      }
      if (!consent) {
        alert("Votre consentement est nécessaire pour utiliser l’analyse IA des données santé.");
        return;
      }
      if (!bridge.saveOpenAiKey(key)) {
        alert("La clé semble invalide. Vérifiez la clé Vitalis AI puis réessayez.");
        return;
      }
      bridge.setAiHealthConsent(true);
      root.remove();
      openCoach();
    };
    var clear = root.querySelector("[data-ai-clear]");
    if (clear) clear.onclick = function () {
      if (bridge && bridge.clearOpenAiKey) bridge.clearOpenAiKey();
      if (bridge && bridge.setAiHealthConsent) bridge.setAiHealthConsent(false);
      root.remove();
      alert("Kofi IA est désactivé et la clé locale a été effacée.");
    };
  }

  function header() {
    return '<div class="vitalis-ai-head"><div class="vitalis-ai-avatar">🧠</div><div class="vitalis-ai-title"><b>Kofi, coach IA</b><small>' +
      (configured() ? "IA en ligne • données Vitalis" : "Mode local actif • IA en ligne à configurer") +
      '</small></div>' +
      '<button class="vitalis-ai-icon" data-ai-voice title="Activer ou couper la voix">🔊</button><button class="vitalis-ai-icon" data-ai-settings title="Réglages">⚙</button><button class="vitalis-ai-icon" data-ai-close>×</button></div>';
  }

  function renderConversation(chat) {
    if (!conversation.length) {
      conversation.push({role:"kofi", text:"Bonjour. Je peux analyser votre activité, sommeil, nutrition, hydratation et récupération. Que souhaitez-vous améliorer aujourd’hui ?"});
    }
    chat.innerHTML = conversation.map(function (message) {
      return '<div class="vitalis-ai-message ' + esc(message.role) + '">' + esc(message.text) + "</div>";
    }).join("");
    chat.scrollTop = chat.scrollHeight;
  }

  function localFallback(question) {
    var data = healthData();
    var q = String(question || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    var advice = ["Analyse locale Vitalis — disponible sans connexion IA."];
    if (!data.syncedAt) {
      advice.push("Aucune synchronisation récente : appuyez sur Actualiser pour améliorer l’analyse.");
    }
    if (/nutrition|repas|glucide|proteine|calorie/.test(q)) {
      var nutrition = data.nutrition || {};
      advice.push("Nutrition : " + Number(nutrition.mealCount || 0) + " repas et " + Math.round(Number(nutrition.caloriesKcal || 0)) + " kcal enregistrés.");
      advice.push("Glucides " + Math.round(Number(nutrition.carbohydratesGrams || 0)) + " g • protéines " + Math.round(Number(nutrition.proteinGrams || 0)) + " g • lipides " + Math.round(Number(nutrition.fatGrams || 0)) + " g • fibres " + Math.round(Number(nutrition.fiberGrams || 0)) + " g.");
      advice.push("Source nutrition : " + sourceFor(data, "nutrition") + ". Priorité : complétez les repas manquants et privilégiez une assiette équilibrée.");
    } else if (/sommeil|dormir|fatigue/.test(q)) {
      var sleepHours = Number(data.sleepMinutes || 0) / 60;
      advice.push("Sommeil : " + sleepHours.toFixed(1) + " h sur les dernières 24 h. Source : " + sourceFor(data, "sleepMinutes") + ".");
      advice.push(sleepHours && sleepHours < 7 ? "Priorité : avancez progressivement l’heure du coucher et réduisez les écrans avant le sommeil." : "Priorité : conservez des horaires réguliers et observez la qualité du réveil.");
    } else if (/activite|sport|entrainement|pas|marche/.test(q)) {
      advice.push("Activité : " + Math.round(Number(data.steps || 0)) + " pas et " + Math.round(Number(data.exerciseMinutes || 0)) + " minutes. Source : " + sourceFor(data, "exerciseMinutes") + ".");
      advice.push(Number(data.exerciseMinutes || 0) < 30 ? "Priorité : ajoutez 15 à 30 minutes d’activité adaptée aujourd’hui." : "Priorité : maintenez la régularité et prévoyez une récupération adaptée.");
    } else if (/hydrat|eau/.test(q)) {
      advice.push("Hydratation : " + Number(data.hydrationLitres || 0).toFixed(2) + " L. Source : " + sourceFor(data, "hydrationLitres") + ".");
      advice.push("Priorité : répartissez l’eau sur la journée et ajustez selon la chaleur et l’activité.");
    } else if (/recuper|coeur|cardiaque|stress/.test(q)) {
      advice.push("Récupération : fréquence cardiaque moyenne " + (data.averageHeartRate == null ? "non disponible" : Math.round(data.averageHeartRate) + " bpm") + ". Source : " + sourceFor(data, "averageHeartRate") + ".");
      advice.push("Priorité : privilégiez sommeil, hydratation et récupération douce. Une valeur inhabituelle persistante doit être discutée avec un professionnel de santé.");
    } else if (/score|bilan|rapport|sante|conseil/.test(q)) {
      advice.push("Score Vitalis : " + Number(data.score || 0) + "/100.");
      advice.push("Activité : " + Math.round(Number(data.steps || 0)) + " pas • sommeil : " + (Number(data.sleepMinutes || 0)/60).toFixed(1) + " h • hydratation : " + Number(data.hydrationLitres || 0).toFixed(1) + " L.");
      advice.push("Priorité : améliorez d’abord la catégorie la moins renseignée ou la plus éloignée de son objectif.");
    } else {
      advice.push("Activité : " + Math.round(Number(data.steps || 0)) + " pas • sommeil : " + (Number(data.sleepMinutes || 0)/60).toFixed(1) + " h • hydratation : " + Number(data.hydrationLitres || 0).toFixed(1) + " L.");
      advice.push("Priorité : choisissez une action mesurable aujourd’hui, puis actualisez les données pour suivre le résultat.");
    }
    advice.push("Ces conseils sont informatifs et ne constituent pas un diagnostic médical.");
    return advice.join("\n");
  }

  function send(question, chat, sendButton) {
    var clean = String(question || "").trim();
    if (!clean) return;
    conversation.push({role:"user", text:clean});
    if (!configured()) {
      var localAnswer = localFallback(clean);
      conversation.push({role:"kofi", text:localAnswer});
      renderConversation(chat);
      if (voiceOn && bridge && bridge.speakText) bridge.speakText(localAnswer, "fr-FR");
      return;
    }
    conversation.push({role:"kofi", text:"Analyse en cours…"});
    renderConversation(chat);
    sendButton.disabled = true;
    var requestId = "coach-" + Date.now();
    pending[requestId] = {chat:chat, button:sendButton, question:clean, kind:"coach"};
    try { bridge.askKofi(clean, requestId); }
    catch (_) {
      conversation.pop();
      conversation.push({role:"error", text:localFallback(clean)});
      renderConversation(chat);
      sendButton.disabled = false;
    }
  }

  function featurePrompt(label) {
    var value = String(label || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    if (/nutrition|repas/.test(value)) return "Analyse ma nutrition, mes macronutriments et mes repas. Donne trois améliorations prioritaires et cite les sources disponibles.";
    if (/sommeil/.test(value)) return "Analyse mon sommeil récent, les données manquantes et propose un plan concret pour améliorer ma récupération.";
    if (/activite|sport|entrainement/.test(value)) return "Analyse mon activité, mes pas et mes séances, puis propose un programme réaliste pour aujourd’hui.";
    if (/recuper|stress|cardiaque/.test(value)) return "Analyse mes indicateurs de récupération et de fréquence cardiaque sans poser de diagnostic. Donne trois conseils prudents.";
    if (/score/.test(value)) return "Explique mon score Vitalis catégorie par catégorie, les sources utilisées et les actions qui peuvent l’améliorer.";
    if (/bilan|rapport|analyse.*sante|insight/.test(value)) return "Fais un bilan complet de mes données Vitalis : activité, sommeil, nutrition, hydratation et récupération. Cite les connecteurs et priorise trois actions.";
    if (/actualiser.*conseil/.test(value)) return "Actualise mon conseil du jour à partir des dernières données Vitalis et indique clairement les sources utilisées.";
    return "";
  }

  function openCoach(initialPrompt) {
    var root = overlay(
      header() +
      '<div class="vitalis-ai-chat" data-ai-chat></div>' +
      '<div class="vitalis-ai-compose"><button class="vitalis-ai-icon" data-ai-mic title="Dicter">🎙️</button><textarea rows="2" data-ai-input placeholder="Demandez conseil à Kofi…"></textarea><button class="vitalis-ai-send" data-ai-send>Envoyer</button></div>'
    );
    var chat = root.querySelector("[data-ai-chat]");
    var input = root.querySelector("[data-ai-input]");
    var sendButton = root.querySelector("[data-ai-send]");
    renderConversation(chat);
    root.querySelector("[data-ai-settings]").onclick = setup;
    var voiceButton = root.querySelector("[data-ai-voice]");
    var micButton = root.querySelector("[data-ai-mic]");
    function updateButtons() {
      var micOn = !!(bridge && bridge.isMicrophoneEnabled && bridge.isMicrophoneEnabled());
      voiceButton.textContent = voiceOn ? "🔊" : "🔇";
      voiceButton.setAttribute("aria-pressed", voiceOn ? "true" : "false");
      micButton.textContent = micOn ? "⏹" : "🎙️";
      micButton.setAttribute("aria-pressed", micOn ? "true" : "false");
      micButton.title = micOn ? "Couper le micro" : "Activer le micro";
    }
    voiceButton.onclick = function () {
      voiceOn = !voiceOn;
      if (!voiceOn && bridge && bridge.stopSpeaking) bridge.stopSpeaking();
      updateButtons();
    };
    micButton.onclick = function () {
      if (!bridge) return;
      var micOn = !!(bridge.isMicrophoneEnabled && bridge.isMicrophoneEnabled());
      if (micOn && bridge.stopVoiceInput) bridge.stopVoiceInput();
      else if (bridge.startVoiceInput) bridge.startVoiceInput();
      setTimeout(updateButtons, 120);
    };
    updateButtons();
    sendButton.onclick = function () {
      var value = input.value;
      input.value = "";
      send(value, chat, sendButton);
    };
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        sendButton.click();
      }
    });
    root.__vitalisAiInput = input;
    if (initialPrompt) setTimeout(function () { send(initialPrompt, chat, sendButton); }, 80);
  }

  function openFeature(label) {
    openCoach(featurePrompt(label));
  }

  function analyzeMeal(imageDataUrl, entry) {
    if (!configured()) {
      showMealResult(
        "La photo est enregistrée. L’analyse visuelle en ligne nécessite la clé Vitalis AI. Le reste du coach demeure opérationnel en mode local ; ouvrez les réglages IA pour activer l’estimation des aliments et macronutriments.",
        true
      );
      return;
    }
    var requestId = "meal-" + Date.now();
    pending[requestId] = {kind:"meal", entry:entry};
    try { bridge.analyzeMealImage(imageDataUrl, requestId); }
    catch (_) { showMealResult(localFallback("Analyse de ce repas"), true); }
  }

  function showMealResult(text, fallback) {
    var root = overlay(
      '<div class="vitalis-ai-head"><div class="vitalis-ai-avatar">🍽️</div><div class="vitalis-ai-title"><b>Analyse IA du repas</b><small>' +
      (fallback ? "Estimation locale" : "Kofi Vision") +
      '</small></div><button class="vitalis-ai-icon" data-ai-close>×</button></div>' +
      '<div class="vitalis-ai-message kofi" style="max-width:none">' + esc(text) + "</div>" +
      '<p class="vitalis-ai-note">Les valeurs issues d’une photo sont des estimations. Corrigez-les si nécessaire avant de les utiliser dans votre suivi.</p>'
    );
    if (voiceOn && bridge && bridge.speakText) bridge.speakText(text, "fr-FR");
    return root;
  }

  window.addEventListener("vitalis-ai-response", function (event) {
    var detail = event.detail || {};
    var task = pending[detail.requestId];
    if (!task) return;
    delete pending[detail.requestId];
    if (task.kind === "meal") {
      var mealText = detail.ok ? detail.text : localFallback("Analyse de ce repas");
      showMealResult(mealText, !detail.ok);
      window.dispatchEvent(new CustomEvent("vitalis-meal-ai-analysis", {
        detail: {entry:task.entry, text:mealText, online:!!detail.ok}
      }));
      return;
    }
    if (task.kind === "coach") {
      conversation.pop();
      var answer = detail.ok ? detail.text : localFallback(task.question);
      conversation.push({role:detail.ok ? "kofi" : "error", text:answer});
      renderConversation(task.chat);
      task.button.disabled = false;
      if (voiceOn && detail.ok && bridge && bridge.speakText) bridge.speakText(answer, "fr-FR");
    }
  });

  window.addEventListener("vitalis-voice-input", function (event) {
    var detail = event.detail || {};
    if (detail.partial || !detail.text) return;
    var root = document.querySelector(".vitalis-ai-overlay");
    var input = root && root.__vitalisAiInput;
    if (input) {
      input.value = detail.text;
      input.focus();
    }
    if (bridge && bridge.stopVoiceInput) bridge.stopVoiceInput();
  });

  window.addEventListener("vitalis-voice-state", function (event) {
    var detail = event.detail || {};
    var root = document.querySelector(".vitalis-ai-overlay");
    var mic = root && root.querySelector("[data-ai-mic]");
    if (mic) {
      mic.textContent = detail.microphoneEnabled ? "⏹" : "🎙️";
      mic.setAttribute("aria-pressed", detail.microphoneEnabled ? "true" : "false");
      mic.title = detail.microphoneEnabled ? "Couper le micro" : "Activer le micro";
    }
  });

  window.VitalisAI = {
    open: openCoach,
    openFeature: openFeature,
    configure: setup,
    analyzeMeal: analyzeMeal,
    setVoiceEnabled: function (enabled) {
      voiceOn = !!enabled;
      if (!voiceOn && bridge && bridge.stopSpeaking) bridge.stopSpeaking();
    },
    clearKey: function () {
      if (bridge && bridge.clearOpenAiKey) bridge.clearOpenAiKey();
      if (bridge && bridge.setAiHealthConsent) bridge.setAiHealthConsent(false);
    }
  };
})();


/* Vitalis 3.11 — catalogue complet des coachs et actualisation visible */
(function () {
  if (window.__vitalisCoachRefresh311) return;
  window.__vitalisCoachRefresh311 = true;

  var bridge = window.VitalisAndroid || null;
  var coaches = [
    { id: "general", icon: "🌿", name: "Kofi", role: "Coach santé global", prompt: "bilan santé global, priorités et recommandations personnalisées" },
    { id: "nutrition", icon: "🥗", name: "Ama", role: "Coach nutrition", prompt: "nutrition, repas, calories, protéines, glucides, lipides, fibres et objectifs" },
    { id: "activity", icon: "🏃", name: "Ayo", role: "Coach activité", prompt: "activité physique, séances, pas, progression et programme sportif" },
    { id: "sleep", icon: "🌙", name: "Nia", role: "Coach sommeil", prompt: "sommeil, régularité, récupération nocturne et conseils de repos" },
    { id: "recovery", icon: "❤️", name: "Sékou", role: "Coach récupération", prompt: "récupération, fréquence cardiaque, hydratation, oxygène et charge d’activité" },
    { id: "mental", icon: "🧘", name: "Zuri", role: "Coach bien-être mental", prompt: "bien-être mental, stress, habitudes, respiration et équilibre" }
  ];
  var refreshBusy = false;

  function norm(value) {
    return String(value || "").toLowerCase().normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim();
  }

  function style() {
    if (document.getElementById("vitalis-coaches-311-style")) return;
    var el = document.createElement("style");
    el.id = "vitalis-coaches-311-style";
    el.textContent =
      ".vitalis-refresh-311{display:inline-flex;align-items:center;gap:7px;border:0;border-radius:999px;padding:10px 14px;background:#063c30;color:#fff;font:700 12px system-ui;box-shadow:0 5px 16px rgba(6,60,48,.18)}" +
      ".vitalis-refresh-311[disabled]{opacity:.62}.vitalis-refresh-311-spin{display:inline-block;animation:vitalis-spin-311 .8s linear infinite}" +
      "@keyframes vitalis-spin-311{to{transform:rotate(360deg)}}" +
      ".vitalis-coach-grid-311{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:12px 0}" +
      ".vitalis-coach-card-311{border:1px solid #dbe6e0;border-radius:16px;padding:13px;background:#fff;text-align:left;color:#14342b}" +
      ".vitalis-coach-card-311 strong{display:block;margin:5px 0 2px}.vitalis-coach-card-311 small{color:#667b73;line-height:1.35}" +
      ".vitalis-coach-avatar-311{font-size:25px}.vitalis-coach-open-311{margin-top:9px;color:#087052;font-size:12px;font-weight:800}" +
      ".vitalis-sync-note-311{font:12px system-ui;color:#5d756d;margin:8px 2px}";
    document.head.appendChild(el);
  }

  function selectedDay() {
    var data = document.body && document.body.getAttribute("data-vitalis-selected-date");
    var selected = document.querySelector(
      "[data-selected-date],[aria-selected='true'][data-date],input[type='date']"
    );
    return (selected && (selected.getAttribute("data-selected-date") ||
      selected.getAttribute("data-date") || selected.value)) || data || new Date().toISOString().slice(0, 10);
  }

  function askCoach(coach) {
    var request = coach.prompt + ". Analyse les données du jour sélectionné (" + selectedDay() +
      "), indique les données manquantes et cite les sources/connecteurs disponibles.";
    if (window.VitalisAI && window.VitalisAI.openFeature) {
      window.VitalisAI.openFeature(request);
    } else if (window.VitalisNativeActions && window.VitalisNativeActions.openCoach) {
      window.VitalisNativeActions.openCoach(request);
    }
  }

  function showCoaches() {
    style();
    var old = document.querySelector(".vitalis-coaches-311-overlay");
    if (old) old.remove();
    var overlay = document.createElement("div");
    overlay.className = "vitalis-native-overlay vitalis-coaches-311-overlay";
    var cards = coaches.map(function (coach) {
      return '<button class="vitalis-coach-card-311" data-coach="' + coach.id + '">' +
        '<span class="vitalis-coach-avatar-311">' + coach.icon + '</span><strong>' + coach.name +
        '</strong><small>' + coach.role + '</small><div class="vitalis-coach-open-311">Ouvrir le coach</div></button>';
    }).join("");
    overlay.innerHTML = '<div class="vitalis-native-sheet"><div class="vitalis-native-head">' +
      '<div><h3>Tous mes coachs IA</h3><div class="vitalis-sync-note-311">Chaque coach analyse la date sélectionnée et les données autorisées.</div></div>' +
      '<button class="vitalis-native-close" aria-label="Fermer">×</button></div>' +
      '<div class="vitalis-coach-grid-311">' + cards + '</div></div>';
    document.body.appendChild(overlay);
    overlay.querySelector(".vitalis-native-close").onclick = function () { overlay.remove(); };
    overlay.addEventListener("click", function (event) {
      if (event.target === overlay) overlay.remove();
      var card = event.target.closest && event.target.closest("[data-coach]");
      if (!card) return;
      var coach = coaches.filter(function (item) { return item.id === card.getAttribute("data-coach"); })[0];
      if (coach) { overlay.remove(); askCoach(coach); }
    });
  }

  function refresh(button) {
    if (refreshBusy) return;
    refreshBusy = true;
    if (button) {
      button.disabled = true;
      button.innerHTML = '<span class="vitalis-refresh-311-spin">↻</span> Actualisation…';
    }
    try {
      var day = selectedDay();
      window.dispatchEvent(new CustomEvent("vitalis-selected-date-change", { detail: { date: day, force: true } }));
      document.dispatchEvent(new CustomEvent("vitalis-selected-date-change", { detail: { date: day, force: true } }));
      if (bridge && bridge.setSelectedDate) bridge.setSelectedDate(day);
      if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
      else if (window.VitalisNativeActions && window.VitalisNativeActions.refreshHealthData) {
        window.VitalisNativeActions.refreshHealthData();
      }
    } catch (_) {
      refreshBusy = false;
      if (button) { button.disabled = false; button.innerHTML = "↻ Actualiser les données"; }
    }
    setTimeout(function () {
      if (!refreshBusy) return;
      refreshBusy = false;
      if (button) { button.disabled = false; button.innerHTML = "↻ Actualiser les données"; }
    }, 15000);
  }

  function installButtons() {
    style();
    if (!document.getElementById("vitalis-refresh-311")) {
      var refreshButton = document.createElement("button");
      refreshButton.id = "vitalis-refresh-311";
      refreshButton.className = "vitalis-refresh-311";
      refreshButton.innerHTML = "↻ Actualiser les données";
      refreshButton.onclick = function () { refresh(refreshButton); };
      var dateArea = document.querySelector("[data-date-navigation],input[type='date']") ||
        Array.prototype.slice.call(document.querySelectorAll("h1,h2,h3,p,div")).filter(function (el) {
          var t = norm(el.textContent);
          return t === "aujourd'hui" || t === "aujourdhui" || /^\d{1,2}\s+(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre)/.test(t);
        })[0];
      var host = dateArea && (dateArea.closest("section,header") || dateArea.parentElement);
      (host || document.querySelector("main") || document.body).insertBefore(refreshButton, (host || document.querySelector("main") || document.body).firstChild);
    }
  }

  window.addEventListener("vitalis-sync-state", function (event) {
    var detail = event.detail || {};
    var button = document.getElementById("vitalis-refresh-311");
    if (detail.status === "complete" || detail.status === "error") {
      refreshBusy = false;
      if (button) {
        button.disabled = false;
        button.innerHTML = detail.status === "complete" ? "✓ Données actualisées" : "↻ Réessayer l’actualisation";
        setTimeout(function () { button.innerHTML = "↻ Actualiser les données"; }, 3000);
      }
    }
  });

  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest ? event.target.closest("button,a,[role='button']") : null;
    if (!target) return;
    var label = norm((target.innerText || "") + " " + (target.getAttribute("aria-label") || ""));
    if (/tous.*coach|mes.*coach|voir.*coach|equipe.*coach/.test(label)) {
      event.preventDefault(); event.stopImmediatePropagation(); showCoaches();
    }
  }, true);

  window.VitalisCoaches = { all: coaches.slice(), open: showCoaches, ask: askCoach, refresh: refresh };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", installButtons);
  else installButtons();
  new MutationObserver(installButtons).observe(document.documentElement, { childList: true, subtree: true });
})();
