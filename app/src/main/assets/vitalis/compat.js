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