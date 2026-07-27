(function () {
  "use strict";

  var api = null;
  var state = null;
  var selectedGroupId = null;
  var layout = readLayout();
  var longPressTimer = 0;
  var pressTarget = null;
  var pressX = 0;
  var pressY = 0;
  var suppressClickUntil = 0;
  var renderedKey = "";

  function byId(id) { return document.getElementById(id); }

  function init(options) {
    api = options;
    buildDialog();
    bindLongPress();
  }

  function applyState(next) {
    state = next;
    var groups = next.groups || [];
    if (!findGroup(groups, selectedGroupId)) {
      selectedGroupId = next.selectedGroupId || (groups[0] ? groups[0].id : null);
    }
    if (byId("quick-list-dialog").classList.contains("open")) render(false, selectedGroupId);
  }

  function buildDialog() {
    var backdrop = document.createElement("div");
    backdrop.id = "quick-list-dialog";
    backdrop.className = "quick-dialog-backdrop";
    backdrop.setAttribute("role", "dialog");
    backdrop.setAttribute("aria-modal", "true");
    backdrop.setAttribute("aria-label", "快捷文本列表");
    backdrop.innerHTML =
      '<div class="quick-dialog-shell">' +
        '<section class="quick-dialog-content"><div id="quick-dialog-items" class="quick-dialog-items"></div></section>' +
        '<nav class="quick-dialog-tabs" aria-label="快捷文本分组">' +
          '<div id="quick-dialog-groups" class="quick-dialog-groups"></div>' +
          '<button id="quick-dialog-layout" class="quick-dialog-layout" type="button" title="切换列表布局" aria-label="切换列表布局"><span class="material-symbols"></span></button>' +
        '</nav>' +
      '</div>';
    document.body.appendChild(backdrop);
    backdrop.addEventListener("click", function (event) { if (event.target === backdrop) close(); });
    backdrop.firstElementChild.addEventListener("click", function (event) { event.stopPropagation(); });
    byId("quick-dialog-layout").onclick = toggleLayout;
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && backdrop.classList.contains("open")) {
        event.preventDefault();
        close();
      }
    });
  }

  function bindLongPress() {
    var items = byId("quick-items");
    items.addEventListener("pointerdown", function (event) {
      var target = event.target.closest(".quick-item:not(.quick-add-item)");
      if (!target || (event.button !== undefined && event.button !== 0)) return;
      cancelLongPress();
      pressTarget = target;
      pressX = event.clientX;
      pressY = event.clientY;
      longPressTimer = window.setTimeout(function () {
        longPressTimer = 0;
        suppressClickUntil = Date.now() + 700;
        if (navigator.vibrate) navigator.vibrate(12);
        open(target.dataset.quickGroupId);
      }, 480);
    });
    items.addEventListener("pointermove", function (event) {
      if (Math.abs(event.clientX - pressX) > 10 || Math.abs(event.clientY - pressY) > 10) cancelLongPress();
    });
    ["pointerup", "pointercancel", "pointerleave"].forEach(function (name) {
      items.addEventListener(name, cancelLongPress);
    });
    items.addEventListener("click", function (event) {
      if (Date.now() < suppressClickUntil && event.target.closest(".quick-item")) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }, true);
    items.addEventListener("contextmenu", function (event) {
      var target = event.target.closest(".quick-item:not(.quick-add-item)");
      if (!target) return;
      event.preventDefault();
      open(target.dataset.quickGroupId);
    });
  }

  function cancelLongPress() {
    window.clearTimeout(longPressTimer);
    longPressTimer = 0;
    pressTarget = null;
  }

  function open(groupId) {
    if (!state || !(state.groups || []).length) return;
    selectedGroupId = findGroup(state.groups, groupId) ? groupId : state.selectedGroupId;
    if (!findGroup(state.groups, selectedGroupId)) selectedGroupId = state.groups[0].id;
    render(false, selectedGroupId);
    var dialog = byId("quick-list-dialog");
    dialog.classList.add("open");
    document.body.classList.add("quick-dialog-open");
  }

  function close() {
    byId("quick-list-dialog").classList.remove("open");
    document.body.classList.remove("quick-dialog-open");
  }

  function render(animate, previousId) {
    var groups = state ? state.groups || [] : [];
    var selected = findGroup(groups, selectedGroupId) || groups[0];
    if (!selected) return;
    selectedGroupId = selected.id;
    var nextKey = JSON.stringify([
      layout,
      String(selectedGroupId),
      groups.map(function (group) {
        return [String(group.id), group.title, group.icon, group.items || []];
      })
    ]);
    if (!animate && nextKey === renderedKey) return;
    renderedKey = nextKey;
    var items = byId("quick-dialog-items");
    items.className = "quick-dialog-items " + layout;
    items.innerHTML = "";
    if (!(selected.items || []).length) {
      items.innerHTML = '<div class="quick-dialog-empty">当前分组暂无快捷文本</div>';
    } else {
      selected.items.forEach(function (text) {
        var button = document.createElement("button");
        button.className = "quick-dialog-item";
        button.type = "button";
        button.textContent = text;
        button.onclick = function () {
          api.sendCommand("submit", { text: text, playVoice: state.playOnSend !== false });
          close();
        };
        items.appendChild(button);
      });
    }
    renderGroups(groups);
    renderLayoutButton();
    if (animate) {
      var oldIndex = groupIndex(groups, previousId);
      var newIndex = groupIndex(groups, selected.id);
      items.classList.add(newIndex >= oldIndex ? "switch-forward" : "switch-back");
    }
  }

  function renderGroups(groups) {
    var holder = byId("quick-dialog-groups");
    holder.innerHTML = "";
    groups.forEach(function (group) {
      var button = document.createElement("button");
      button.className = "quick-dialog-group" + (String(group.id) === String(selectedGroupId) ? " active" : "");
      button.type = "button";
      button.innerHTML = '<span class="material-symbols"></span><span></span>';
      button.firstElementChild.textContent = group.icon || "label";
      button.lastElementChild.textContent = group.title || "未命名分组";
      button.onclick = function () {
        var previous = selectedGroupId;
        selectedGroupId = group.id;
        api.sendCommand("selectGroup", { groupId: group.id });
        render(true, previous);
        if (window.matchMedia("(orientation: landscape)").matches) window.KigttsRemoteGroupHint.showDialog(group.icon, group.title);
      };
      holder.appendChild(button);
    });
  }

  function toggleLayout() {
    layout = layout === "grid" ? "list" : "grid";
    try { localStorage.setItem("kigtts_quick_dialog_layout", layout); } catch (_) {}
    render(true, selectedGroupId);
  }

  function renderLayoutButton() {
    var button = byId("quick-dialog-layout");
    button.firstElementChild.textContent = layout === "grid" ? "view_list" : "grid_view";
    button.title = layout === "grid" ? "当前宫格，点击切换列表" : "当前列表，点击切换宫格";
    button.setAttribute("aria-label", button.title);
  }

  function findGroup(groups, id) {
    return (groups || []).find(function (group) { return String(group.id) === String(id); });
  }

  function groupIndex(groups, id) {
    var index = (groups || []).findIndex(function (group) { return String(group.id) === String(id); });
    return index < 0 ? 0 : index;
  }

  function readLayout() {
    try { return localStorage.getItem("kigtts_quick_dialog_layout") === "list" ? "list" : "grid"; }
    catch (_) { return "grid"; }
  }

  window.KigttsRemoteQuickDialog = { init: init, applyState: applyState };
})();
