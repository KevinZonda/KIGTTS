(function () {
  "use strict";

  var timers = new WeakMap();

  function ensureHint(container, className) {
    var hint = container.querySelector("." + className);
    if (hint) return hint;
    hint = document.createElement("div");
    hint.className = className;
    hint.setAttribute("role", "status");
    hint.setAttribute("aria-live", "polite");
    hint.innerHTML = '<span class="material-symbols"></span><span></span>';
    container.appendChild(hint);
    return hint;
  }

  function showHint(container, className, icon, title) {
    if (!container) return;
    var hint = ensureHint(container, className);
    hint.firstElementChild.textContent = icon || "label";
    hint.lastElementChild.textContent = title || "未命名分组";
    hint.classList.remove("visible");
    void hint.offsetWidth;
    hint.classList.add("visible");
    window.clearTimeout(timers.get(hint));
    timers.set(hint, window.setTimeout(function () { hint.classList.remove("visible"); }, 900));
  }

  function showMain(icon, title) {
    showHint(document.querySelector(".quick-card"), "remote-group-hint", icon, title);
  }

  function showDialog(icon, title) {
    showHint(document.querySelector(".quick-dialog-shell"), "quick-dialog-group-hint", icon, title);
  }

  document.addEventListener("click", function (event) {
    var groupTab = event.target.closest(".group-tab");
    if (groupTab && window.matchMedia("(orientation: landscape)").matches) {
      showMain(
        groupTab.querySelector(".material-symbols").textContent,
        groupTab.lastElementChild.textContent
      );
      return;
    }
    var compactButton = event.target.closest("#compact-group-previous, #compact-group-next");
    if (compactButton) {
      var current = document.querySelector("#compact-group-current");
      showMain(
        current.querySelector(".material-symbols").textContent,
        document.querySelector("#compact-group-title").textContent
      );
      return;
    }
    var dialogTab = event.target.closest(".quick-dialog-group");
    if (dialogTab && window.matchMedia("(orientation: landscape)").matches) {
      showDialog(dialogTab.querySelector(".material-symbols").textContent, dialogTab.lastElementChild.textContent);
    }
  });

  window.KigttsRemoteGroupHint = { showDialog: showDialog };
})();
