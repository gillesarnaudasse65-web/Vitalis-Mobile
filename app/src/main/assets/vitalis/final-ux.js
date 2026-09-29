(function () {
  "use strict";
  if (/\/run(?:2|3|4|5|6)[^/]*fixture\.html$/.test(location.pathname)) return;
  if (window.__vitalisFinalUx || !window.VitalisUxCore) return;
  window.__vitalisFinalUx = true;

  var Core = window.VitalisUxCore;
  var STORE_KEY = "vitalis-offline-v1";
  var bridge = window.VitalisAndroid || null;
  var root;
  var health = readHealth();
  var settings = loadSettings();
  var media = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;
  var renderStart = performance.now ? performance.now() : Date.now();
  var lastHydrationChange = null;

  var ICONS = {
    score:"<path d='M12 3a9 9 0 1 0 9 9'/><path d='M12 12l5-5'/><circle cx='12' cy='12' r='1.2'/>",
    activity:"<path d='M13 5a2 2 0 1 0 0-4 2 2 0 0 0 4'/><path d='M9 22l2-7 2 2 2 5M6 12l3-4 4 2 3 3 3-1'/>",
    heart:"<path d='M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z'/>",
    sleep:"<path d='M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.5 8.5 0 1 0 20.5 14.2Z'/>",
    hydration:"<path d='M12 2S5 10 5 15a7 7 0 0 0 14 0c0-5-7-13-7-13Z'/>",
    nutrition:"<path d='M7 3v8M4 3v5a3 3 0 0 0 6 0V3M7 11v10M17 3v18M17 3c4 3 4 8 0 10'/>",
    recovery:"<path d='M4 13h4l2-7 4 13 2-6h4'/>",
    mental:"<path d='M9 18h6M10 22h4M8.5 14.5A7 7 0 1 1 15.5 14.5c-1 .7-1.5 1.5-1.5 2.5h-4c0-1-.5-1.8-1.5-2.5Z'/>",
    body:"<circle cx='12' cy='5' r='3'/><path d='M5 21c.7-5 3-8 7-8s6.3 3 7 8'/>",
    coach:"<path d='M4 5h16v12H8l-4 4V5Z'/><path d='M8 9h8M8 13h5'/>",
    quick:"<path d='M12 2v20M2 12h20'/>",
    sources:"<path d='M8 12a4 4 0 0 1 4-4h4M16 12a4 4 0 0 1-4 4H8'/><path d='M14 5l3 3-3 3M10 19l-3-3 3-3'/>",
    settings:"<circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z'/>",
    calendar:"<rect x='3' y='5' width='18' height='16' rx='2'/><path d='M16 3v4M8 3v4M3 10h18'/>",
    customize:"<path d='M4 6h16M4 12h16M4 18h16'/><circle cx='9' cy='6' r='2'/><circle cx='15' cy='12' r='2'/><circle cx='8' cy='18' r='2'/>",
    back:"<path d='M19 12H5M12 19l-7-7 7-7'/>",
    sync:"<path d='M20 7h-6V1M4 17h6v6'/><path d='M18.5 12a7 7 0 0 0-12-5L4 10M5.5 12a7 7 0 0 0 12 5L20 14'/>",
    plus:"<path d='M12 5v14M5 12h14'/>",
    drag:"<path d='M8 6h.01M8 12h.01M8 18h.01M16 6h.01M16 12h.01M16 18h.01'/>",
    check:"<path d='m5 12 4 4L19 6'/>",
    warning:"<path d='M12 3 2 21h20L12 3Z'/><path d='M12 9v5M12 18h.01'/>",
    offline:"<path d='m2 2 20 20M5 12a10 10 0 0 1 3-2M9.5 6.5A12 12 0 0 1 22 12M8.5 16.5A5 5 0 0 1 12 15c1 0 2 .3 2.8.8M12 20h.01'/>"
  };

  function icon(name, label) {
    return "<svg class='vux-icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'" +
      (label ? " role='img' aria-label='" + esc(label) + "'" : " aria-hidden='true'") + ">" + (ICONS[name] || ICONS.score) + "</svg>";
  }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char];
    });
  }
  function parse(value, fallback) { try { return JSON.parse(value); } catch (_) { return fallback; } }
  function readOffline() { return parse(localStorage.getItem(STORE_KEY), {}) || {}; }
  function loadSettings() { return Core.sanitize(readOffline().finalUx); }
  function saveSettings(next) {
    settings = Core.sanitize(next);
    var offline = readOffline();
    offline.finalUx = settings;
    localStorage.setItem(STORE_KEY, JSON.stringify(offline));
    setTimeout(function () {
      window.dispatchEvent(new CustomEvent("vitalis-local-state-changed"));
    }, 0);
    applyTheme();
  }
  function readHealth() {
    if (window.__vitalisFinalUxSyntheticData) return window.__vitalisFinalUxSyntheticData;
    try { return bridge && bridge.getLastHealthData ? JSON.parse(bridge.getLastHealthData()) : {}; }
    catch (_) { return {}; }
  }
  function selectedDate() {
    if (window.VitalisDate && window.VitalisDate.get) return window.VitalisDate.get();
    return health.selectedDate || new Date().toISOString().slice(0, 10);
  }
  function today() {
    try { return bridge && bridge.getTodayHealthDate ? bridge.getTodayHealthDate() : new Date().toISOString().slice(0,10); }
    catch (_) { return new Date().toISOString().slice(0,10); }
  }
  function dateLabel(value) {
    var parts = String(value).split("-").map(Number);
    var date = new Date(parts[0], parts[1] - 1, parts[2]);
    return new Intl.DateTimeFormat("fr-FR", {weekday:"short", day:"numeric", month:"short"}).format(date);
  }
  function shiftDate(delta) {
    var parts = selectedDate().split("-").map(Number);
    var date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + delta));
    var value = date.toISOString().slice(0,10);
    if (window.VitalisDate) window.VitalisDate.select(value);
    health.selectedDate = value;
    render();
  }
  function statusFor(key, value) {
    var raw = health.metrics && health.metrics[key] && health.metrics[key].status;
    if (raw === "NOT_AUTHORIZED") raw = "PERMISSION_REQUIRED";
    return Core.metric(value, raw);
  }
  function formatMetric(metric, formatter, emptyText) {
    if (metric.status === "DATA") return {value:formatter(metric.value), label:"", state:"data"};
    var text = metric.status === "NO_DATA" ? emptyText :
      metric.status === "PERMISSION_REQUIRED" ? "Permission requise" :
      metric.status === "UNSUPPORTED" ? "Non pris en charge" :
      metric.status === "LOADING" ? "Chargement…" : "Lecture impossible";
    return {value:"—", label:text, state:metric.status.toLowerCase()};
  }
  function pct(value, goal) { return Math.max(0, Math.min(100, Math.round((Number(value) || 0) / goal * 100))); }
  function spark(values, label) {
    var points = (Array.isArray(values) ? values : []).map(Number).filter(isFinite);
    if (points.length < 3) return "";
    var min = Math.min.apply(Math, points), max = Math.max.apply(Math, points), range = Math.max(1, max - min);
    var poly = points.map(function (value, index) {
      return (index * 100 / (points.length - 1)).toFixed(1) + "," + (34 - ((value - min) / range) * 27).toFixed(1);
    }).join(" ");
    return "<svg class='vux-spark' viewBox='0 0 100 38' role='img' aria-label='" + esc(label) + "'><polyline points='" + poly + "'/></svg>";
  }
  function metricCard(id, title, metric, unit, secondary, progress, chart, actionHtml) {
    return "<article class='vux-card vux-metric-card state-" + metric.state + "' data-widget='" + id + "' data-detail='" + id + "' tabindex='0' role='button' aria-label='Ouvrir le détail " + esc(title) + "'>" +
      "<div class='vux-card-top'><span class='vux-icon-box'>" + icon(id) + "</span><span class='vux-card-title'>" + esc(title) + "</span><span class='vux-chevron'>›</span></div>" +
      "<div class='vux-metric-line'><strong>" + esc(metric.value) + "</strong>" + (unit ? "<span>" + esc(unit) + "</span>" : "") + "</div>" +
      "<p class='vux-secondary'>" + esc(metric.label || secondary) + "</p>" +
      (typeof progress === "number" ? "<div class='vux-progress' role='progressbar' aria-valuenow='" + progress + "' aria-valuemin='0' aria-valuemax='100'><i style='width:" + progress + "%'></i></div>" : "") +
      (chart || "") + (actionHtml || "") + "</article>";
  }
  function scoreValue() {
    var source = document.querySelector("#healthScore,[data-health-score]");
    var value = source && Number(String(source.textContent).replace(/[^0-9.]/g, ""));
    if (!isFinite(value) && window.__vitalisFinalUxSyntheticData) value = Number(health.score);
    return isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : null;
  }
  function nutritionSummary() {
    var meals = [];
    try { meals = bridge && bridge.getLocalNutritionMeals ? JSON.parse(bridge.getLocalNutritionMeals()) : []; } catch (_) {}
    if (!Array.isArray(meals) && meals && Array.isArray(meals.meals)) meals = meals.meals;
    if (Array.isArray(health.nutrition)) meals = health.nutrition;
    return (meals || []).filter(function (meal) { return !meal.date || meal.date === selectedDate(); }).reduce(function (sum, meal) {
      ["caloriesKcal","proteinG","carbohydratesG","fatG"].forEach(function (key) { sum[key] += Number(meal[key]) || 0; });
      sum.count += 1; return sum;
    }, {caloriesKcal:0, proteinG:0, carbohydratesG:0, fatG:0, count:0});
  }
  function hydrationValue() {
    return Number(health.hydrationLitres || 0) + Number(settings.manualHydration[selectedDate()] || 0);
  }
  function widgetHtml(id) {
    var steps, heart, sleep, water, nutrition, weight, score, sourceState, coach;
    if (id === "score") {
      score = scoreValue();
      return "<article class='vux-card vux-score-card' data-widget='score' data-detail='score' tabindex='0' role='button' aria-label='Ouvrir le détail du Score Vitalis'>" +
        "<div><span class='vux-eyebrow'>VITALIS SCORE</span><h2>Votre équilibre quotidien</h2><p>Uniquement à partir des composants réellement disponibles.</p><div class='vux-component-chips'><span>Activité</span><span>Sommeil</span><span>Hydratation</span></div></div>" +
        "<div class='vux-score-ring' style='position:relative;--score:" + (score == null ? 0 : score) + "'><div><strong>" + (score == null ? "—" : score) + "</strong><small>" + (score == null ? "Indisponible" : score >= 80 ? "Très bon" : score >= 60 ? "Bon" : "À suivre") + "</small></div></div></article>";
    }
    if (id === "activity") {
      steps = formatMetric(statusFor("steps", health.steps), function (v) { return Number(v).toLocaleString("fr-FR"); }, "Aucune activité ce jour");
      return metricCard(id,"Activité",steps,"pas",health.distanceKm != null ? Number(health.distanceKm).toFixed(1) + " km • " + (health.activeCalories == null ? "—" : Math.round(health.activeCalories) + " kcal") : "Distance indisponible",pct(health.steps,8000),spark(health.history && health.history.steps,"Tendance des pas"));
    }
    if (id === "heart") {
      heart = formatMetric(statusFor("averageHeartRate", health.averageHeartRate), function (v) { return Math.round(v); }, "Aucune fréquence cardiaque");
      return metricCard(id,"Cœur",heart,"bpm",health.minHeartRate != null ? Math.round(health.minHeartRate) + "–" + Math.round(health.maxHeartRate) + " bpm" : "Min/max indisponibles",null,spark(health.history && health.history.heart,"Tendance cardiaque"));
    }
    if (id === "sleep") {
      sleep = formatMetric(statusFor("sleepMinutes", health.sleepMinutes), function (v) { return (Number(v) / 60).toFixed(1); }, "Aucun sommeil enregistré");
      return metricCard(id,"Sommeil",sleep,"h",health.sleepInterval || "Intervalle indisponible",pct((health.sleepMinutes || 0) / 60,8),spark(health.history && health.history.sleep,"Tendance sommeil"));
    }
    if (id === "hydration") {
      water = formatMetric(Core.metric(hydrationValue(),"DATA"), function (v) { return Number(v).toFixed(2); }, "Aucune hydratation");
      return metricCard(id,"Hydratation",water,"L","Objectif 2,5 L",pct(hydrationValue(),2.5),spark(health.history && health.history.hydration,"Tendance hydratation"),
        "<div class='vux-inline-actions' aria-label='Ajouter de l’eau'><button data-act='water' data-amount='.15'>+150</button><button class='primary' data-act='water' data-amount='.25'>+250 ml</button><button data-act='water' data-amount='.5'>+500</button></div>");
    }
    if (id === "nutrition") {
      nutrition = nutritionSummary();
      var nm = formatMetric(Core.metric(nutrition.caloriesKcal, nutrition.count ? "DATA" : "NO_DATA"), function (v) { return Math.round(v); }, "Aucun repas enregistré");
      return metricCard(id,"Nutrition",nm,"kcal",nutrition.count ? Math.round(nutrition.proteinG) + " g protéines • " + Math.round(nutrition.carbohydratesG) + " g glucides" : "Scannez ou ajoutez un repas",null,"",
        "<div class='vux-inline-actions'><button class='primary' data-act='scan-meal'>Scanner</button><button data-act='nutrition-manager'>Repas</button></div>");
    }
    if (id === "recovery") {
      var rv = formatMetric(statusFor("recovery", health.recovery), function (v) { return Math.round(v); }, "Données insuffisantes");
      return metricCard(id,"Récupération",rv,rv.value === "—" ? "" : "%","Sommeil, cœur et activité disponibles uniquement",null,"");
    }
    if (id === "mental") {
      var mental = formatMetric(statusFor("wellbeing", health.wellbeing), function (v) { return String(v); }, "Aucun check-in récent");
      return metricCard(id,"Bien-être mental",mental,"","Respiration et coach Zuri",null,"","<button class='vux-card-action' data-act='zuri'>Parler à Zuri</button>");
    }
    if (id === "body") {
      weight = formatMetric(statusFor("weightKg", health.weightKg), function (v) { return Number(v).toFixed(1); }, "Aucune mesure récente");
      return metricCard(id,"Corps",weight,"kg",health.bodyFatPercent != null ? Number(health.bodyFatPercent).toFixed(1) + " % masse grasse" : "Composition indisponible",null,spark(health.history && health.history.weight,"Tendance du poids"),"<button class='vux-card-action' data-act='measure'>Ajouter une mesure</button>");
    }
    if (id === "coach") {
      coach = window.VitalisCoaches && window.VitalisCoaches.selected ? window.VitalisCoaches.selected() : {id:"general",name:"Kofi",role:"Coach santé global",image:"kofi.webp"};
      var base = location.hostname.indexOf("chatgpt.site") >= 0 ? location.origin + "/__vitalis/coaches/" : "https://appassets.androidplatform.net/assets/vitalis/coaches/";
      var roster = window.VitalisCoaches && Array.isArray(window.VitalisCoaches.all) ? window.VitalisCoaches.all : [
        {id:"general",name:"Kofi",role:"Santé globale",image:"kofi.webp"},
        {id:"nutrition",name:"Ama",role:"Nutrition",image:"ama.webp"},
        {id:"activity",name:"Ayo",role:"Activité",image:"ayo.webp"},
        {id:"sleep",name:"Nia",role:"Sommeil",image:"nia.webp"},
        {id:"recovery",name:"Sékou",role:"Récupération",image:"sekou.webp"},
        {id:"mental",name:"Zuri",role:"Bien-être mental",image:"zuri.webp"}
      ];
      var rosterHtml = roster.map(function (item) {
        var active = item.id === (coach.id || "general") ? " active" : "";
        return "<button type='button' class='vux-coach-chip" + active + "' data-coach-select='" + esc(item.id) + "' aria-label='Choisir " + esc(item.name) + "'>" +
          "<img src='" + base + esc(item.image) + "' alt=''><span><b>" + esc(item.name) + "</b><small>" + esc(item.role) + "</small></span></button>";
      }).join("");
      return "<section class='vux-card vux-coach-card vux-coach-suite' data-widget='coach'>" +
        "<div class='vux-coach-feature' data-detail='coach' tabindex='0' role='button'><img class='vux-coach-hero' src='" + base + esc(coach.image || "kofi.webp") + "' alt='" + esc(coach.name || "Kofi") + "'>" +
        "<div class='vux-coach-copy'><span class='vux-eyebrow'>VOTRE ÉQUIPE VITALIS</span><h3>" + esc(coach.name || "Kofi") + "</h3><p>" + esc(coach.role || "Coach Vitalis") + "</p>" +
        "<small>Conseils adaptés aux données et à la date sélectionnée.</small><button class='vux-coach-primary' data-act='coach'>Parler à " + esc(coach.name || "Kofi") + "</button></div></div>" +
        "<div class='vux-coach-roster' aria-label='Choisir un coach'>" + rosterHtml + "</div></section>";
    }
    if (id === "quick") {
      return "<section class='vux-quick' data-widget='quick'><div class='vux-section-heading'><div><span class='vux-eyebrow'>AUJOURD’HUI</span><h2>Actions rapides</h2></div></div><div class='vux-quick-grid'>" +
        quick("scan-meal","nutrition","Scanner un repas") + quick("coach","coach","Parler au coach") + quick("water","hydration","Ajouter 250 ml"," data-amount='.25'") + quick("measure","body","Ajouter une mesure") + quick("sync","sync","Synchroniser") + "</div></section>";
    }
    if (id === "sources") {
      sourceState = String(health.syncState || health.state || "NO_DATA").toUpperCase();
      var text = sourceState === "DATA" || sourceState === "READY" ? "Prêt" : sourceState === "PARTIAL_PERMISSION" ? "Autorisations partielles" : sourceState === "NOT_AUTHORIZED" || sourceState === "PERMISSION_REQUIRED" ? "Autorisation requise" : sourceState === "ERROR" ? "Lecture impossible" : "Aucune donnée";
      return "<article class='vux-card vux-source-card state-" + sourceState.toLowerCase() + "' data-widget='sources' data-detail='sources' tabindex='0' role='button'><span class='vux-icon-box'>" + icon("sources") + "</span><div><span class='vux-card-title'>Health Connect</span><strong>" + esc(text) + "</strong><small>" + esc(lastUpdated()) + "</small></div><span class='vux-chevron'>›</span></article>";
    }
    return "";
  }
  function quick(action, iconName, label, extra) {
    return "<button class='vux-quick-action' data-act='" + action + "'" + (extra || "") + ">" + icon(iconName) + "<span>" + esc(label) + "</span></button>";
  }
  function lastUpdated() {
    if (!health.syncedAt) return navigator.onLine ? "Pas encore synchronisé" : "Données en cache";
    var value = new Intl.DateTimeFormat("fr-FR", {day:"2-digit",month:"short",hour:"2-digit",minute:"2-digit"}).format(new Date(health.syncedAt));
    return (navigator.onLine ? "Dernière synchro : " : "Cache du ") + value;
  }
  function render() {
    if (!root) return;
    health = window.__vitalisFinalUxSyntheticData || health;
    var visible = Core.visible(settings);
    root.querySelector("[data-date-label]").textContent = dateLabel(selectedDate());
    root.querySelector("[data-date-input]").value = selectedDate();
    root.querySelector("[data-offline]").classList.toggle("hidden", navigator.onLine);
    root.querySelector("[data-last-updated]").textContent = lastUpdated();
    root.querySelector("[data-widgets]").innerHTML = visible.map(widgetHtml).join("");
    root.setAttribute("data-density", settings.density);
    bindCards();
  }
  function bindCards() {
    root.querySelectorAll("[data-coach-select]").forEach(function (button) {
      button.onclick = function (event) {
        event.preventDefault();
        event.stopPropagation();
        var id = button.getAttribute("data-coach-select");
        if (window.VitalisCoaches && window.VitalisCoaches.select) {
          window.VitalisCoaches.select(id);
          setTimeout(render, 40);
        }
      };
    });
    root.querySelectorAll("[data-detail]").forEach(function (card) {
      card.onclick = function (event) {
        if (event.target.closest("button,input,select,.vux-drag-handle")) return;
        openDetail(card.getAttribute("data-detail"));
      };
      card.onkeydown = function (event) {
        if ((event.key === "Enter" || event.key === " ") && !event.target.closest("button")) {
          event.preventDefault(); openDetail(card.getAttribute("data-detail"));
        }
      };
    });
  }
  function detailRows(id) {
    var rows = [];
    function add(label, value) { if (value !== null && value !== undefined && value !== "") rows.push([label,value]); }
    if (id === "score") { add("Score actuel", scoreValue() == null ? "Indisponible" : scoreValue() + "/100"); add("Méthode", "Composants Vitalis existants uniquement"); }
    if (id === "activity") { add("Pas", statusFor("steps",health.steps).status === "DATA" ? Number(health.steps).toLocaleString("fr-FR") : "Indisponible"); add("Distance", health.distanceKm == null ? "Indisponible" : health.distanceKm + " km"); add("Calories actives", health.activeCalories == null ? "Indisponible" : Math.round(health.activeCalories) + " kcal"); }
    if (id === "heart") { add("Moyenne", health.averageHeartRate == null ? "Indisponible" : Math.round(health.averageHeartRate) + " bpm"); add("Minimum / maximum", health.minHeartRate == null ? "Indisponible" : Math.round(health.minHeartRate) + " / " + Math.round(health.maxHeartRate)); add("SpO₂", health.oxygenPercent == null ? "Indisponible" : health.oxygenPercent + " %"); add("HRV", health.hrvMs == null ? "Indisponible" : health.hrvMs + " ms"); }
    if (id === "sleep") { add("Durée totale", health.sleepMinutes == null ? "Indisponible" : (health.sleepMinutes / 60).toFixed(1) + " h"); add("Intervalle principal", health.sleepInterval || "Indisponible"); }
    if (id === "hydration") { add("Consommation", hydrationValue().toFixed(2) + " L"); add("Objectif", "2,50 L"); }
    if (id === "nutrition") { var n=nutritionSummary(); add("Calories", n.count ? Math.round(n.caloriesKcal)+" kcal" : "Aucun repas"); add("Protéines", n.count ? Math.round(n.proteinG)+" g" : "Indisponible"); add("Glucides", n.count ? Math.round(n.carbohydratesG)+" g" : "Indisponible"); add("Lipides", n.count ? Math.round(n.fatG)+" g" : "Indisponible"); }
    if (id === "body") { add("Poids", health.weightKg == null ? "Aucune mesure récente" : Number(health.weightKg).toFixed(1)+" kg"); add("Masse grasse", health.bodyFatPercent == null ? "Indisponible" : health.bodyFatPercent+" %"); }
    if (id === "recovery") { add("Indicateur", health.recovery == null ? "Données insuffisantes" : health.recovery+" %"); add("Entrées", "Sommeil, cœur et activité disponibles"); }
    if (id === "mental") { add("Dernier check-in", health.wellbeing || "Aucun check-in récent"); add("Coach", "Zuri"); }
    if (id === "sources") { add("État", health.syncState || health.state || "NO_DATA"); add("Dernière synchronisation", lastUpdated()); add("Sources", (health.sources || []).join(", ") || "Aucune source détectée"); }
    return rows;
  }
  function openDetail(id) {
    var interactionStart = performance.now ? performance.now() : Date.now();
    if (id === "coach") { if (window.VitalisCoaches) window.VitalisCoaches.open(); return; }
    if (id === "sources" && window.VitalisConnectorControls && window.VitalisConnectorControls.showSources) { window.VitalisConnectorControls.showSources(); return; }
    var definition = Core.widgets.filter(function (item) { return item.id === id; })[0] || {title:id};
    var rows = detailRows(id);
    var history = health.history && health.history[id === "activity" ? "steps" : id];
    var modal = document.createElement("div");
    modal.className = "vux-layer";
    modal.setAttribute("data-view", id + "-details");
    modal.innerHTML = "<section class='vux-detail' role='dialog' aria-modal='true' aria-label='Détail " + esc(definition.title) + "'><header><button class='vux-icon-button' data-close aria-label='Retour'>" + icon("back") + "</button><div><span class='vux-eyebrow'>" + esc(dateLabel(selectedDate())) + "</span><h1>" + esc(definition.title) + "</h1></div><button class='vux-text-button' data-today>Aujourd’hui</button></header>" +
      "<div class='vux-detail-hero'><span class='vux-icon-box'>" + icon(id) + "</span><h2>" + esc(rows[0] ? rows[0][1] : "Indisponible") + "</h2><p>" + esc(rows[0] ? rows[0][0] : "Aucune donnée") + "</p></div>" +
      (history && history.length >= 3 ? "<section class='vux-detail-block'><h3>Tendance</h3>" + spark(history,"Historique " + definition.title) + "</section>" : "<section class='vux-empty'><h3>Historique insuffisant</h3><p>Aucune tendance n’est dessinée sans données suffisantes.</p></section>") +
      "<section class='vux-detail-block'><h3>Mesures</h3><div class='vux-detail-rows'>" + rows.map(function (row) { return "<div><span>" + esc(row[0]) + "</span><strong>" + esc(row[1]) + "</strong></div>"; }).join("") + "</div></section>" +
      "<section class='vux-detail-block'><h3>Source</h3><p>" + esc((health.sources || []).join(", ") || "Vitalis local / Health Connect selon disponibilité") + "</p></section></section>";
    root.appendChild(modal);
    window.__vitalisFinalUxMetrics = window.__vitalisFinalUxMetrics || {};
    window.__vitalisFinalUxMetrics.cardInteractionMs = (performance.now ? performance.now() : Date.now()) - interactionStart;
    modal.querySelector("[data-close]").onclick = function () { modal.remove(); };
    modal.querySelector("[data-today]").onclick = function () { if (window.VitalisDate) window.VitalisDate.today(); modal.remove(); render(); };
    modal.querySelector("[data-close]").focus();
  }
  function toast(message, undo) {
    var old = root.querySelector(".vux-toast"); if (old) old.remove();
    var node = document.createElement("div"); node.className = "vux-toast";
    node.innerHTML = "<span>" + esc(message) + "</span>" + (undo ? "<button data-undo>Annuler</button>" : "");
    root.appendChild(node);
    if (undo) node.querySelector("[data-undo]").onclick = function () { undo(); node.remove(); };
    setTimeout(function () { if (node.parentNode) node.remove(); }, 4500);
  }
  function addWater(amount) {
    amount = Number(amount) || .25;
    var date = selectedDate(), previous = Number(settings.manualHydration[date] || 0);
    var next = Core.sanitize(settings); next.manualHydration[date] = Math.round((previous + amount) * 100) / 100;
    saveSettings(next);
    var journalEntryId = window.VitalisNativeActions && window.VitalisNativeActions.addWaterAmount
      ? window.VitalisNativeActions.addWaterAmount(amount, date) : null;
    lastHydrationChange = {date:date, previous:previous};
    render();
    toast("+" + Math.round(amount * 1000) + " ml ajoutés", function () {
      var reverted = Core.sanitize(settings); reverted.manualHydration[date] = previous; saveSettings(reverted); render();
      if (journalEntryId && window.VitalisNativeActions && window.VitalisNativeActions.removeJournalEntry) {
        window.VitalisNativeActions.removeJournalEntry(journalEntryId);
      }
    });
  }
  function doAction(button) {
    var action = button.getAttribute("data-act");
    if (action === "water") addWater(button.getAttribute("data-amount"));
    else if (action === "scan-meal" && window.VitalisNutrition) window.VitalisNutrition.startScan();
    else if (action === "nutrition-manager" && window.VitalisNutrition) window.VitalisNutrition.openManager();
    else if (action === "coach" && window.VitalisCoaches) window.VitalisCoaches.open();
    else if (action === "zuri" && window.VitalisCoaches) window.VitalisCoaches.select("mental");
    else if (action === "measure" && window.VitalisNativeActions) window.VitalisNativeActions.addMeasure();
    else if (action === "sync") { if (window.VitalisDate) window.VitalisDate.refresh(); toast("Synchronisation demandée"); }
  }
  function applyTheme() {
    var start = performance.now ? performance.now() : Date.now();
    var effective = settings.theme === "system" ? (media && media.matches ? "dark" : "classic") : settings.theme;
    document.documentElement.setAttribute("data-vitalis-theme", settings.theme);
    document.documentElement.setAttribute("data-vitalis-effective-theme", effective);
    document.documentElement.setAttribute("data-vitalis-accent", settings.accent);
    document.documentElement.classList.toggle("vitalis-reduced-motion", settings.reducedMotion);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = effective === "amoled" ? "#000000" : effective === "dark" ? "#101715" : effective === "ocean" ? "#075985" : "#063c30";
    window.__vitalisFinalUxMetrics = window.__vitalisFinalUxMetrics || {};
    window.__vitalisFinalUxMetrics.themeSwitchMs = (performance.now ? performance.now() : Date.now()) - start;
  }
  function openAppearance() {
    var modal = layer("appearance", "Apparence", "Thèmes, accent et mouvement");
    var body = modal.querySelector("[data-layer-body]");
    body.innerHTML = "<fieldset class='vux-theme-grid'><legend>Thème</legend>" + Core.themes.map(function (theme) {
      return "<label class='vux-choice'><input type='radio' name='theme' value='" + theme + "' " + (settings.theme === theme ? "checked" : "") + "><span class='vux-theme-preview theme-" + theme + "'></span><b>" + ({classic:"Classic",ocean:"Ocean",dark:"Dark",amoled:"AMOLED",aurora:"Aurora",system:"Système"}[theme]) + "</b></label>";
    }).join("") + "</fieldset><fieldset class='vux-accent-grid'><legend>Accent</legend>" + Core.accents.map(function (accent) {
      return "<label><input type='radio' name='accent' value='" + accent + "' " + (settings.accent === accent ? "checked" : "") + "><span class='accent-" + accent + "'></span><b>" + accent + "</b></label>";
    }).join("") + "</fieldset><label class='vux-switch'><span><b>Réduire les animations</b><small>Limite les transitions et mouvements.</small></span><input type='checkbox' data-reduced " + (settings.reducedMotion ? "checked" : "") + "></label>";
    body.onchange = function (event) {
      var next = Core.sanitize(settings);
      if (event.target.name === "theme") next.theme = event.target.value;
      if (event.target.name === "accent") next.accent = event.target.value;
      if (event.target.hasAttribute("data-reduced")) next.reducedMotion = event.target.checked;
      saveSettings(next); render();
    };
  }
  function layer(name, title, subtitle) {
    var modal = document.createElement("div"); modal.className = "vux-layer"; modal.setAttribute("data-view", name);
    modal.innerHTML = "<section class='vux-sheet' role='dialog' aria-modal='true' aria-label='" + esc(title) + "'><header><button class='vux-icon-button' data-close aria-label='Fermer'>" + icon("back") + "</button><div><h1>" + esc(title) + "</h1><p>" + esc(subtitle) + "</p></div></header><div data-layer-body></div></section>";
    root.appendChild(modal); modal.querySelector("[data-close]").onclick = function () { modal.remove(); render(); }; modal.querySelector("[data-close]").focus(); return modal;
  }
  function openCustomize() {
    var modal = layer("customize", "Personnaliser le tableau", "Affichage, ordre, densité et préréglages");
    var body = modal.querySelector("[data-layer-body]");
    body.innerHTML = "<div class='vux-presets'>" + Object.keys(Core.presets).map(function (name) { return "<button data-preset='" + name + "'>" + ({balanced:"Équilibré",fitness:"Fitness",recovery:"Récupération",nutrition:"Nutrition",minimal:"Minimal"}[name]) + "</button>"; }).join("") + "</div><div class='vux-density'><button data-density='comfortable' class='" + (settings.density === "comfortable" ? "active" : "") + "'>Confortable</button><button data-density='compact' class='" + (settings.density === "compact" ? "active" : "") + "'>Compact</button></div><div class='vux-sort-list'>" + settings.order.map(function (id) {
      var widget=Core.widgets.filter(function (x) { return x.id === id; })[0]; var shown=settings.hidden.indexOf(id)<0;
      return "<div class='vux-sort-item' draggable='true' data-sort='" + id + "'><span class='vux-drag-handle' aria-label='Déplacer " + esc(widget.title) + "'>" + icon("drag") + "</span><label><input type='checkbox' data-toggle='" + id + "' " + (shown ? "checked" : "") + "><span>" + esc(widget.title) + "</span></label><div><button data-move='-1' data-id='" + id + "' aria-label='Monter " + esc(widget.title) + "'>↑</button><button data-move='1' data-id='" + id + "' aria-label='Descendre " + esc(widget.title) + "'>↓</button></div></div>";
    }).join("") + "</div><button class='vux-primary-button' data-restore>Restaurer les réglages par défaut</button>";
    var dragged = null;
    body.onclick = function (event) {
      var preset=event.target.closest("[data-preset]"), move=event.target.closest("[data-move]"), restore=event.target.closest("[data-restore]"), density=event.target.closest("[data-density]");
      if (preset) settings=Core.applyPreset(settings,preset.getAttribute("data-preset"));
      else if (move) settings=Core.move(settings,move.getAttribute("data-id"),Number(move.getAttribute("data-move")));
      else if (restore) settings=Core.defaults();
      else if (density) { settings=Core.sanitize(settings); settings.density=density.getAttribute("data-density"); }
      else return;
      saveSettings(settings); modal.remove(); openCustomize(); render();
    };
    body.onchange = function (event) { if (event.target.hasAttribute("data-toggle")) { settings=Core.toggle(settings,event.target.getAttribute("data-toggle"),event.target.checked); saveSettings(settings); render(); } };
    body.querySelectorAll("[data-sort]").forEach(function (item) {
      item.ondragstart=function(){dragged=item.getAttribute("data-sort");item.classList.add("dragging");};
      item.ondragend=function(){dragged=null;item.classList.remove("dragging");};
      item.ondragover=function(event){event.preventDefault();item.classList.add("drop-target");};
      item.ondragleave=function(){item.classList.remove("drop-target");};
      item.ondrop=function(event){event.preventDefault();item.classList.remove("drop-target");if(dragged){settings=Core.reorder(settings,dragged,item.getAttribute("data-sort"));saveSettings(settings);modal.remove();openCustomize();render();}};
    });
    body.querySelectorAll(".vux-drag-handle").forEach(function (handle) {
      var timer = null, active = false, target = null;
      function clearTarget() { if (target) target.classList.remove("drop-target"); target = null; }
      handle.onpointerdown = function (event) {
        var item = handle.closest("[data-sort]");
        timer = setTimeout(function () {
          active = true; dragged = item.getAttribute("data-sort"); item.classList.add("dragging");
          if (navigator.vibrate) navigator.vibrate(20);
        }, 420);
        if (handle.setPointerCapture) handle.setPointerCapture(event.pointerId);
      };
      handle.onpointermove = function (event) {
        if (!active) return;
        event.preventDefault(); clearTarget();
        var sheet = modal.querySelector(".vux-sheet");
        if (sheet && event.clientY < 80) sheet.scrollBy(0, -24);
        else if (sheet && event.clientY > window.innerHeight - 80) sheet.scrollBy(0, 24);
        var hit = document.elementFromPoint(event.clientX, event.clientY);
        target = hit && hit.closest ? hit.closest("[data-sort]") : null;
        if (target) target.classList.add("drop-target");
      };
      function finish(event) {
        clearTimeout(timer); timer = null;
        if (!active) return;
        event.preventDefault(); event.stopPropagation(); active = false;
        var source = dragged; dragged = null;
        var sourceNode = body.querySelector(".dragging"); if (sourceNode) sourceNode.classList.remove("dragging");
        if (source && target) {
          settings = Core.reorder(settings, source, target.getAttribute("data-sort"));
          saveSettings(settings); modal.remove(); openCustomize(); render();
        }
        clearTarget();
      }
      handle.onpointerup = finish;
      handle.onpointercancel = function (event) { active = false; dragged = null; clearTimeout(timer); clearTarget(); var node=body.querySelector(".dragging");if(node)node.classList.remove("dragging");event.stopPropagation(); };
    });
  }
  function openSettings() {
    var modal = layer("settings", "Réglages Vitalis", "Personnalisation et confidentialité");
    modal.querySelector("[data-layer-body]").innerHTML = "<div class='vux-settings-list'><button data-open='appearance'>" + icon("settings") + "<span><b>Apparence</b><small>Thème, accent, mouvement</small></span><i>›</i></button><button data-open='customize'>" + icon("customize") + "<span><b>Personnaliser le tableau</b><small>Widgets, ordre et préréglages</small></span><i>›</i></button><button data-open='privacy'>" + icon("sources") + "<span><b>Confidentialité et données</b><small>Export, import, suppression et clés</small></span><i>›</i></button></div>";
    modal.onclick=function(event){var open=event.target.closest("[data-open]");if(!open)return;var target=open.getAttribute("data-open");modal.remove();if(target==="appearance")openAppearance();else if(target==="customize")openCustomize();else if(bridge&&bridge.openPrivacyDataSettings)bridge.openPrivacyDataSettings();};
  }
  function installStyles() {
    if (document.getElementById("vitalis-final-ux-style")) return;
    var style=document.createElement("style");style.id="vitalis-final-ux-style";style.textContent = `
:root{--vux-bg:#f4f7f5;--vux-surface:#fff;--vux-elevated:#fff;--vux-subtle:#eaf2ee;--vux-primary:#075f45;--vux-secondary:#0d8060;--vux-accent:#17a673;--vux-text:#10231d;--vux-text2:#42564f;--vux-muted:#6e8079;--vux-success:#14815e;--vux-warning:#b77900;--vux-error:#bb3f39;--vux-info:#176aa4;--vux-border:#dce7e1;--vux-chart1:#16a275;--vux-chart2:#288dc2;--vux-xs:.25rem;--vux-sm:.5rem;--vux-md:.875rem;--vux-lg:1.25rem;--vux-xl:1.75rem;--vux-radius-sm:12px;--vux-radius-card:22px;--vux-radius-hero:30px;--vux-radius-pill:999px;--vux-shadow-card:0 8px 24px rgba(14,54,42,.08);--vux-shadow-floating:0 18px 50px rgba(7,34,26,.18);--vux-fast:120ms;--vux-standard:220ms;--vux-emphasized:360ms}
:root[data-vitalis-effective-theme=ocean]{--vux-bg:#eef8fb;--vux-surface:#fff;--vux-subtle:#e0f2f6;--vux-primary:#075985;--vux-secondary:#087b8f;--vux-accent:#0e9f9b;--vux-text:#102a36;--vux-text2:#395c69;--vux-muted:#64808b;--vux-border:#d3e7ed;--vux-chart1:#0891b2;--vux-chart2:#0f766e}
:root[data-vitalis-effective-theme=dark]{--vux-bg:#101715;--vux-surface:#18211e;--vux-elevated:#202b27;--vux-subtle:#26332e;--vux-primary:#63d7aa;--vux-secondary:#43b7aa;--vux-accent:#71d5b0;--vux-text:#f2f7f4;--vux-text2:#cad7d1;--vux-muted:#9fb2aa;--vux-border:#31413a;--vux-shadow-card:0 9px 30px rgba(0,0,0,.25);--vux-shadow-floating:0 20px 55px rgba(0,0,0,.5)}
:root[data-vitalis-effective-theme=amoled]{--vux-bg:#000;--vux-surface:#050706;--vux-elevated:#0a0e0c;--vux-subtle:#101612;--vux-primary:#74e5b7;--vux-secondary:#4ccaa1;--vux-accent:#74e5b7;--vux-text:#fff;--vux-text2:#d4ddd8;--vux-muted:#a0aea7;--vux-border:#26312c;--vux-shadow-card:none;--vux-shadow-floating:0 14px 40px rgba(0,0,0,.8)}
:root[data-vitalis-effective-theme=aurora]{--vux-bg:#0c1226;--vux-surface:rgba(24,32,65,.88);--vux-elevated:rgba(35,46,88,.94);--vux-subtle:rgba(72,86,148,.26);--vux-primary:#70e2d0;--vux-secondary:#77a7ff;--vux-accent:#a68cff;--vux-text:#f7f8ff;--vux-text2:#d5daf4;--vux-muted:#aab4d8;--vux-border:rgba(173,190,255,.22);--vux-chart1:#6be0cf;--vux-chart2:#a88cff;--vux-shadow-card:0 14px 36px rgba(3,5,18,.38);--vux-shadow-floating:0 24px 70px rgba(0,0,0,.55)}
:root[data-vitalis-accent=ocean]{--vux-accent:#1687bd}:root[data-vitalis-accent=aqua]{--vux-accent:#00a6a6}:root[data-vitalis-accent=indigo]{--vux-accent:#5969db}:root[data-vitalis-accent=violet]{--vux-accent:#8b5fd3}:root[data-vitalis-accent=coral]{--vux-accent:#e2675f}
body.vitalis-final-ux-active{margin:0!important;background:var(--vux-bg)!important;color:var(--vux-text)!important;font-family:Inter,system-ui,-apple-system,"Segoe UI",sans-serif!important;padding-bottom:0!important}body.vitalis-final-ux-active>:not(#vitalis-final-ux):not(.vitalis-native-overlay):not(.vitalis-power-overlay-312):not(script):not(style){display:none!important}
#vitalis-final-ux{min-height:100vh;background:var(--vux-bg);color:var(--vux-text);font-size:16px;line-height:1.45;padding-bottom:92px;transition:background var(--vux-standard),color var(--vux-standard)}#vitalis-final-ux *{box-sizing:border-box}#vitalis-final-ux button,#vitalis-final-ux input{font:inherit}#vitalis-final-ux button{cursor:pointer;min-height:48px}#vitalis-final-ux button:focus-visible,#vitalis-final-ux [tabindex]:focus-visible{outline:3px solid color-mix(in srgb,var(--vux-accent) 70%,white);outline-offset:3px}.vux-icon{width:24px;height:24px;flex:none}.vux-appbar{position:sticky;top:0;z-index:20;padding:calc(12px + env(safe-area-inset-top)) 16px 12px;background:color-mix(in srgb,var(--vux-bg) 88%,transparent);backdrop-filter:blur(16px);border-bottom:1px solid var(--vux-border)}.vux-appbar-top{display:flex;align-items:center;justify-content:space-between;gap:12px;max-width:1120px;margin:auto}.vux-brand{display:flex;align-items:center;gap:10px}.vux-logo{width:44px;height:44px;border-radius:15px;background:linear-gradient(145deg,var(--vux-accent),var(--vux-primary));color:#fff;display:grid;place-items:center}.vux-brand b{display:block;font-size:1rem}.vux-brand small{color:var(--vux-muted);font-size:.73rem}.vux-icon-button{width:48px;height:48px;border:1px solid var(--vux-border);border-radius:16px;background:var(--vux-surface);color:var(--vux-text);display:grid;place-items:center}.vux-datebar{display:flex;align-items:center;justify-content:center;gap:8px;margin:12px auto 0;max-width:1120px}.vux-datebar button{border:0;background:transparent;color:var(--vux-text);width:48px}.vux-date-button{width:auto!important;min-width:150px!important;border:1px solid var(--vux-border)!important;background:var(--vux-surface)!important;border-radius:var(--vux-radius-pill)!important;padding:8px 16px;display:flex;align-items:center;justify-content:center;gap:8px;font-weight:750}.vux-date-button input{position:absolute;opacity:0;pointer-events:none}.vux-content{max-width:1120px;margin:auto;padding:18px 16px}.vux-context{display:flex;justify-content:space-between;align-items:center;gap:12px;margin:2px 2px 16px}.vux-context h1{font-size:clamp(1.35rem,5vw,2rem);line-height:1.15;margin:0}.vux-context p{margin:5px 0 0;color:var(--vux-muted);font-size:.84rem}.vux-offline{display:flex;align-items:center;gap:9px;background:color-mix(in srgb,var(--vux-warning) 14%,var(--vux-surface));color:var(--vux-text);border:1px solid color-mix(in srgb,var(--vux-warning) 35%,var(--vux-border));border-radius:16px;padding:11px 13px;margin-bottom:14px;font-size:.82rem}.hidden{display:none!important}.vux-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.vux-card{position:relative;background:var(--vux-surface);border:1px solid var(--vux-border);border-radius:var(--vux-radius-card);padding:16px;box-shadow:var(--vux-shadow-card);transition:transform var(--vux-fast),opacity var(--vux-fast),border var(--vux-fast);overflow:hidden}.vux-card[role=button]:active{transform:scale(.985);opacity:.88}.vux-card-top{display:flex;align-items:center;gap:9px}.vux-card-title{font-weight:780;font-size:.86rem;flex:1}.vux-chevron{font-size:1.5rem;color:var(--vux-muted)}.vux-icon-box{width:42px;height:42px;border-radius:14px;background:color-mix(in srgb,var(--vux-accent) 15%,var(--vux-subtle));color:var(--vux-accent);display:grid;place-items:center;flex:none}.vux-metric-line{display:flex;align-items:baseline;gap:6px;margin-top:16px}.vux-metric-line strong{font-size:clamp(1.55rem,7vw,2.35rem);line-height:1;font-variant-numeric:tabular-nums}.vux-metric-line span{font-size:.78rem;color:var(--vux-muted);font-weight:700}.vux-secondary{min-height:2.5em;margin:8px 0;color:var(--vux-muted);font-size:.73rem}.vux-progress{height:7px;background:var(--vux-subtle);border-radius:99px;overflow:hidden;margin-top:11px}.vux-progress i{display:block;height:100%;background:linear-gradient(90deg,var(--vux-accent),var(--vux-secondary));border-radius:inherit}.vux-spark{width:100%;height:38px;margin-top:9px}.vux-spark polyline{fill:none;stroke:var(--vux-chart1);stroke-width:3;vector-effect:non-scaling-stroke}.vux-score-card{grid-column:1/-1;background:linear-gradient(135deg,color-mix(in srgb,var(--vux-accent) 20%,var(--vux-surface)),var(--vux-surface));border-radius:var(--vux-radius-hero);padding:22px;display:flex;align-items:center;justify-content:space-between;gap:18px;min-height:190px}.vux-score-card h2{font-size:clamp(1.35rem,5vw,2rem);margin:5px 0}.vux-score-card p{color:var(--vux-text2);max-width:520px;font-size:.84rem}.vux-eyebrow{font-size:.68rem;letter-spacing:.12em;font-weight:850;color:var(--vux-accent)}.vux-component-chips{display:flex;gap:6px;flex-wrap:wrap}.vux-component-chips span{font-size:.68rem;background:var(--vux-subtle);padding:5px 8px;border-radius:99px}.vux-score-ring{--angle:calc(var(--score)*3.6deg);width:120px;height:120px;border-radius:50%;background:conic-gradient(var(--vux-accent) var(--angle),var(--vux-subtle) 0);display:grid;place-items:center;flex:none}.vux-score-ring:before{content:"";position:absolute;width:92px;height:92px;border-radius:50%;background:var(--vux-surface)}.vux-score-ring div{position:relative;text-align:center}.vux-score-ring strong{display:block;font-size:2rem;line-height:1}.vux-score-ring small{color:var(--vux-muted);font-size:.68rem}.vux-inline-actions{display:flex;gap:6px;margin-top:12px;position:relative;z-index:2}.vux-inline-actions button,.vux-card-action{border:1px solid var(--vux-border);background:var(--vux-subtle);color:var(--vux-text);border-radius:12px;padding:7px 9px;min-height:40px!important;font-size:.69rem;font-weight:750;flex:1}.vux-inline-actions button.primary,.vux-card-action{background:var(--vux-accent);color:#fff;border-color:transparent}.vux-coach-card{grid-column:1/-1;display:flex;gap:16px;align-items:center;background:linear-gradient(135deg,color-mix(in srgb,var(--vux-accent) 12%,var(--vux-surface)),var(--vux-surface))}.vux-coach-card img{width:92px;height:92px;object-fit:cover;object-position:center 28%;border-radius:22px}.vux-coach-card h3{font-size:1.4rem;margin:3px 0}.vux-coach-card p{color:var(--vux-muted);margin:0 0 10px}.vux-coach-card button{border:0;border-radius:14px;background:var(--vux-accent);color:#fff;padding:9px 14px;font-weight:750}.vux-source-card{grid-column:1/-1;display:flex;align-items:center;gap:12px}.vux-source-card div:nth-child(2){flex:1}.vux-source-card strong,.vux-source-card small{display:block}.vux-source-card small{color:var(--vux-muted);font-size:.72rem;margin-top:3px}.vux-quick{grid-column:1/-1;margin:6px 0}.vux-section-heading h2{margin:2px 0 10px;font-size:1.15rem}.vux-quick-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:9px}.vux-quick-action{border:1px solid var(--vux-border);background:var(--vux-surface);color:var(--vux-text);border-radius:17px;padding:10px 5px;min-height:82px!important;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:6px;box-shadow:var(--vux-shadow-card)}.vux-quick-action span{font-size:.67rem;font-weight:740;text-align:center}.vux-bottom-nav{position:fixed;z-index:25;left:10px;right:10px;bottom:calc(8px + env(safe-area-inset-bottom));max-width:650px;margin:auto;background:color-mix(in srgb,var(--vux-surface) 94%,transparent);backdrop-filter:blur(18px);border:1px solid var(--vux-border);border-radius:23px;box-shadow:var(--vux-shadow-floating);display:grid;grid-template-columns:repeat(5,1fr);padding:4px}.vux-bottom-nav button{border:0;background:transparent;color:var(--vux-muted);border-radius:17px;display:flex;align-items:center;justify-content:center;flex-direction:column;font-size:.62rem;gap:2px}.vux-bottom-nav button.active{background:var(--vux-subtle);color:var(--vux-accent);font-weight:800}.vux-bottom-nav .vux-icon{width:20px;height:20px}.vux-layer{position:fixed;z-index:2147483645;inset:0;background:rgba(3,13,10,.65);display:flex;align-items:flex-end}.vux-sheet,.vux-detail{width:100%;max-height:96vh;overflow:auto;background:var(--vux-bg);color:var(--vux-text);border-radius:28px 28px 0 0;padding:18px 16px calc(26px + env(safe-area-inset-bottom));box-shadow:var(--vux-shadow-floating)}.vux-sheet>header,.vux-detail>header{display:flex;align-items:center;gap:12px;position:sticky;top:-18px;background:var(--vux-bg);padding:18px 0 12px;z-index:2}.vux-sheet header div,.vux-detail header div{flex:1}.vux-sheet h1,.vux-detail h1{font-size:1.25rem;margin:0}.vux-sheet header p{font-size:.75rem;color:var(--vux-muted);margin:2px 0 0}.vux-text-button{border:0;background:transparent;color:var(--vux-accent);font-weight:750}.vux-detail-hero{text-align:center;background:var(--vux-surface);border:1px solid var(--vux-border);border-radius:var(--vux-radius-hero);padding:24px;margin-bottom:12px}.vux-detail-hero .vux-icon-box{margin:auto}.vux-detail-hero h2{font-size:2rem;margin:12px 0 0}.vux-detail-hero p{color:var(--vux-muted);margin:3px 0}.vux-detail-block,.vux-empty{background:var(--vux-surface);border:1px solid var(--vux-border);border-radius:var(--vux-radius-card);padding:16px;margin:10px 0}.vux-detail-block h3,.vux-empty h3{font-size:.9rem;margin:0 0 10px}.vux-detail-block p,.vux-empty p{color:var(--vux-muted);font-size:.78rem}.vux-detail-rows>div{display:flex;justify-content:space-between;gap:12px;padding:11px 0;border-bottom:1px solid var(--vux-border)}.vux-detail-rows>div:last-child{border:0}.vux-detail-rows span{color:var(--vux-muted);font-size:.78rem}.vux-detail-rows strong{text-align:right;font-size:.8rem}.vux-theme-grid,.vux-accent-grid{border:0;padding:0;margin:14px 0;display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.vux-theme-grid legend,.vux-accent-grid legend{font-weight:800;margin-bottom:8px}.vux-choice{border:1px solid var(--vux-border);background:var(--vux-surface);border-radius:17px;padding:9px;display:grid;grid-template-columns:auto 1fr;gap:7px;align-items:center}.vux-choice input{grid-row:1/3}.vux-theme-preview{height:42px;border-radius:10px;background:linear-gradient(135deg,#f4f7f5 50%,#087052 50%)}.theme-ocean{background:linear-gradient(135deg,#e5f7fb 50%,#087b8f 50%)}.theme-dark{background:linear-gradient(135deg,#101715 50%,#63d7aa 50%)}.theme-amoled{background:linear-gradient(135deg,#000 50%,#74e5b7 50%)}.theme-aurora{background:linear-gradient(135deg,#11183a,#63ddca 55%,#9b78e8)}.theme-system{background:linear-gradient(135deg,#fff 50%,#111 50%)}.vux-accent-grid{grid-template-columns:repeat(3,1fr)}.vux-accent-grid label{text-align:center;font-size:.7rem}.vux-accent-grid input{position:absolute;opacity:0}.vux-accent-grid span{display:block;width:38px;height:38px;border-radius:50%;margin:auto;border:4px solid var(--vux-surface);box-shadow:0 0 0 1px var(--vux-border)}.vux-accent-grid input:checked+span{box-shadow:0 0 0 3px var(--vux-text)}.accent-green{background:#17a673}.accent-ocean{background:#1687bd}.accent-aqua{background:#00a6a6}.accent-indigo{background:#5969db}.accent-violet{background:#8b5fd3}.accent-coral{background:#e2675f}.vux-switch{display:flex;align-items:center;justify-content:space-between;background:var(--vux-surface);border:1px solid var(--vux-border);border-radius:16px;padding:13px}.vux-switch small,.vux-switch b{display:block}.vux-switch small{color:var(--vux-muted);font-size:.7rem}.vux-switch input{width:24px;height:24px}.vux-presets{display:flex;gap:7px;overflow:auto;padding:4px 0 12px}.vux-presets button,.vux-density button{border:1px solid var(--vux-border);background:var(--vux-surface);color:var(--vux-text);border-radius:99px;padding:8px 13px;white-space:nowrap}.vux-density{display:grid;grid-template-columns:1fr 1fr;gap:7px;margin-bottom:12px}.vux-density button.active{background:var(--vux-accent);color:#fff}.vux-sort-list{display:grid;gap:7px}.vux-sort-item{display:flex;align-items:center;gap:8px;background:var(--vux-surface);border:1px solid var(--vux-border);border-radius:15px;padding:8px}.vux-sort-item.dragging{opacity:.45;transform:scale(.98)}.vux-sort-item.drop-target{border:2px dashed var(--vux-accent)}.vux-drag-handle{width:42px;height:42px;display:grid;place-items:center;color:var(--vux-muted);touch-action:none}.vux-sort-item label{display:flex;align-items:center;gap:8px;flex:1}.vux-sort-item input{width:22px;height:22px}.vux-sort-item button{width:42px;min-height:42px!important;border:0;border-radius:12px;background:var(--vux-subtle);color:var(--vux-text)}.vux-primary-button{width:100%;border:0;border-radius:15px;background:var(--vux-accent);color:#fff;font-weight:800;margin-top:14px}.vux-settings-list{display:grid;gap:9px}.vux-settings-list>button{display:flex;align-items:center;gap:12px;border:1px solid var(--vux-border);background:var(--vux-surface);color:var(--vux-text);border-radius:18px;padding:12px;text-align:left}.vux-settings-list span{flex:1}.vux-settings-list b,.vux-settings-list small{display:block}.vux-settings-list small{color:var(--vux-muted);font-size:.72rem}.vux-settings-list i{font-size:1.5rem}.vux-toast{position:fixed;z-index:2147483647;left:16px;right:16px;bottom:92px;max-width:520px;margin:auto;background:var(--vux-text);color:var(--vux-bg);padding:12px 14px;border-radius:16px;box-shadow:var(--vux-shadow-floating);display:flex;align-items:center;justify-content:space-between}.vux-toast button{border:0;background:transparent;color:var(--vux-accent);font-weight:850}.state-error{border-color:color-mix(in srgb,var(--vux-error) 45%,var(--vux-border))}.state-permission_required{border-color:color-mix(in srgb,var(--vux-warning) 45%,var(--vux-border))}[data-density=compact] .vux-card{padding:12px}[data-density=compact] .vux-secondary{min-height:auto}[data-density=compact] .vux-score-card{min-height:150px}
@media(max-width:370px){.vux-grid{grid-template-columns:1fr}.vux-score-card,.vux-coach-card,.vux-source-card,.vux-quick{grid-column:1}.vux-score-ring{width:96px;height:96px}.vux-score-ring:before{width:74px;height:74px}.vux-quick-grid{grid-template-columns:repeat(3,1fr)}.vux-brand small{display:none}}
@media(min-width:700px){.vux-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.vux-score-card,.vux-coach-card,.vux-source-card,.vux-quick{grid-column:1/-1}.vux-sheet,.vux-detail{max-width:760px;margin:auto;border-radius:28px}.vux-layer{align-items:center}.vux-quick-grid{grid-template-columns:repeat(5,1fr)}}
@media(min-width:1000px){.vux-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.vux-score-card{grid-column:span 2}.vux-coach-card{grid-column:span 2}.vux-source-card{grid-column:span 2}.vux-quick{grid-column:1/-1}}
@media(orientation:landscape) and (max-height:600px){.vux-score-card{min-height:140px}.vux-content{padding-top:10px}.vux-bottom-nav{max-width:760px}}
@media(prefers-reduced-motion:reduce){#vitalis-final-ux *,#vitalis-final-ux *:before,#vitalis-final-ux *:after{animation-duration:.01ms!important;transition-duration:.01ms!important}}.vitalis-reduced-motion #vitalis-final-ux *{animation:none!important;transition:none!important}
`;
    document.head.appendChild(style);
  }
  function installPremiumPolish() {
    if (document.getElementById("vitalis-premium-polish")) return;
    var premium = document.createElement("style");
    premium.id = "vitalis-premium-polish";
    premium.textContent = [
      "#vitalis-final-ux{background:radial-gradient(circle at 15% -10%,color-mix(in srgb,var(--vux-accent) 12%,transparent),transparent 34%),var(--vux-bg)}",
      ".vux-card{border-color:color-mix(in srgb,var(--vux-border) 78%,transparent);box-shadow:0 12px 30px rgba(8,37,29,.07),0 2px 8px rgba(8,37,29,.04);transition:transform .18s ease,box-shadow .18s ease,border-color .18s ease}",
      ".vux-card:active{transform:scale(.992)}",
      ".vux-score-card{background:linear-gradient(145deg,color-mix(in srgb,var(--vux-surface) 92%,var(--vux-accent) 8%),var(--vux-surface));overflow:hidden}",
      ".vux-quick-action{border:1px solid color-mix(in srgb,var(--vux-border) 80%,transparent);box-shadow:0 8px 20px rgba(8,37,29,.055);border-radius:20px}",
      ".vux-coach-suite{display:block!important;padding:0!important;overflow:hidden}",
      ".vux-coach-feature{display:grid;grid-template-columns:104px 1fr;gap:16px;align-items:center;padding:18px;background:linear-gradient(135deg,color-mix(in srgb,var(--vux-accent) 12%,var(--vux-surface)),var(--vux-surface));cursor:pointer}",
      ".vux-coach-hero{width:104px;height:104px;border-radius:28px;object-fit:cover;object-position:center 28%;box-shadow:0 12px 30px rgba(6,60,48,.18)}",
      ".vux-coach-copy h3{font-size:1.45rem;margin:4px 0 2px}.vux-coach-copy p{margin:0 0 4px;font-weight:750}.vux-coach-copy small{display:block;color:var(--vux-muted);line-height:1.35}",
      ".vux-coach-primary{margin-top:12px;border:0;border-radius:999px!important;padding:10px 16px!important;background:var(--vux-primary)!important;color:#fff!important;font-weight:800!important;min-height:42px!important}",
      ".vux-coach-roster{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding:12px;background:color-mix(in srgb,var(--vux-surface) 96%,var(--vux-bg))}",
      ".vux-coach-chip{display:flex!important;align-items:center!important;gap:8px!important;min-height:58px!important;padding:7px!important;border:1px solid var(--vux-border)!important;border-radius:16px!important;background:var(--vux-surface)!important;color:var(--vux-text)!important;text-align:left!important}",
      ".vux-coach-chip.active{border-color:var(--vux-accent)!important;box-shadow:0 0 0 2px color-mix(in srgb,var(--vux-accent) 18%,transparent)!important}",
      ".vux-coach-chip img{width:42px;height:42px;border-radius:13px;object-fit:cover;object-position:center 28%}.vux-coach-chip span{min-width:0}.vux-coach-chip b,.vux-coach-chip small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.vux-coach-chip b{font-size:.82rem}.vux-coach-chip small{font-size:.66rem;color:var(--vux-muted);margin-top:1px}",
      ".vux-bottom-nav{box-shadow:0 -12px 30px rgba(8,37,29,.08);border-top-color:color-mix(in srgb,var(--vux-border) 70%,transparent);backdrop-filter:blur(18px)}",
      "@media(max-width:520px){.vux-coach-feature{grid-template-columns:82px 1fr;padding:15px;gap:13px}.vux-coach-hero{width:82px;height:82px;border-radius:23px}.vux-coach-roster{grid-template-columns:repeat(2,minmax(0,1fr))}.vux-coach-copy h3{font-size:1.25rem}}"
    ].join("");
    document.head.appendChild(premium);
  }

  function mount() {
    installStyles(); installPremiumPolish(); applyTheme();
    document.body.classList.add("vitalis-final-ux-active");
    root=document.createElement("div");root.id="vitalis-final-ux";root.setAttribute("data-density",settings.density);
    root.innerHTML="<header class='vux-appbar'><div class='vux-appbar-top'><div class='vux-brand'><span class='vux-logo'>"+icon("score")+"</span><div><b>Vitalis</b><small>Santé quotidienne, simplement</small></div></div><button class='vux-icon-button' data-settings aria-label='Ouvrir les réglages'>"+icon("settings")+"</button></div><div class='vux-datebar'><button data-prev aria-label='Jour précédent'>‹</button><button class='vux-date-button' data-calendar>"+icon("calendar")+"<span data-date-label></span><input data-date-input type='date' aria-label='Choisir la date'></button><button data-next aria-label='Jour suivant'>›</button></div></header><main class='vux-content'><div class='vux-context'><div><h1>Votre journée santé</h1><p data-last-updated></p></div></div><div class='vux-offline hidden' data-offline>"+icon("offline")+"<span>Mode hors ligne — les données affichées sont mises en cache.</span></div><div class='vux-grid' data-widgets></div></main><nav class='vux-bottom-nav' aria-label='Navigation principale'><button class='active' data-nav='home'>"+icon("score")+"<span>Accueil</span></button><button data-nav='coach'>"+icon("coach")+"<span>Coach</span></button><button data-nav='sources'>"+icon("sources")+"<span>Sources</span></button><button data-nav='customize'>"+icon("customize")+"<span>Widgets</span></button><button data-nav='settings'>"+icon("settings")+"<span>Réglages</span></button></nav>";
    document.body.insertBefore(root,document.body.firstChild);
    root.addEventListener("click",function(event){var action=event.target.closest("[data-act]");if(action){event.preventDefault();event.stopPropagation();doAction(action);return;}var nav=event.target.closest("[data-nav]");if(nav){var value=nav.getAttribute("data-nav");if(value==="coach"&&window.VitalisCoaches)window.VitalisCoaches.open();else if(value==="sources"&&window.VitalisConnectorControls)window.VitalisConnectorControls.showSources();else if(value==="customize")openCustomize();else if(value==="settings")openSettings();return;}if(event.target.closest("[data-settings]"))openSettings();else if(event.target.closest("[data-prev]"))shiftDate(-1);else if(event.target.closest("[data-next]"))shiftDate(1);else if(event.target.closest("[data-calendar]")){var input=root.querySelector("[data-date-input]");if(input.showPicker)input.showPicker();else input.click();}});
    root.querySelector("[data-date-input]").onchange=function(){if(window.VitalisDate)window.VitalisDate.select(this.value);health.selectedDate=this.value;render();};
    render();
    window.__vitalisFinalUxMetrics=window.__vitalisFinalUxMetrics||{};window.__vitalisFinalUxMetrics.initialRenderMs=(performance.now?performance.now():Date.now())-renderStart;
  }

  window.addEventListener("vitalis-health-data",function(event){health=Object.assign({},health,event.detail||{});render();});
  window.addEventListener("vitalis-connectors",function(event){var detail=event.detail||{};health.sources=detail.sourcePackages||health.sources;render();});
  window.addEventListener("online",render);window.addEventListener("offline",render);
  if(media&&media.addEventListener)media.addEventListener("change",function(){if(settings.theme==="system")applyTheme();});

  window.VitalisFinalUX={
    themes:Core.themes.slice(),widgets:Core.widgets.slice(),getSettings:function(){return Core.sanitize(settings);},
    setTheme:function(theme){var next=Core.sanitize(settings);next.theme=theme;saveSettings(next);render();},
    setAccent:function(accent){var next=Core.sanitize(settings);next.accent=accent;saveSettings(next);render();},
    applyPreset:function(name){saveSettings(Core.applyPreset(settings,name));render();},
    moveWidget:function(id,delta){saveSettings(Core.move(settings,id,delta));render();},
    hideWidget:function(id,hidden){saveSettings(Core.toggle(settings,id,!hidden));render();},
    restoreDefaults:function(){saveSettings(Core.defaults());render();},
    openDetail:openDetail,openAppearance:openAppearance,openCustomize:openCustomize,addWater:addWater,
    snapshot:function(){return {date:selectedDate(),settings:Core.sanitize(settings),health:JSON.parse(JSON.stringify(health)),visible:Core.visible(settings),metrics:window.__vitalisFinalUxMetrics||{}};}
  };
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",mount);else requestAnimationFrame(mount);
})();
