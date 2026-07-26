(function () {
  "use strict";

  function init(root, callbacks) {
    function visible(element) {
      if (!element || element.disabled || element.tabIndex < 0) return false;
      var style = getComputedStyle(element);
      var rect = element.getBoundingClientRect();
      return style.visibility !== "hidden" && style.display !== "none" && rect.width > 0 && rect.height > 0;
    }

    function focusScope() {
      var dialog = root.querySelector(".led-dialog.active");
      if (dialog) return dialog;
      var panel = root.querySelector(".led-panel.active");
      return panel || root;
    }

    function candidates() {
      if (callbacks.isLocked()) return [callbacks.unlockButton()].filter(visible);
      var selector = "button,input,[tabindex]:not([tabindex='-1'])";
      return Array.prototype.filter.call(focusScope().querySelectorAll(selector), visible);
    }

    function center(rect) {
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
    }

    function scoreCandidate(origin, candidate, direction) {
      var point = center(candidate.getBoundingClientRect());
      var dx = point.x - origin.x;
      var dy = point.y - origin.y;
      var primary;
      var secondary;
      if (direction === "left") { primary = -dx; secondary = Math.abs(dy); }
      else if (direction === "right") { primary = dx; secondary = Math.abs(dy); }
      else if (direction === "up") { primary = -dy; secondary = Math.abs(dx); }
      else { primary = dy; secondary = Math.abs(dx); }
      if (primary <= 2) return Number.POSITIVE_INFINITY;
      return primary + secondary * 0.42 + secondary * secondary / Math.max(180, primary * 8);
    }

    function verticalCandidate(origin, list, active, direction) {
      var choices = [];
      for (var i = 0; i < list.length; i++) {
        var candidate = list[i];
        if (candidate === active) continue;
        var point = center(candidate.getBoundingClientRect());
        var primary = direction === "up" ? origin.y - point.y : point.y - origin.y;
        if (primary <= 2) continue;
        choices.push({
          element: candidate,
          primary: primary,
          secondary: Math.abs(point.x - origin.x)
        });
      }
      if (!choices.length) return null;
      var nearestRow = Math.min.apply(null, choices.map(function (choice) { return choice.primary; }));
      var rowTolerance = Math.max(10, Math.min(28, nearestRow * 0.3));
      choices = choices.filter(function (choice) {
        return choice.primary <= nearestRow + rowTolerance;
      });
      choices.sort(function (a, b) {
        return a.secondary - b.secondary || a.primary - b.primary;
      });
      return choices[0].element;
    }

    function focusInitial(list) {
      var preferred = focusScope().querySelector("[data-tv-focus-first]") || list[0];
      if (preferred) focusElement(preferred);
    }

    function focusElement(element) {
      try { element.focus({ preventScroll: true }); } catch (_) { element.focus(); }
      element.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" });
    }

    function move(direction) {
      var list = candidates();
      if (!list.length) return;
      var active = document.activeElement;
      if (list.indexOf(active) < 0) { focusInitial(list); return; }
      var origin = center(active.getBoundingClientRect());
      if (direction === "up" || direction === "down") {
        var vertical = verticalCandidate(origin, list, active, direction);
        if (vertical) focusElement(vertical);
        return;
      }
      var best = null;
      var bestScore = Number.POSITIVE_INFINITY;
      for (var i = 0; i < list.length; i++) {
        if (list[i] === active) continue;
        var score = scoreCandidate(origin, list[i], direction);
        if (score < bestScore) { best = list[i]; bestScore = score; }
      }
      if (best) focusElement(best);
    }

    function isEditable(element) {
      return element && (element.tagName === "INPUT" && element.type !== "range" && element.type !== "checkbox");
    }

    function onKeyDown(event) {
      if (!document.body.classList.contains("display-page")) return;
      var key = event.key;
      var direction = key === "ArrowLeft" ? "left" : key === "ArrowRight" ? "right" :
        key === "ArrowUp" ? "up" : key === "ArrowDown" ? "down" : null;
      if (direction) {
        callbacks.onActivity();
        if (document.activeElement && document.activeElement.type === "range" &&
            (direction === "left" || direction === "right")) return;
        event.preventDefault();
        move(direction);
        return;
      }
      if (key === "Enter" || key === " " || key === "Select") {
        callbacks.onActivity();
        if (document.activeElement === document.body || document.activeElement === root) {
          event.preventDefault();
          focusInitial(candidates());
        }
        return;
      }
      var back = key === "Escape" || key === "BrowserBack" || key === "GoBack" || event.keyCode === 4;
      if (back || (key === "Backspace" && !isEditable(document.activeElement))) {
        event.preventDefault();
        callbacks.onBack();
        return;
      }
      if (key === "Settings" || key === "ContextMenu" || key === "Menu" || event.keyCode === 82) {
        event.preventDefault();
        callbacks.openSettings();
      }
    }

    document.addEventListener("keydown", onKeyDown, true);
    return {
      focusPanel: function () {
        window.setTimeout(function () { focusInitial(candidates()); }, 230);
      },
      focusUnlock: function () { focusElement(callbacks.unlockButton()); }
    };
  }

  window.KigttsTvRemote = { init: init };
})();
