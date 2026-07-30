(function () {
  "use strict";
  if (window.__vitalisDeveloperAI) return;
  window.__vitalisDeveloperAI = true;

  var STORAGE_KEY = "vitalis-developer-ai-v1";
  var bridge = window.VitalisAndroid || null;

  function state() {
    try {
      return Object.assign({ requests: [], consent: false, mode: "advisory" }, JSON.parse(localStorage.getItem(STORAGE_KEY)) || {});
    } catch (_) {
      return { requests: [], consent: false, mode: "advisory" };
    }
  }

  function save(value) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char];
    });
  }

  function ensureStyles() {
    if (document.getElementById("vitalis-developer-ai-style")) return;
    var style = document.createElement("style");
    style.id = "vitalis-developer-ai-style";
    style.textContent =
      ".vitalis-dev-overlay{position:fixed;z-index:2147483646;inset:0;background:rgba(4,34,26,.62);display:flex;align-items:flex-end;font-family:system-ui,-apple-system,sans-serif}" +
      ".vitalis-dev-sheet{width:100%;max-height:92vh;overflow:auto;background:#f8f6ef;border-radius:24px 24px 0 0;padding:18px;color:#14342b}" +
      ".vitalis-dev-head{display:flex;gap:12px;align-items:center}.vitalis-dev-avatar{width:56px;height:56px;border-radius:18px;object-fit:cover;background:#dcebe4}" +
      ".vitalis-dev-title{flex:1}.vitalis-dev-title b,.vitalis-dev-title small{display:block}.vitalis-dev-title small{color:#667a73;margin-top:3px}" +
      ".vitalis-dev-close{border:0;border-radius:50%;width:35px;height:35px;font-size:22px;background:#e2ebe6}" +
      ".vitalis-dev-note{margin:14px 0;padding:12px;border-radius:14px;background:#e9f5ee;font-size:12px;line-height:1.45}" +
      ".vitalis-dev-textarea{width:100%;min-height:120px;border:1px solid #cad8d1;border-radius:14px;padding:12px;background:#fff;color:#14342b;font:inherit;resize:vertical}" +
      ".vitalis-dev-actions{display:flex;gap:9px;margin-top:11px}.vitalis-dev-button{flex:1;border:0;border-radius:13px;padding:12px;background:#063c30;color:#fff;font-weight:700}.vitalis-dev-button.alt{background:#e2f3e9;color:#063c30}" +
      ".vitalis-dev-result{white-space:pre-wrap;background:#fff;border:1px solid #dbe6e0;border-radius:15px;padding:13px;margin-top:13px;line-height:1.48;font-size:13px}" +
      ".vitalis-dev-history{margin-top:16px}.vitalis-dev-item{background:#fff;border:1px solid #dbe6e0;border-radius:14px;padding:11px;margin-top:8px}.vitalis-dev-item small{display:block;color:#667a73;margin-top:5px}";
    document.head.appendChild(style);
  }

  function localPlan(request) {
    return [
      "PLAN DE MODIFICATION",
      "1. Reformuler précisément le besoin : " + request,
      "2. Identifier les fichiers et modules concernés sans modifier l’interface existante.",
      "3. Vérifier les permissions, connecteurs et impacts sur les données santé.",
      "4. Préparer une branche dédiée et un diff réversible.",
      "5. Exécuter les tests Android, Health Connect, hors ligne, voix et nutrition.",
      "6. Présenter les risques et demander une validation explicite avant toute écriture ou publication.",
      "",
      "MODE ACTUEL : conseil uniquement. Aucun code, secret, compte, permission ou dépôt n’est modifié automatiquement."
    ].join("\n");
  }

  function addRequest(request, result, online) {
    var data = state();
    data.requests.unshift({ id: Date.now(), request: request, result: result, online: !!online, at: new Date().toISOString() });
    data.requests = data.requests.slice(0, 50);
    save(data);
  }

  function renderHistory(root) {
    var data = state();
    var host = root.querySelector("[data-dev-history]");
    if (!host) return;
    host.innerHTML = data.requests.length ? data.requests.slice(0, 8).map(function (item) {
      return '<div class="vitalis-dev-item"><b>' + escapeHtml(item.request) + '</b><small>' +
        new Date(item.at).toLocaleString("fr-FR") + " • " + (item.online ? "Analyse IA" : "Plan local") +
        '</small></div>';
    }).join("") : '<div class="vitalis-dev-note">Aucune demande enregistrée.</div>';
  }

  function open(initialRequest) {
    ensureStyles();
    var old = document.querySelector(".vitalis-dev-overlay");
    if (old) old.remove();
    var root = document.createElement("div");
    root.className = "vitalis-dev-overlay";
    root.innerHTML =
      '<div class="vitalis-dev-sheet">' +
      '<div class="vitalis-dev-head"><img class="vitalis-dev-avatar" alt="Vitalis Developer AI" src="https://api.dicebear.com/9.x/personas/svg?seed=VitalisDeveloper"><div class="vitalis-dev-title"><b>Vitalis Developer AI</b><small>Agent développeur séparé • lecture seule par défaut</small></div><button class="vitalis-dev-close" aria-label="Fermer">×</button></div>' +
      '<div class="vitalis-dev-note"><b>Règle de sécurité :</b> cet agent prépare les évolutions, les fichiers concernés, les risques et les tests. Il ne modifie jamais l’application, GitHub, vos données santé ou vos autorisations sans validation explicite.</div>' +
      '<textarea class="vitalis-dev-textarea" data-dev-request placeholder="Décrivez la modification souhaitée : ajouter un connecteur, corriger une fonction, améliorer un module…">' + escapeHtml(initialRequest || "") + '</textarea>' +
      '<div class="vitalis-dev-actions"><button class="vitalis-dev-button" data-dev-plan>Préparer le plan</button><button class="vitalis-dev-button alt" data-dev-clear>Effacer l’historique</button></div>' +
      '<div data-dev-result></div><div class="vitalis-dev-history"><b>Historique local</b><div data-dev-history></div></div>' +
      '</div>';
    document.body.appendChild(root);

    root.querySelector(".vitalis-dev-close").onclick = function () { root.remove(); };
    root.addEventListener("click", function (event) { if (event.target === root) root.remove(); });
    root.querySelector("[data-dev-clear]").onclick = function () {
      var data = state(); data.requests = []; save(data); renderHistory(root);
      root.querySelector("[data-dev-result]").innerHTML = '<div class="vitalis-dev-note">Historique supprimé.</div>';
    };
    root.querySelector("[data-dev-plan]").onclick = function () {
      var request = root.querySelector("[data-dev-request]").value.trim();
      if (!request) {
        root.querySelector("[data-dev-result]").innerHTML = '<div class="vitalis-dev-note">Décrivez d’abord la modification souhaitée.</div>';
        return;
      }
      var result = localPlan(request);
      addRequest(request, result, false);
      root.querySelector("[data-dev-result]").innerHTML = '<div class="vitalis-dev-result">' + escapeHtml(result) + '</div>';
      renderHistory(root);
      window.dispatchEvent(new CustomEvent("vitalis-developer-request", { detail: { request: request, plan: result, requiresApproval: true } }));
    };
    renderHistory(root);
  }

  window.VitalisDeveloperAI = {
    open: open,
    getState: state,
    clearHistory: function () { var data = state(); data.requests = []; save(data); },
    capabilities: {
      advisoryOnly: true,
      requiresExplicitApproval: true,
      repositoryWriteDefault: false,
      healthDataWriteDefault: false,
      secretsInClient: false
    }
  };

  document.addEventListener("click", function (event) {
    var target = event.target && event.target.closest ? event.target.closest("button,a,[role='button']") : null;
    if (!target) return;
    var label = String((target.innerText || "") + " " + (target.getAttribute("aria-label") || "")).toLowerCase();
    if (/developer ai|developpeur ia|agent developpeur|modifier vitalis|faire evoluer vitalis/.test(label)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      open();
    }
  }, true);
})();
