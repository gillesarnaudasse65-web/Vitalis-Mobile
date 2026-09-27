(function () {
  if (window.__vitalisPowerLayer312) return;
  window.__vitalisPowerLayer312 = true;

  var bridge = window.VitalisAndroid || null;
  var ASSET_BASE = location.hostname === "vitalis-health-os.gillesarnaudasse65.chatgpt.site"
    ? location.origin + "/__vitalis/coaches/"
    : "https://appassets.androidplatform.net/assets/vitalis/coaches/";
  var SELECTED_COACH_KEY = "vitalis-selected-coach-v312";
  var selectedCoachId = localStorage.getItem(SELECTED_COACH_KEY) || "general";
  var voiceOn = true;
  var pending = {};
  var conversations = {};
  var activeNutritionScan = null;
  var connectorState = readJsonBridge("getConnectorStatus", {connectors:[], connectorCount:0});
  var recoveredNutritionScan = readJsonBridge("getPendingNutritionScan", null);
  if (recoveredNutritionScan && recoveredNutritionScan.scanId) {
    activeNutritionScan = {
      scanId:recoveredNutritionScan.scanId,
      capturedSelectedDate:recoveredNutritionScan.selectedDate,
      source:recoveredNutritionScan.source,
      recovered:true
    };
  }

  var coaches = [
    {
      id:"general", name:"Kofi", role:"Coach santé global", image:"kofi.webp",
      intro:"Je relie activité, sommeil, nutrition, hydratation et récupération pour définir vos priorités.",
      prompt:"Fais mon bilan santé global, explique les données manquantes et donne trois priorités personnalisées."
    },
    {
      id:"nutrition", name:"Ama", role:"Coach nutrition", image:"ama.webp",
      intro:"J’analyse les repas, calories, protéines, glucides, lipides, fibres et habitudes alimentaires.",
      prompt:"Analyse ma nutrition, mes macronutriments et mes repas. Cite les sources et donne trois améliorations."
    },
    {
      id:"activity", name:"Ayo", role:"Coach activité", image:"ayo.webp",
      intro:"Je transforme vos pas et séances en programme sportif réaliste, progressif et mesurable.",
      prompt:"Analyse mes pas et mes activités. Propose un programme réaliste pour aujourd’hui avec récupération."
    },
    {
      id:"sleep", name:"Nia", role:"Coach sommeil", image:"nia.webp",
      intro:"J’accompagne la régularité, la durée du sommeil et la qualité de la récupération nocturne.",
      prompt:"Analyse mon sommeil, indique ce qui manque et propose trois actions concrètes pour mieux récupérer."
    },
    {
      id:"recovery", name:"Sékou", role:"Coach récupération", image:"sekou.webp",
      intro:"Je surveille récupération, fréquence cardiaque, oxygène, hydratation et charge d’activité.",
      prompt:"Analyse ma récupération et ma charge sans diagnostic. Donne trois conseils prudents et cite les sources."
    },
    {
      id:"mental", name:"Zuri", role:"Coach bien-être mental", image:"zuri.webp",
      intro:"Je vous aide à structurer stress, respiration, habitudes et équilibre quotidien.",
      prompt:"Analyse les facteurs de stress visibles et propose une routine courte de respiration et d’équilibre."
    }
  ];

  function norm(value) {
    return String(value || "").toLowerCase().normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim();
  }

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char];
    });
  }

  function readJsonBridge(method, fallback) {
    try {
      if (!bridge || !bridge[method]) return fallback;
      return JSON.parse(bridge[method]());
    } catch (_) {
      return fallback;
    }
  }

  function coachById(id) {
    return coaches.filter(function (coach) { return coach.id === id; })[0] || coaches[0];
  }

  function imageUrl(coach) {
    return ASSET_BASE + coach.image;
  }

  function selectedDate() {
    if (window.VitalisDate) return window.VitalisDate.get();
    var nativeData = readJsonBridge("getLastHealthData", {});
    if (nativeData.selectedDate) return nativeData.selectedDate;
    var input = document.querySelector("input[type='date']");
    return input && input.value ? input.value : new Date().toISOString().slice(0,10);
  }

  function installStyles() {
    if (document.getElementById("vitalis-power-layer-312-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-power-layer-312-style";
    style.textContent =
      ".vitalis-portrait-312{width:62px;height:62px;border-radius:18px;object-fit:cover;object-position:center 30%;background:#e5ece8;box-shadow:0 6px 18px rgba(6,60,48,.16)}" +
      ".vitalis-coach-grid-312{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;margin:12px 0}" +
      ".vitalis-coach-card-312{position:relative;border:1px solid #dbe5e0;border-radius:18px;background:#fff;padding:12px;text-align:left;color:#14342b}" +
      ".vitalis-coach-card-312.selected{border:2px solid #087052;padding:11px}.vitalis-coach-card-312 .vitalis-portrait-312{width:100%;height:auto;aspect-ratio:1/1;border-radius:14px;box-shadow:none}" +
      ".vitalis-coach-card-312 strong{display:block;font-size:15px;margin-top:8px}.vitalis-coach-card-312 small{display:block;color:#64766f;line-height:1.35;margin-top:2px}" +
      ".vitalis-selected-badge-312{position:absolute;right:18px;top:18px;background:#087052;color:#fff;border-radius:999px;padding:5px 8px;font-size:10px;font-weight:800}" +
      ".vitalis-developer-card-312{display:flex;align-items:center;gap:12px;border:1px solid #cfdcd6;border-radius:18px;padding:13px;background:linear-gradient(135deg,#f0f7f4,#fff);width:100%;text-align:left;color:#14342b}" +
      ".vitalis-developer-icon-312{width:54px;height:54px;border-radius:17px;background:#063c30;color:#fff;display:grid;place-items:center;font-size:24px}" +
      ".vitalis-ai-avatar-312{width:48px;height:48px;border-radius:15px;object-fit:cover;object-position:center 30%}" +
      ".vitalis-agent-chip-312{display:inline-block;border-radius:999px;background:#e2f3e9;color:#063c30;padding:5px 8px;font-size:10px;font-weight:800;margin-top:4px}" +
      ".vitalis-developer-response-312{white-space:pre-wrap;background:#fff;border:1px solid #e0e7e3;border-radius:16px;padding:13px;line-height:1.5;font-size:13px;margin:10px 0}" +
      ".vitalis-meal-grid-312{display:grid;grid-template-columns:1fr 1fr;gap:9px}.vitalis-meal-grid-312 label{font-size:11px;color:#687970}.vitalis-meal-grid-312 input{width:100%;box-sizing:border-box;margin-top:4px;border:1px solid #cad7d1;border-radius:11px;padding:10px;background:#fff}" +
      ".vitalis-meal-preview-312{width:100%;max-height:210px;object-fit:cover;border-radius:16px;margin:8px 0}.vitalis-meal-status-312{padding:12px;border-radius:13px;background:#edf5f1;color:#31584b;margin:9px 0}.vitalis-meal-actions-312{display:flex;gap:8px;flex-wrap:wrap;margin-top:13px}.vitalis-meal-actions-312 button{flex:1;min-width:120px;border:0;border-radius:12px;padding:11px;font-weight:750}.vitalis-meal-secondary-312{background:#e4ece8;color:#173f33}.vitalis-meal-danger-312{background:#fae2df;color:#8e231a}.vitalis-meal-list-312{display:grid;gap:10px}.vitalis-meal-card-312{border:1px solid #dce6e1;background:#fff;border-radius:16px;padding:13px}.vitalis-meal-card-312 small{color:#687970}.vitalis-meal-warning-312{background:#fff2cd;color:#6c5300;padding:10px;border-radius:11px;margin:8px 0}.vitalis-native-field textarea{width:100%;box-sizing:border-box;border:1px solid #cad7d1;border-radius:11px;padding:10px;background:#fff}" +
      ".vitalis-connector-card-312{background:#fff;border:1px solid #dce5e1;border-radius:16px;padding:13px;margin:9px 0}.vitalis-connector-top-312{display:flex;justify-content:space-between;gap:10px;align-items:center}" +
      ".vitalis-connector-card-312 small{display:block;color:#677a72;line-height:1.4;margin:6px 0}.vitalis-connector-action-312{border:0;border-radius:999px;padding:8px 11px;background:#063c30;color:#fff;font-size:11px;font-weight:800}" +
      ".vitalis-connector-action-312[disabled]{background:#dfe5e2;color:#708078}" +
      ".vitalis-status-312{border-radius:999px;padding:5px 8px;font-size:10px;font-weight:800;background:#edf1ef;color:#52645d}.vitalis-status-312.connected{background:#dff4e7;color:#075f45}.vitalis-status-312.installed{background:#fff1c9;color:#775b00}";
    document.head.appendChild(style);
  }

  function overlay(titleHtml, bodyHtml, className) {
    installStyles();
    var old = document.querySelector(".vitalis-power-overlay-312");
    if (old) { stopOverlayVoice(); old.remove(); }
    var root = document.createElement("div");
    root.className = "vitalis-native-overlay vitalis-power-overlay-312 " + (className || "");
    root.innerHTML = '<div class="vitalis-native-sheet"><div class="vitalis-native-head">' +
      titleHtml + '<button class="vitalis-native-close" aria-label="Fermer">×</button></div>' +
      bodyHtml + "</div>";
    document.body.appendChild(root);
    root.querySelector(".vitalis-native-close").onclick = function () {
      stopOverlayVoice();
      root.remove();
    };
    root.addEventListener("click", function (event) {
      if (event.target === root) { stopOverlayVoice(); root.remove(); }
    });
    return root;
  }

  function stopOverlayVoice() {
    if (bridge && bridge.stopVoiceInput) bridge.stopVoiceInput();
    if (bridge && bridge.stopSpeaking) bridge.stopSpeaking();
  }

  function setSelectedCoach(id) {
    selectedCoachId = coachById(id).id;
    localStorage.setItem(SELECTED_COACH_KEY, selectedCoachId);
    document.documentElement.setAttribute("data-vitalis-selected-coach", selectedCoachId);
    updateExistingCoachCard();
  }

  function updateExistingCoachCard() {
    var coach = coachById(selectedCoachId);
    var hosts = Array.prototype.slice.call(document.querySelectorAll(
      ".dashboard-coach-stage,.proactive-brief,article.coach,[data-vitalis-coach-host]"
    ));
    Array.prototype.slice.call(document.querySelectorAll("button,a,[role='button']")).forEach(function (button) {
      var label = norm((button.innerText || button.textContent || "") + " " +
        (button.getAttribute("aria-label") || ""));
      if (/changer.*coach|change.*coach|choisir.*coach|selectionner.*coach/.test(label)) {
        button.style.removeProperty("display");
        var host = button.closest("article,section") || button.parentElement;
        if (host && hosts.indexOf(host) < 0) hosts.push(host);
      }
    });
    hosts.forEach(function (host) {
      if (!host) return;
      var stageLabel = host.querySelector(".stage-copy > span");
      if (stageLabel && /coach/.test(norm(stageLabel.textContent))) {
        var stageTitle = coach.name.toUpperCase() + " · " + coach.role.toUpperCase();
        if (stageLabel.textContent !== stageTitle) stageLabel.textContent = stageTitle;
      }
      Array.prototype.slice.call(host.querySelectorAll("h1,h2,h3,h4,b,strong")).some(function (heading) {
        if (/aina|malik|kofi|ama|ayo|nia|sekou|zuri|coach vitalis|coach sante/.test(norm(heading.textContent))) {
          var title = coach.name + " — " + coach.role;
          if (heading.textContent !== title) heading.textContent = title;
          return true;
        }
        return false;
      });
      var avatar = host.querySelector(".avatar,[class*='avatar']");
      if (avatar && !avatar.querySelector("img")) {
        avatar.textContent = "";
        avatar.style.backgroundImage = "url('" + imageUrl(coach) + "')";
        avatar.style.backgroundSize = "cover";
        avatar.style.backgroundPosition = "center 30%";
      }
      var image = host.querySelector("img");
      if (image && /coach|kofi|aina|malik|avatar/.test(norm(
        (image.alt || "") + " " + (image.className || "") + " " + (image.src || "")
      ))) {
        if (image.src !== imageUrl(coach)) image.src = imageUrl(coach);
        image.alt = coach.name + ", " + coach.role;
      }
    });
  }

  function healthAiConfigured() {
    try {
      return !!(bridge && bridge.hasOpenAiKey && bridge.hasOpenAiKey() &&
        bridge.hasAiHealthConsent && bridge.hasAiHealthConsent());
    } catch (_) { return false; }
  }

  function developerAiConfigured() {
    try { return !!(bridge && bridge.hasDeveloperAiKey && bridge.hasDeveloperAiKey()); }
    catch (_) { return false; }
  }

  function configureHealthAi(nextCoach, onConfigured) {
    var root = overlay(
      '<div><h3>Activer les coachs IA</h3><div class="vitalis-agent-chip-312">Configuration sécurisée</div></div>',
      '<div class="vitalis-ai-config"><p>Renseignez la clé dédiée « Vitalis AI ». Elle est chiffrée par Android Keystore et n’est jamais intégrée à l’APK.</p>' +
      '<input type="password" autocomplete="off" spellcheck="false" data-health-key placeholder="sk-proj-…">' +
      '<label class="vitalis-ai-check"><input type="checkbox" data-health-consent><span>J’autorise l’envoi à OpenAI des données santé nécessaires aux demandes et des photos que je choisis d’analyser.</span></label>' +
      '<button class="vitalis-ai-primary" data-health-save>Activer les coachs IA</button>' +
      (bridge && bridge.hasOpenAiKey && bridge.hasOpenAiKey() ? '<button class="vitalis-ai-secondary" data-health-clear>Effacer la clé santé locale</button>' : "") +
      '<p class="vitalis-ai-note">Les coachs accompagnent le bien-être et ne posent aucun diagnostic.</p></div>'
    );
    root.querySelector("[data-health-save]").onclick = function () {
      var key = root.querySelector("[data-health-key]").value.trim();
      if (!root.querySelector("[data-health-consent]").checked) {
        alert("Le consentement est nécessaire pour analyser les données santé.");
        return;
      }
      if (!bridge || !bridge.saveOpenAiKey || !bridge.saveOpenAiKey(key)) {
        alert("La clé semble invalide. Vérifiez-la puis réessayez.");
        return;
      }
      bridge.setAiHealthConsent(true);
      root.remove();
      if (onConfigured) onConfigured();
      else openCoach((nextCoach && nextCoach.id) || selectedCoachId);
    };
    var clear = root.querySelector("[data-health-clear]");
    if (clear) clear.onclick = function () {
      bridge.clearOpenAiKey();
      bridge.setAiHealthConsent(false);
      root.remove();
    };
  }

  function localCoachAnswer(coach, question) {
    var data = readJsonBridge("getLastHealthData", {});
    var n = data.nutrition || {};
    var source = data.attribution || {};
    function connector(key) {
      return source[key] && source[key].lastConnector ? source[key].lastConnector : "source non disponible";
    }
    function metricValue(key, formatter) {
      var metric = data.metrics && data.metrics[key];
      if (!metric || metric.status === "DATA") return formatter(data[key]);
      if (metric.status === "NO_DATA") return "aucune donnée";
      if (metric.status === "NOT_AUTHORIZED") return "non autorisé";
      if (metric.status === "UNSUPPORTED") return "non pris en charge";
      return "erreur de lecture";
    }
    var lines = [coach.name + " — analyse locale Vitalis."];
    if (coach.id === "nutrition") {
      lines.push("Repas : " + Number(n.mealCount || 0) + " • " + Math.round(Number(n.caloriesKcal || 0)) + " kcal.");
      lines.push("Glucides " + Math.round(Number(n.carbohydratesGrams || 0)) + " g • protéines " + Math.round(Number(n.proteinGrams || 0)) + " g • lipides " + Math.round(Number(n.fatGrams || 0)) + " g.");
    } else if (coach.id === "activity") {
      lines.push("Pas : " + metricValue("steps", function(v){return Math.round(Number(v));}) +
        " • activité : " + metricValue("exerciseMinutes", function(v){return Math.round(Number(v)) + " minutes";}) +
        ". Source : " + connector("exerciseMinutes") + ".");
    } else if (coach.id === "sleep") {
      lines.push("Sommeil : " + metricValue("sleepMinutes", function(v){return (Number(v) / 60).toFixed(1) + " h";}) +
        ". Source : " + connector("sleepMinutes") + ".");
    } else if (coach.id === "recovery") {
      lines.push("Fréquence cardiaque moyenne : " + metricValue("averageHeartRate", function(v){return Math.round(Number(v)) + " bpm";}) + ".");
      lines.push("Hydratation : " + metricValue("hydrationLitres", function(v){return Number(v).toFixed(2) + " L";}) + ".");
    } else if (coach.id === "mental") {
      lines.push("Aucune mesure mentale ne suffit à établir un diagnostic. Prenez deux minutes de respiration lente et notez votre niveau de tension.");
    } else {
      lines.push("Score Vitalis : " + Number(data.score || 0) + "/100.");
      lines.push("Activité " + metricValue("steps", function(v){return Math.round(Number(v)) + " pas";}) +
        " • sommeil " + metricValue("sleepMinutes", function(v){return (Number(v)/60).toFixed(1) + " h";}) +
        " • hydratation " + metricValue("hydrationLitres", function(v){return Number(v).toFixed(1) + " L";}) + ".");
    }
    lines.push(data.syncedAt ? "Données du " + (data.selectedDate || "jour sélectionné") + "." : "Actualisez les données pour affiner le conseil.");
    lines.push("Conseil informatif, sans diagnostic médical.");
    return lines.join("\n");
  }

  function renderConversation(chat, coach) {
    var items = conversations[coach.id] || [];
    if (!items.length) {
      items.push({role:"coach", text:coach.intro});
      conversations[coach.id] = items;
    }
    chat.innerHTML = items.map(function (message) {
      var role = message.role === "user" ? "user" : message.role === "error" ? "error" : "kofi";
      return '<div class="vitalis-ai-message ' + role + '">' + esc(message.text) + "</div>";
    }).join("");
    chat.scrollTop = chat.scrollHeight;
  }

  function sendCoachQuestion(coach, question, chat, button) {
    var clean = String(question || "").trim();
    if (!clean) return;
    var items = conversations[coach.id] || [];
    items.push({role:"user", text:clean});
    conversations[coach.id] = items;
    if (!healthAiConfigured()) {
      items.push({role:"coach", text:localCoachAnswer(coach, clean)});
      renderConversation(chat, coach);
      return;
    }
    items.push({role:"coach", text:"Analyse en cours…"});
    renderConversation(chat, coach);
    button.disabled = true;
    var requestId = "coach312-" + Date.now();
    pending[requestId] = {type:"coach", coach:coach, chat:chat, button:button, question:clean};
    try {
      if (bridge.askCoach) bridge.askCoach(clean, coach.id, requestId);
      else bridge.askKofi(clean, requestId);
    } catch (_) {
      items.pop();
      items.push({role:"error", text:localCoachAnswer(coach, clean)});
      button.disabled = false;
      renderConversation(chat, coach);
    }
  }

  function openCoach(coachId, initialPrompt) {
    var coach = coachById(coachId || selectedCoachId);
    setSelectedCoach(coach.id);
    var root = overlay(
      '<img class="vitalis-ai-avatar-312" src="' + imageUrl(coach) + '" alt="' + esc(coach.name) + '">' +
      '<div style="flex:1"><h3>' + esc(coach.name) + '</h3><div class="vitalis-agent-chip-312">' + esc(coach.role) + '</div></div>' +
      '<button class="vitalis-ai-icon" data-agent-voice title="Activer ou couper la voix">🔊</button>' +
      '<button class="vitalis-ai-icon" data-agent-settings title="Réglages IA">⚙</button>',
      '<div class="vitalis-ai-chat" data-agent-chat></div>' +
      '<div class="vitalis-ai-compose"><button class="vitalis-ai-icon" data-agent-mic title="Dicter">🎙️</button>' +
      '<textarea rows="2" data-agent-input placeholder="Demandez conseil à ' + esc(coach.name) + '…"></textarea>' +
      '<button class="vitalis-ai-send" data-agent-send>Envoyer</button></div>',
      "vitalis-coach-overlay-312"
    );
    var chat = root.querySelector("[data-agent-chat]");
    var input = root.querySelector("[data-agent-input]");
    var send = root.querySelector("[data-agent-send]");
    var voice = root.querySelector("[data-agent-voice]");
    var mic = root.querySelector("[data-agent-mic]");
    renderConversation(chat, coach);
    root.querySelector("[data-agent-settings]").onclick = function () { configureHealthAi(coach); };
    voice.onclick = function () {
      voiceOn = !voiceOn;
      voice.textContent = voiceOn ? "🔊" : "🔇";
      if (!voiceOn && bridge && bridge.stopSpeaking) bridge.stopSpeaking();
    };
    mic.onclick = function () {
      if (!bridge) return;
      var active = !!(bridge.isMicrophoneEnabled && bridge.isMicrophoneEnabled());
      if (active && bridge.stopVoiceInput) bridge.stopVoiceInput();
      else if (bridge.startVoiceInput) bridge.startVoiceInput();
    };
    send.onclick = function () {
      var value = input.value;
      input.value = "";
      sendCoachQuestion(coach, value, chat, send);
    };
    input.onkeydown = function (event) {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        send.click();
      }
    };
    root.__vitalisAiInput = input;
    if (initialPrompt) setTimeout(function () { sendCoachQuestion(coach, initialPrompt, chat, send); }, 80);
  }

  function configureDeveloperAi() {
    var root = overlay(
      '<div class="vitalis-developer-icon-312">⌘</div><div><h3>Vitalis Developer AI</h3><div class="vitalis-agent-chip-312">Clé séparée</div></div>',
      '<div class="vitalis-ai-config"><p>Renseignez uniquement la clé « Vitalis Developer AI ». Elle reste chiffrée sur le téléphone et n’est jamais publiée dans l’APK ou GitHub.</p>' +
      '<input type="password" autocomplete="off" spellcheck="false" data-developer-key placeholder="sk-proj-…">' +
      '<button class="vitalis-ai-primary" data-developer-save>Activer le développeur IA</button>' +
      (developerAiConfigured() ? '<button class="vitalis-ai-secondary" data-developer-clear>Effacer la clé développeur locale</button>' : "") +
      '<p class="vitalis-ai-note">L’IA prépare les demandes. Les modifications réelles sont exécutées dans ChatGPT Work avec validation et accès GitHub.</p></div>'
    );
    root.querySelector("[data-developer-save]").onclick = function () {
      var key = root.querySelector("[data-developer-key]").value.trim();
      if (!bridge || !bridge.saveDeveloperAiKey || !bridge.saveDeveloperAiKey(key)) {
        alert("La clé semble invalide. Vérifiez la clé Vitalis Developer AI.");
        return;
      }
      root.remove();
      openDeveloper();
    };
    var clear = root.querySelector("[data-developer-clear]");
    if (clear) clear.onclick = function () {
      bridge.clearDeveloperAiKey();
      root.remove();
    };
  }

  function openDeveloper() {
    var root = overlay(
      '<div class="vitalis-developer-icon-312">⌘</div><div style="flex:1"><h3>Vitalis Developer AI</h3><div class="vitalis-agent-chip-312">' +
      (developerAiConfigured() ? "IA prête" : "Configuration requise") +
      '</div></div><button class="vitalis-ai-icon" data-developer-settings title="Réglages">⚙</button>',
      '<p class="vitalis-ai-note">Décrivez la modification souhaitée. L’IA préparera une demande structurée sans prétendre avoir modifié l’application.</p>' +
      '<div class="vitalis-native-field"><label>Votre besoin</label><textarea data-developer-input rows="5" style="width:100%;box-sizing:border-box;border:1px solid #cad7d1;border-radius:13px;padding:12px" placeholder="Ex. Ajouter un nouveau connecteur sans modifier l’accueil…"></textarea></div>' +
      '<button class="vitalis-ai-primary" data-developer-analyze>Préparer la modification</button>' +
      '<div data-developer-result></div>',
      "vitalis-developer-overlay-312"
    );
    root.querySelector("[data-developer-settings]").onclick = configureDeveloperAi;
    var input = root.querySelector("[data-developer-input]");
    var button = root.querySelector("[data-developer-analyze]");
    var result = root.querySelector("[data-developer-result]");
    button.onclick = function () {
      var request = input.value.trim();
      if (!request) return;
      if (!developerAiConfigured()) {
        configureDeveloperAi();
        return;
      }
      button.disabled = true;
      button.textContent = "Analyse en cours…";
      result.innerHTML = "";
      var requestId = "developer312-" + Date.now();
      pending[requestId] = {type:"developer", request:request, result:result, button:button};
      bridge.askDeveloper(request, requestId);
    };
  }

  function showCoachCatalog() {
    var selected = coachById(selectedCoachId);
    var cards = coaches.map(function (coach) {
      return '<button type="button" class="vitalis-coach-card-312 ' + (coach.id === selected.id ? "selected" : "") +
        '" data-coach-312="' + coach.id + '" aria-label="' + esc(coach.name) + ' — ' + esc(coach.role) +
        '" aria-pressed="' + (coach.id === selected.id ? 'true' : 'false') +
        '"><img class="vitalis-portrait-312" src="' + imageUrl(coach) +
        '" alt="' + esc(coach.name) + '"><strong>' + esc(coach.name) + '</strong><small>' +
        esc(coach.role) + '</small>' + (coach.id === selected.id ? '<span class="vitalis-selected-badge-312">Actif</span>' : "") +
        "</button>";
    }).join("");
    var root = overlay(
      '<div><h3>Choisir mon coach IA</h3><div class="vitalis-agent-chip-312">6 spécialistes sélectionnables</div></div>',
      '<div class="vitalis-coach-grid-312">' + cards + '</div>' +
      '<h4 style="margin:18px 2px 9px">Développement de Vitalis</h4>' +
      '<button class="vitalis-developer-card-312" data-open-developer-312><span class="vitalis-developer-icon-312">⌘</span>' +
      '<span><strong>Vitalis Developer AI</strong><small>Prépare vos demandes de modification et les transmet à ChatGPT Work.</small></span></button>'
    );
    root.addEventListener("click", function (event) {
      var card = event.target.closest && event.target.closest("[data-coach-312]");
      if (card) {
        if (event.target.closest('[data-coach-action]')) return;
        root.remove();
        openCoach(card.getAttribute("data-coach-312"));
        return;
      }
      if (event.target.closest && event.target.closest("[data-open-developer-312]")) {
        root.remove();
        openDeveloper();
      }
    });
  }

  var nutritionFields = [
    ["caloriesKcal","Calories","kcal",20000], ["carbohydratesG","Glucides","g",2000],
    ["proteinG","Protéines","g",2000], ["fatG","Lipides","g",2000],
    ["fibreG","Fibres","g",2000], ["sugarG","Sucre","g",2000],
    ["sodiumMg","Sodium","mg",100000]
  ];

  function nutritionId(prefix) {
    var value = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() :
      Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
    return prefix + "-" + value.replace(/[^A-Za-z0-9_.:-]/g, "").slice(0, 100);
  }

  function parseNutritionReview(text) {
    try {
      var review = JSON.parse(String(text || ""));
      if (!review || typeof review !== "object" || !review.reviewable || !review.estimate) return null;
      if (!Array.isArray(review.errors) || !Array.isArray(review.warnings)) return null;
      return review;
    } catch (_) { return null; }
  }

  function numberOrNull(value, ceiling) {
    if (value === "" || value == null) return null;
    var parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 0 && parsed <= ceiling ? parsed : undefined;
  }

  function foodsFromText(value) {
    return String(value || "").split("\n").map(function (line) {
      var parts = line.split(" — ");
      return {name:(parts.shift() || "").trim(), portion:parts.join(" — ").trim() || null};
    }).filter(function (item) { return item.name; }).slice(0, 50);
  }

  function saveJournalMeal(meal) {
    try {
      var key = "vitalis-native-journal-v1";
      var entries = JSON.parse(localStorage.getItem(key) || "[]");
      entries = entries.filter(function (entry) { return entry.mealId !== meal.id; });
      entries.push({
        id:meal.id, mealId:meal.id, scanId:meal.scanId || null, type:"meal",
        title:meal.mealName || meal.name, detail:"Estimation enregistrée",
        at:meal.updatedAt || meal.createdAt, date:meal.date, source:"Vitalis Scanner",
        metadataOnly:true
      });
      localStorage.setItem(key, JSON.stringify(entries.slice(-500)));
    } catch (_) {}
  }

  function showMealEstimate(text, fallback, task) {
    var review = parseNutritionReview(text);
    if (!review) {
      var errorRoot = overlay(
        '<div><h3>Analyse non exploitable</h3><div class="vitalis-agent-chip-312">Aucune donnée enregistrée</div></div>',
        '<div class="vitalis-meal-warning-312">' + esc(fallback ? text : "La réponse reçue ne respecte pas le format nutritionnel attendu.") + '</div>' +
        '<div class="vitalis-meal-actions-312"><button class="vitalis-meal-secondary-312" data-retry-meal>Ré-analyser</button><button class="vitalis-meal-secondary-312" data-manual-meal>Correction manuelle</button></div>'
      );
      errorRoot.querySelector("[data-retry-meal]").onclick = function () {
        errorRoot.remove();
        analyzeNutritionScan(task && task.scanId);
      };
      errorRoot.querySelector("[data-manual-meal]").onclick = function () {
        errorRoot.remove();
        showMealReview({reviewable:true, savable:true, errors:[], warnings:["manual_entry"], estimate:{
          mealName:"Repas à compléter", foodItems:[], portionDescription:null, confidence:0,
          uncertaintyNotes:"Valeurs saisies manuellement."
        }}, task || activeNutritionScan);
      };
      return;
    }
    showMealReview(review, task || activeNutritionScan);
  }

  function showMealReview(review, task) {
    if (!task || !task.scanId || !review.estimate) return;
    var estimate = review.estimate;
    var confidence = numberOrNull(estimate.confidence, 1);
    if (confidence === undefined || confidence === null) confidence = 0;
    var warnings = review.warnings || [];
    var foodText = (estimate.foodItems || []).map(function (item) {
      return String(item.name || "") + (item.portion ? " — " + item.portion : "");
    }).join("\n");
    var root = overlay(
      '<div><h3>Vérifier l’estimation</h3><div class="vitalis-agent-chip-312">Estimation IA · confiance ' + Math.round(confidence * 100) + "%</div></div>",
      (task.preview ? '<img class="vitalis-meal-preview-312" src="' + esc(task.preview) + '" alt="Photo normalisée du repas">' : "") +
      (confidence < 0.35 || warnings.indexOf("low_confidence") >= 0 ? '<div class="vitalis-meal-warning-312">Confiance faible : vérifiez chaque valeur avant d’enregistrer.</div>' : "") +
      '<div class="vitalis-native-field"><label>Nom du repas</label><input data-meal-name maxlength="160" value="' + esc(estimate.mealName || "") + '"></div>' +
      '<div class="vitalis-native-field"><label>Aliments détectés (un par ligne, portion après « — »)</label><textarea data-meal-foods rows="3">' + esc(foodText) + '</textarea></div>' +
      '<div class="vitalis-native-field"><label>Portion générale</label><input data-meal-portion maxlength="240" value="' + esc(estimate.portionDescription || "") + '"></div>' +
      '<div class="vitalis-meal-grid-312">' + nutritionFields.map(function (field) {
        var value = estimate[field[0]];
        return '<label>' + field[1] + ' (' + field[2] + ')<input type="number" min="0" max="' + field[3] + '" step="0.1" data-meal-field="' +
          field[0] + '" value="' + (typeof value === "number" && Number.isFinite(value) ? esc(value) : "") + '"></label>';
      }).join("") + '</div>' +
      '<div class="vitalis-native-field"><label>Incertitudes</label><textarea data-meal-notes maxlength="2000" rows="3">' + esc(estimate.uncertaintyNotes || "") + '</textarea></div>' +
      '<div class="vitalis-meal-actions-312"><button class="vitalis-ai-primary" data-save-meal-estimate>Enregistrer</button><button class="vitalis-meal-secondary-312" data-reanalyze-meal>Ré-analyser</button><button class="vitalis-meal-secondary-312" data-cancel-meal>Annuler</button></div>' +
      '<p class="vitalis-ai-note">Valeurs estimées à partir d’une photo, pas des mesures de laboratoire. Date capturée : ' + esc(task.capturedSelectedDate) + '.</p>'
    );
    root.querySelector("[data-cancel-meal]").onclick = function () {
      if (bridge && bridge.cancelNutritionScan) bridge.cancelNutritionScan(task.scanId);
      if (activeNutritionScan && activeNutritionScan.scanId === task.scanId) activeNutritionScan = null;
      root.remove();
    };
    root.querySelector("[data-reanalyze-meal]").onclick = function () {
      root.remove();
      analyzeNutritionScan(task.scanId);
    };
    root.querySelector("[data-save-meal-estimate]").onclick = function () {
      var name = root.querySelector("[data-meal-name]").value.trim();
      if (!name) { alert("Indiquez un nom de repas."); return; }
      var invalid = false;
      var reviewed = {
        mealName:name,
        foodItems:foodsFromText(root.querySelector("[data-meal-foods]").value),
        portionDescription:root.querySelector("[data-meal-portion]").value.trim() || null,
        confidence:confidence,
        uncertaintyNotes:root.querySelector("[data-meal-notes]").value.trim() || null,
        estimated:true
      };
      nutritionFields.forEach(function (field) {
        var parsed = numberOrNull(root.querySelector('[data-meal-field="' + field[0] + '"]').value, field[3]);
        if (parsed === undefined || parsed === null) invalid = true;
        reviewed[field[0]] = parsed;
      });
      if (invalid) { alert("Toutes les valeurs nutritionnelles doivent être renseignées dans les limites indiquées."); return; }
      var button = root.querySelector("[data-save-meal-estimate]");
      button.disabled = true;
      var result;
      try { result = JSON.parse(bridge.saveNutritionMeal(task.scanId, JSON.stringify(reviewed))); }
      catch (_) { result = {ok:false,error:"bridge_error"}; }
      if (!result.ok) {
        button.disabled = false;
        alert("L’enregistrement a échoué : " + (result.error || "erreur inconnue"));
        return;
      }
      saveJournalMeal(result.meal);
      if (activeNutritionScan && activeNutritionScan.scanId === task.scanId) activeNutritionScan = null;
      root.remove();
      if (window.VitalisDate && window.VitalisDate.refresh) window.VitalisDate.refresh();
      else if (bridge.refreshHealthDataForDate) bridge.refreshHealthDataForDate(task.capturedSelectedDate);
    };
  }

  function analyzeNutritionScan(scanId) {
    var scan = activeNutritionScan;
    if (!scan || scan.scanId !== scanId || !bridge || !bridge.analyzeMealSession) return;
    var requestId = nutritionId("nutrition-request");
    scan.requestId = requestId;
    pending[requestId] = {type:"meal", scanId:scan.scanId, capturedSelectedDate:scan.capturedSelectedDate, preview:scan.preview};
    bridge.analyzeMealSession(scan.scanId, requestId);
  }

  function chooseNutritionSource() {
    var root = overlay(
      '<div><h3>Scanner un repas</h3><div class="vitalis-agent-chip-312">Sélection locale sécurisée</div></div>',
      '<p class="vitalis-ai-note">La photo est d’abord validée et normalisée sur cet appareil. Elle n’est envoyée au service IA externe qu’après consentement.</p>' +
      '<div class="vitalis-meal-actions-312"><button class="vitalis-ai-primary" data-source-gallery>Choisir une photo</button><button class="vitalis-meal-secondary-312" data-source-camera>Prendre une photo</button><button class="vitalis-meal-secondary-312" data-manage-meals>Gérer mes repas</button></div>'
    );
    root.querySelector("[data-source-gallery]").onclick = function () { root.remove(); startNutritionScan("gallery"); };
    root.querySelector("[data-source-camera]").onclick = function () { root.remove(); startNutritionScan("camera"); };
    root.querySelector("[data-manage-meals]").onclick = function () { root.remove(); openNutritionManager(); };
  }

  function startNutritionScan(source) {
    if (!source) { chooseNutritionSource(); return; }
    if (!bridge || !bridge.beginNutritionScan) { alert("Le scanner sécurisé nécessite l’application Android Vitalis."); return; }
    var capturedSelectedDate = selectedDate();
    var scan = {scanId:nutritionId("scan"), capturedSelectedDate:capturedSelectedDate, source:source};
    activeNutritionScan = scan;
    var accepted = false;
    try { accepted = bridge.beginNutritionScan(scan.scanId, capturedSelectedDate, source); } catch (_) {}
    if (!accepted) { activeNutritionScan = null; alert("Impossible de démarrer le scanner."); return; }
    overlay(
      '<div><h3>Préparation de la photo</h3><div class="vitalis-agent-chip-312">' + esc(capturedSelectedDate) + '</div></div>',
      '<div class="vitalis-meal-status-312" data-nutrition-status>Choisissez une photo dans l’écran Android…</div><p class="vitalis-ai-note">Aucune donnée nutritionnelle n’est enregistrée avant votre validation.</p>'
    );
  }

  function editNutritionMeal(meal) {
    var task = {scanId:meal.scanId || meal.id, capturedSelectedDate:meal.date};
    var review = {reviewable:true,savable:true,errors:[],warnings:[],estimate:{
      mealName:meal.mealName, foodItems:meal.foodItems || [], portionDescription:null,
      caloriesKcal:meal.caloriesKcal, carbohydratesG:meal.carbohydratesG,
      proteinG:meal.proteinG, fatG:meal.fatG, fibreG:meal.fibreG,
      sugarG:meal.sugarG, sodiumMg:meal.sodiumMg, confidence:meal.confidence,
      uncertaintyNotes:meal.notes
    }};
    showMealReview(review, task);
    var save = document.querySelector("[data-save-meal-estimate]");
    if (save) save.onclick = function () {
      var root = save.closest(".vitalis-power-overlay-312");
      var updated = Object.assign({}, meal, {
        mealName:root.querySelector("[data-meal-name]").value.trim(),
        name:root.querySelector("[data-meal-name]").value.trim(),
        foodItems:foodsFromText(root.querySelector("[data-meal-foods]").value),
        notes:root.querySelector("[data-meal-notes]").value.trim() || null
      });
      var invalid = false;
      nutritionFields.forEach(function (field) {
        var value = numberOrNull(root.querySelector('[data-meal-field="' + field[0] + '"]').value, field[3]);
        if (value === null || value === undefined) invalid = true;
        updated[field[0]] = value;
      });
      if (!updated.mealName || invalid) { alert("Corrigez les champs invalides."); return; }
      var result = JSON.parse(bridge.updateLocalNutritionMeal(JSON.stringify(updated)));
      if (!result.ok) { alert("Modification impossible."); return; }
      root.remove(); openNutritionManager();
    };
  }

  function openNutritionManager() {
    var payload = {ok:false,meals:[]};
    try { payload = JSON.parse(bridge.getLocalNutritionMeals("")); } catch (_) {}
    var meals = payload.meals || [];
    var cards = meals.map(function (meal) {
      return '<div class="vitalis-meal-card-312" data-meal-card="' + esc(meal.id) + '"><b>' + esc(meal.mealName) + '</b><small>' + esc(meal.date) + ' · ' + esc(meal.caloriesKcal) + ' kcal</small><div class="vitalis-meal-actions-312"><button class="vitalis-meal-secondary-312" data-edit-meal="' + esc(meal.id) + '">Modifier</button><button class="vitalis-meal-danger-312" data-delete-meal="' + esc(meal.id) + '">Supprimer</button></div></div>';
    }).join("");
    var root = overlay(
      '<div><h3>Mes repas Vitalis</h3><div class="vitalis-agent-chip-312">Stockage local structuré</div></div>',
      '<div class="vitalis-meal-list-312">' + (cards || '<div class="vitalis-meal-status-312">Aucun repas Vitalis enregistré.</div>') + '</div>' +
      '<div class="vitalis-meal-actions-312"><button class="vitalis-meal-secondary-312" data-export-meals>Exporter en JSON</button><button class="vitalis-meal-danger-312" data-delete-all-meals>Supprimer toutes les données nutrition Vitalis</button></div>'
    );
    root.addEventListener("click", function (event) {
      var edit = event.target.closest && event.target.closest("[data-edit-meal]");
      var remove = event.target.closest && event.target.closest("[data-delete-meal]");
      if (edit) { var found = meals.filter(function (m) { return m.id === edit.getAttribute("data-edit-meal"); })[0]; root.remove(); if (found) editNutritionMeal(found); }
      if (remove && confirm("Supprimer uniquement ce repas Vitalis ?")) {
        var result = JSON.parse(bridge.deleteLocalNutritionMeal(remove.getAttribute("data-delete-meal")));
        if (result.ok && result.deleted) { root.remove(); openNutritionManager(); }
      }
    });
    root.querySelector("[data-export-meals]").onclick = function () { bridge.exportLocalNutrition(); };
    root.querySelector("[data-delete-all-meals]").onclick = function () {
      if (!confirm("Supprimer tous les repas locaux Vitalis ? Les données Health Connect et les clés IA ne seront pas supprimées.")) return;
      if (bridge.deleteAllLocalNutritionData()) { root.remove(); openNutritionManager(); }
    };
  }

  function analyzeMeal() { startNutritionScan(); }

  window.addEventListener("vitalis-nutrition-image-ready", function (event) {
    var detail = event.detail || {};
    if (!activeNutritionScan || detail.scanId !== activeNutritionScan.scanId) return;
    activeNutritionScan.preview = detail.preview || null;
    activeNutritionScan.metadata = detail.metadata || null;
    var status = document.querySelector("[data-nutrition-status]");
    if (status) status.textContent = "Photo validée et normalisée. Analyse en cours…";
    if (!healthAiConfigured() && activeNutritionScan.source !== "synthetic_test") {
      var scan = activeNutritionScan;
      var root = overlay(
        '<div><h3>Consentement requis</h3><div class="vitalis-agent-chip-312">Aucun envoi effectué</div></div>',
        '<img class="vitalis-meal-preview-312" src="' + esc(scan.preview) + '" alt="Photo normalisée du repas">' +
        '<p class="vitalis-ai-note">La sélection et la normalisation sont locales. L’analyse enverra cette photo au service IA externe OpenAI.</p>' +
        '<div class="vitalis-meal-actions-312"><button class="vitalis-ai-primary" data-configure-nutrition>Configurer et consentir</button><button class="vitalis-meal-secondary-312" data-manual-nutrition>Correction manuelle</button><button class="vitalis-meal-secondary-312" data-cancel-nutrition>Annuler</button></div>'
      );
      root.querySelector("[data-configure-nutrition]").onclick = function () {
        configureHealthAi(coachById("nutrition"), function () { analyzeNutritionScan(scan.scanId); });
      };
      root.querySelector("[data-manual-nutrition]").onclick = function () {
        root.remove();
        showMealReview({reviewable:true,savable:true,errors:[],warnings:["manual_entry"],estimate:{mealName:"",foodItems:[],portionDescription:null,confidence:0,uncertaintyNotes:"Saisie manuelle."}}, scan);
      };
      root.querySelector("[data-cancel-nutrition]").onclick = function () {
        bridge.cancelNutritionScan(scan.scanId); activeNutritionScan = null; root.remove();
      };
      return;
    }
    analyzeNutritionScan(detail.scanId);
  });

  window.addEventListener("vitalis-nutrition-scan-state", function (event) {
    var detail = event.detail || {};
    if (!activeNutritionScan || detail.scanId !== activeNutritionScan.scanId) return;
    var status = document.querySelector("[data-nutrition-status]");
    if (status) status.textContent = {
      selecting_image:"Sélection de la photo…", normalizing:"Validation et normalisation locales…",
      ready_for_analysis:"Photo prête pour l’analyse.", analyzing:"Analyse nutritionnelle externe en cours…",
      review:"Estimation prête à vérifier.", saving:"Enregistrement local…",
      saved:"Repas enregistré.", cancelled:"Scan annulé.", error:"Le scan a échoué. Vous pouvez réessayer."
    }[detail.status] || "Préparation du scan…";
    if (detail.status === "cancelled" || detail.status === "error") {
      if (detail.status === "cancelled") activeNutritionScan = null;
    }
  });

  window.addEventListener("vitalis-nutrition-local-cleared", function () {
    activeNutritionScan = null;
    try {
      var key = "vitalis-native-journal-v1";
      var entries = JSON.parse(localStorage.getItem(key) || "[]");
      localStorage.setItem(key, JSON.stringify(entries.filter(function (entry) {
        return !(entry.type === "meal" && (entry.mealId || entry.source === "Vitalis Scanner"));
      })));
      var offlineKey = "vitalis-offline-v1";
      var offline = JSON.parse(localStorage.getItem(offlineKey) || "null");
      if (offline && Array.isArray(offline.entries)) {
        offline.entries = offline.entries.filter(function (entry) {
          return !(entry.type === "meal" && (entry.mealId || entry.source === "Vitalis Scanner"));
        });
        localStorage.setItem(offlineKey, JSON.stringify(offline));
      }
    } catch (_) {}
  });

  function statusLabel(status) {
    return {
      health_connect_data_available:"Données détectées via Health Connect",
      health_connect_available_no_data:"Accès Health Connect activé, aucune donnée",
      health_connect_permission_required:"Autorisation Health Connect requise",
      setup_required:"Configuration requise", api_unavailable:"Connexion directe non implémentée",
      unsupported:"Non pris en charge sur Android", unavailable:"Indisponible",
      not_installed:"Non installé", installed:"Installé"
    }[status] || "À configurer";
  }

  function actionLabel(item) {
    if (item.status === "health_connect_data_available") return "Gérer";
    if (item.action === "authorize_health_connect") return "Autoriser";
    if (item.action === "authorize_via_health_connect") return "Autoriser et ouvrir";
    if (item.action === "open_provider") return "Ouvrir l’application";
    if (item.action === "unsupported_android") return "Non disponible sur Android";
    return "Installer";
  }

  function showConnectors() {
    connectorState = readJsonBridge("getConnectorStatus", connectorState);
    var items = connectorState.connectors || [];
    var cards = items.map(function (item) {
      return '<div class="vitalis-connector-card-312"><div class="vitalis-connector-top-312"><b>' +
        esc(item.name || item.packageName) + '</b><span class="vitalis-status-312 ' + esc(item.status) + '">' +
        esc(item.userFacingStatus || statusLabel(item.status)) + '</span></div><small>' + esc(item.note || "Source détectée automatiquement.") +
        '</small><button class="vitalis-connector-action-312" data-connector-312="' + esc(item.id || item.packageName) +
        '" ' + (item.action === "unsupported_android" ? "disabled" : "") + ">" +
        esc(actionLabel(item)) + "</button></div>";
    }).join("");
    var root = overlay(
      '<div><h3>Connecteurs et autorisations</h3><div class="vitalis-agent-chip-312">' +
      Number(connectorState.connectorCount || 0) + " source(s) avec données</div></div>",
      '<p class="vitalis-ai-note">Une application installée ou ouverte n’est pas automatiquement connectée. Pour Health Connect, activez le partage chez le fournisseur puis revenez dans Vitalis. Connexion directe non implémentée signifie qu’aucun OAuth/API fournisseur n’est actif.</p>' +
      (cards || '<div class="vitalis-deep-empty">Aucun connecteur disponible.</div>')
    );
    root.addEventListener("click", function (event) {
      var button = event.target.closest && event.target.closest("[data-connector-312]");
      if (!button || !bridge || !bridge.authorizeConnector) return;
      bridge.authorizeConnector(button.getAttribute("data-connector-312"));
    });
  }

  window.addEventListener("vitalis-ai-response", function (event) {
    var detail = event.detail || {};
    var task = pending[detail.requestId];
    if (!task) return;
    delete pending[detail.requestId];
    if (task.type === "meal") {
      if (!activeNutritionScan || task.scanId !== activeNutritionScan.scanId ||
          task.capturedSelectedDate !== activeNutritionScan.capturedSelectedDate) return;
      showMealEstimate(detail.ok ? detail.text : (detail.error || "Analyse indisponible."), !detail.ok, task);
      return;
    }
    if (task.type === "coach") {
      var items = conversations[task.coach.id] || [];
      items.pop();
      var answer = detail.ok ? detail.text : localCoachAnswer(task.coach, task.question);
      items.push({role:detail.ok ? "coach" : "error", text:answer});
      task.button.disabled = false;
      renderConversation(task.chat, task.coach);
      if (voiceOn && detail.ok && bridge && bridge.speakText) bridge.speakText(answer, "fr-FR");
      return;
    }
    if (task.type === "developer") {
      var answerText = detail.ok ? detail.text : (detail.error || "Le développeur IA est indisponible.");
      task.result.innerHTML = '<div class="vitalis-developer-response-312">' + esc(answerText) + '</div>' +
        '<button class="vitalis-ai-primary" data-send-work-312>Continuer dans ChatGPT Work</button>' +
        '<p class="vitalis-ai-note">La demande sera copiée puis ChatGPT Work sera ouvert. Vous gardez la validation finale des changements.</p>';
      task.button.disabled = false;
      task.button.textContent = "Préparer la modification";
      task.result.querySelector("[data-send-work-312]").onclick = function () {
        var handoff = "Projet : gillesarnaudasse65-web/Vitalis-Mobile\n" +
          "Contrainte : préserver l’interface Vitalis actuelle.\n\nDemande utilisateur :\n" + task.request +
          "\n\nProposition de Vitalis Developer AI :\n" + answerText +
          "\n\nVérifie le dépôt réel, implémente uniquement après validation, compile et contrôle GitHub Actions.";
        if (bridge && bridge.sendDeveloperRequestToChatGpt) bridge.sendDeveloperRequestToChatGpt(handoff);
      };
    }
  });

  window.addEventListener("vitalis-connectors", function (event) {
    connectorState = event.detail || connectorState;
  });

  window.addEventListener("vitalis-voice-input", function (event) {
    var detail = event.detail || {};
    if (detail.kind !== "FINAL" || !detail.text) return;
    var root = document.querySelector(".vitalis-coach-overlay-312");
    var input = root && root.__vitalisAiInput;
    if (input) {
      input.value = detail.text;
      input.focus();
    }
    if (bridge && bridge.stopVoiceInput) bridge.stopVoiceInput();
  });

  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest
      ? event.target.closest("button,a,[role='button']")
      : null;
    if (!target || target.closest(".vitalis-power-overlay-312")) return;
    var label = norm((target.innerText || target.textContent || "") + " " +
      (target.getAttribute("aria-label") || "") + " " + (target.getAttribute("title") || ""));
    if (/changer.*coach|change.*coach|choisir.*coach|selectionner.*coach|tous.*coach|mes.*coach|equipe.*coach/.test(label)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      showCoachCatalog();
    } else if (/parler.*coach|parler.*(kofi|ama|ayo|nia|sekou|zuri)|coach.*vitalis.*ai|ouvrir.*coach|demarrer.*direct/.test(label)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      openCoach(selectedCoachId);
    } else if (/vitalis.*developer|developpeur.*vitalis|developer.*ai/.test(label)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      openDeveloper();
    }
  }, true);

  window.VitalisAI = {
    open:function (prompt) { openCoach(selectedCoachId, prompt); },
    openFeature:function (label) {
      var value = norm(label);
      var coach = /nutrition|repas/.test(value) ? "nutrition" :
        /sommeil/.test(value) ? "sleep" :
        /activite|sport|entrainement/.test(value) ? "activity" :
        /recuper|cardiaque/.test(value) ? "recovery" :
        /stress|mental/.test(value) ? "mental" : selectedCoachId;
      openCoach(coach, coachById(coach).prompt);
    },
    configure:function () { configureHealthAi(coachById(selectedCoachId)); },
    analyzeMeal:analyzeMeal,
    openDeveloper:openDeveloper,
    setVoiceEnabled:function (enabled) {
      voiceOn = !!enabled;
      if (!voiceOn && bridge && bridge.stopSpeaking) bridge.stopSpeaking();
    }
  };

  window.VitalisNutrition = {
    startScan:startNutritionScan,
    openManager:openNutritionManager,
    active:function () { return activeNutritionScan ? Object.assign({}, activeNutritionScan) : null; }
  };

  window.VitalisCoaches = {
    all:coaches.slice(),
    selected:function () { return coachById(selectedCoachId); },
    open:showCoachCatalog,
    ask:function (coach) { openCoach(coach && coach.id || selectedCoachId, coach && coach.prompt); },
    select:function (id) { setSelectedCoach(id); openCoach(id); },
    refresh:function () {
      if (window.VitalisDate) window.VitalisDate.refresh();
      else if (bridge && bridge.refreshHealthDataForDate) bridge.refreshHealthDataForDate(selectedDate());
      else if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
    }
  };

  window.VitalisConnectorControls = Object.assign({}, window.VitalisConnectorControls || {}, {
    showSources:showConnectors,
    refresh:function () {
      if (window.VitalisDate) window.VitalisDate.refresh();
      else if (bridge && bridge.refreshHealthDataForDate) bridge.refreshHealthDataForDate(selectedDate());
      else if (bridge && bridge.refreshHealthData) bridge.refreshHealthData();
    }
  });

  setSelectedCoach(selectedCoachId);
  setTimeout(updateExistingCoachCard, 400);
  new MutationObserver(function () { setTimeout(updateExistingCoachCard, 30); })
    .observe(document.documentElement, {childList:true, subtree:true});
})();
