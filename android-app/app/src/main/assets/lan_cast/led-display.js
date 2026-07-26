(function () {
  "use strict";

  var root;
  var api;
  var renderer;
  var state = null;
  var activePanel = "none";
  var locked = false;
  var selectedGroupId = null;
  var playOnSend = true;
  var audioEnabled = false;
  var controlsTimer = 0;
  var guideTimer = 0;
  var settingsTimer = 0;
  var wakeLock = null;
  var wakeLockPending = false;
  var tvRemote = null;

  function byId(id) { return document.getElementById(id); }

  function init(options) {
    api = options;
    root = byId("display-view");
    renderer = window.KigttsLedRenderer.create(byId("led-canvas"), byId("adaptive-stage"), byId("adaptive-text"));
    bindControls();
    bindInputPanel();
    bindQuickPanel();
    bindSettingsPanel();
    bindGestures();
    tvRemote = window.KigttsTvRemote.init(root, {
      isLocked: function () { return locked; },
      unlockButton: function () { return byId("led-lock-button"); },
      onActivity: revealControls,
      onBack: handleBack,
      openSettings: function () { openPanel("settings"); }
    });
    revealControls();
    guideTimer = window.setTimeout(hideGuide, 3000);
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) updateWakeLock();
    });
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", updatePreviewInsets);
    }
  }

  function applyState(next, animateText) {
    state = next;
    playOnSend = next.playOnSend !== false;
    if (selectedGroupId === null || !findGroup(selectedGroupId)) {
      selectedGroupId = next.selectedGroupId || (next.groups[0] && next.groups[0].id);
    }
    applyDisplayColors();
    renderer.applyState(next, animateText);
    updateInputPlayIcon();
    renderQuickPanel(false);
    fillSettings();
    updateGuideText();
    updateScreenEffects();
  }

  function bindControls() {
    byId("fullscreen-button").onclick = toggleFullscreen;
    byId("led-settings-button").onclick = function () { openPanel("settings"); };
    byId("led-quick-button").onclick = function () { openPanel("quick"); };
    byId("led-input-button").onclick = function () { openPanel("input"); };
    byId("led-lock-button").onclick = toggleLock;
    byId("led-panel-scrim").onclick = closePanel;
    byId("led-guide").onclick = function () { hideGuide(); revealControls(); };
    document.addEventListener("fullscreenchange", function () {
      byId("fullscreen-button").querySelector(".material-symbols").textContent =
        document.fullscreenElement ? "fullscreen_exit" : "fullscreen";
    });
  }

  function bindInputPanel() {
    var input = byId("led-text-input");
    input.addEventListener("input", updateInputPreview);
    input.addEventListener("keyup", updateInputPreview);
    input.addEventListener("click", updateInputPreview);
    input.addEventListener("select", updateInputPreview);
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter") { event.preventDefault(); submitInput(); }
    });
    byId("led-cursor-left").onclick = function () { moveCursor(-1); };
    byId("led-cursor-right").onclick = function () { moveCursor(1); };
    byId("led-play-on-send").onclick = function () {
      playOnSend = !playOnSend;
      updateInputPlayIcon();
      api.sendCommand("playOnSend", { enabled: playOnSend });
    };
    byId("led-input-clear").onclick = function () {
      input.value = "";
      input.focus();
      updateInputPreview();
    };
    byId("led-input-send").onclick = submitInput;
  }

  function bindQuickPanel() {
    byId("led-group-previous").onclick = function () { changeGroup(-1); };
    byId("led-group-next").onclick = function () { changeGroup(1); };
  }

  function bindSettingsPanel() {
    byId("led-settings-close").onclick = closePanel;
    byId("led-reset-settings").onclick = function () { setDialogVisible(true); };
    byId("led-reset-cancel").onclick = function () { setDialogVisible(false); };
    byId("led-reset-confirm").onclick = function () {
      setDialogVisible(false);
      api.sendCommand("resetLedSettings");
    };
    var ids = ["led-normal-font", "led-adaptive-multiline", "led-color", "led-background-color", "led-density",
      "led-glow-enabled", "led-glow-strength", "led-height", "led-speed", "led-quick-swipe",
      "led-gap", "led-keep-awake", "led-follow-brightness", "led-brightness"];
    ids.forEach(function (id) {
      var input = byId(id);
      input.addEventListener("input", function () { settingsChanged(input.type === "range"); });
      input.addEventListener("change", function () { settingsChanged(false); });
    });
    byId("led-web-audio").onchange = function () { api.setAudioEnabled(this.checked); };
    Array.prototype.forEach.call(root.querySelectorAll(".led-setting-segments[data-setting] button"), function (button) {
      button.onclick = function () {
        setSegment(button.parentElement.parentElement.querySelector(".led-setting-segments").dataset.setting,
          Number(button.dataset.value));
        settingsChanged(false);
      };
    });
  }

  function openPanel(panel) {
    if (locked) return;
    revealControls();
    hideGuide();
    if (activePanel === panel) { closePanel(); return; }
    closePanel(false);
    activePanel = panel;
    byId("led-panel-scrim").classList.add("active");
    byId("led-" + panel + "-panel").classList.add("active");
    if (panel === "input") {
      var input = byId("led-text-input");
      if (!input.value && state && state.inputText) input.value = state.inputText;
      updateInputPreview();
      window.setTimeout(function () {
        input.focus();
        updatePreviewInsets();
      }, 230);
    } else {
      renderer.clearLocalPreview();
      if (tvRemote) tvRemote.focusPanel();
    }
  }

  function closePanel(clearPreview) {
    activePanel = "none";
    byId("led-panel-scrim").classList.remove("active");
    Array.prototype.forEach.call(root.querySelectorAll(".led-panel.active"), function (panel) {
      panel.classList.remove("active");
    });
    setDialogVisible(false);
    if (clearPreview !== false) renderer.clearLocalPreview();
    updatePreviewInsets();
    if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
  }

  function updateInputPreview() {
    var input = byId("led-text-input");
    byId("led-input-clear").classList.toggle("visible", input.value.length > 0);
    byId("led-input-send").disabled = !input.value.trim();
    if (activePanel === "input" && input.value) renderer.setLocalPreview(input.value, input.selectionStart || 0);
    else renderer.clearLocalPreview();
    updatePreviewInsets();
    revealControls();
  }

  function moveCursor(delta) {
    var input = byId("led-text-input");
    var position = Math.max(0, Math.min(input.value.length, (input.selectionStart || 0) + delta));
    input.focus();
    input.setSelectionRange(position, position);
    updateInputPreview();
  }

  function submitInput() {
    var input = byId("led-text-input");
    var text = input.value.trim();
    if (!text) { input.focus(); return; }
    api.sendCommand("submit", { text: text, playVoice: playOnSend });
    input.value = "";
    updateInputPreview();
  }

  function updateInputPlayIcon() {
    var button = byId("led-play-on-send");
    button.querySelector(".material-symbols").textContent = playOnSend ? "volume_up" : "volume_off";
    button.setAttribute("aria-label", playOnSend ? "发送时播放语音：开" : "发送时播放语音：关");
  }

  function updatePreviewInsets() {
    if (!renderer) return;
    var stage = byId("adaptive-stage");
    var panel = byId("led-input-panel");
    var bottomInset = activePanel === "input" ? panel.getBoundingClientRect().height + 14 : 0;
    stage.style.paddingBottom = bottomInset ? bottomInset + "px" : "";
    renderer.resize();
  }

  function renderQuickPanel(animate) {
    if (!state || !state.groups) return;
    var group = findGroup(selectedGroupId) || state.groups[0];
    if (!group) return;
    selectedGroupId = group.id;
    byId("led-group-title").textContent = group.title || "未命名分组";
    var list = byId("led-quick-items");
    list.textContent = "";
    (group.items || []).forEach(function (text) {
      var button = document.createElement("button");
      button.className = "led-quick-item";
      button.textContent = text;
      button.onclick = function () { api.sendCommand("submit", { text: text, playVoice: playOnSend }); };
      list.appendChild(button);
    });
    var add = document.createElement("button");
    add.className = "led-quick-item led-add-current";
    add.setAttribute("aria-label", "添加当前文本");
    add.innerHTML = '<span class="material-symbols">add</span>';
    add.onclick = function () { api.sendCommand("addCurrentText", { groupId: group.id }); };
    list.appendChild(add);
    var rail = byId("led-group-rail");
    rail.textContent = "";
    state.groups.forEach(function (item) {
      var button = document.createElement("button");
      button.className = "led-group-button" + (String(item.id) === String(group.id) ? " active" : "");
      button.setAttribute("aria-label", item.title || "未命名分组");
      button.innerHTML = '<span class="material-symbols"></span>';
      button.firstChild.textContent = item.icon || "folder";
      button.onclick = function () { selectGroup(item.id, true); };
      rail.appendChild(button);
    });
    if (animate) animateGroupSwitch();
  }

  function findGroup(id) {
    if (!state || !state.groups) return null;
    for (var i = 0; i < state.groups.length; i++) if (String(state.groups[i].id) === String(id)) return state.groups[i];
    return null;
  }

  function changeGroup(delta) {
    if (!state || !state.groups.length) return;
    var index = state.groups.indexOf(findGroup(selectedGroupId));
    selectGroup(state.groups[(index + delta + state.groups.length) % state.groups.length].id, true);
  }

  function selectGroup(id, notify) {
    selectedGroupId = id;
    renderQuickPanel(true);
    if (notify) api.sendCommand("selectGroup", { groupId: id });
  }

  function animateGroupSwitch() {
    [byId("led-group-title"), byId("led-quick-items")].forEach(function (element) {
      element.classList.remove("switching"); void element.offsetWidth; element.classList.add("switching");
    });
  }

  function fillSettings() {
    if (!state || !state.led) return;
    var led = state.led;
    setChecked("led-normal-font", !led.dotMatrix);
    setValue("led-color", led.color || "#ffffff");
    setValue("led-background-color", led.background || "#000000");
    setValue("led-density", led.dotDensity);
    setChecked("led-glow-enabled", led.glowEnabled);
    setValue("led-glow-strength", led.glowStrength);
    setValue("led-height", led.displayHeightFraction);
    setChecked("led-adaptive-multiline", led.adaptiveMultiLine);
    setValue("led-speed", led.speed);
    setChecked("led-quick-swipe", led.quickSwipeOpensQuickText);
    setValue("led-gap", led.loopGap);
    setChecked("led-keep-awake", led.keepScreenOn);
    setChecked("led-follow-brightness", led.followSystemBrightness);
    setValue("led-brightness", led.screenBrightness);
    setSegment("dotShape", Number(led.dotShape));
    setSegment("direction", Number(led.direction));
    setSegment("shortTextAlignment", Number(led.shortTextAlignment));
    byId("led-web-audio").checked = audioEnabled;
    updateSettingVisibility();
    updateSettingLabels();
  }

  function settingsChanged(debounce) {
    if (!state) return;
    var density = numberValue("led-density");
    var led = {
      color: byId("led-color").value, background: byId("led-background-color").value,
      dotMatrix: !byId("led-normal-font").checked, dotShape: segmentValue("dotShape"),
      dotDensity: density, dotSize: 4 + density * 8, dotGap: 1 + (1 - density) * 5,
      glowEnabled: byId("led-glow-enabled").checked, glowStrength: numberValue("led-glow-strength"),
      displayHeightFraction: numberValue("led-height"),
      adaptiveMultiLine: byId("led-adaptive-multiline").checked, speed: numberValue("led-speed"),
      direction: segmentValue("direction"), quickSwipeOpensQuickText: byId("led-quick-swipe").checked,
      loopGap: numberValue("led-gap"), shortTextAlignment: segmentValue("shortTextAlignment"),
      keepScreenOn: byId("led-keep-awake").checked,
      followSystemBrightness: byId("led-follow-brightness").checked,
      screenBrightness: numberValue("led-brightness")
    };
    state.led = led;
    renderer.applyState(state, false);
    applyDisplayColors();
    updateSettingVisibility();
    updateSettingLabels();
    updateScreenEffects();
    window.clearTimeout(settingsTimer);
    settingsTimer = window.setTimeout(function () { api.sendCommand("ledSettings", { settings: led }); }, debounce ? 120 : 0);
  }

  function setValue(id, value) { if (document.activeElement !== byId(id)) byId(id).value = value; }
  function setChecked(id, value) { byId(id).checked = value === true; }
  function numberValue(id) { return Number(byId(id).value); }
  function setSegment(name, value) {
    var group = root.querySelector('[data-setting="' + name + '"]');
    group.dataset.value = value;
    Array.prototype.forEach.call(group.querySelectorAll("button"), function (button) {
      button.classList.toggle("active", Number(button.dataset.value) === Number(value));
    });
  }
  function segmentValue(name) { return Number(root.querySelector('[data-setting="' + name + '"]').dataset.value || 0); }

  function updateSettingVisibility() {
    var normal = byId("led-normal-font").checked;
    byId("led-dot-settings").hidden = normal;
    byId("led-glow-strength-row").hidden = normal || !byId("led-glow-enabled").checked;
    byId("led-scroll-options").hidden = byId("led-adaptive-multiline").checked;
    byId("led-brightness-row").hidden = byId("led-follow-brightness").checked;
  }

  function updateSettingLabels() {
    byId("led-density-value").textContent = Math.round(numberValue("led-density") * 100) + "%";
    byId("led-glow-value").textContent = Math.round(numberValue("led-glow-strength") * 100) + "%";
    byId("led-height-value").textContent = Math.round(numberValue("led-height") * 100) + "%";
    byId("led-speed-value").textContent = Math.round(numberValue("led-speed")) + " dp/s";
    byId("led-gap-value").textContent = Math.round(numberValue("led-gap")) + " dp";
    byId("led-brightness-value").textContent = Math.round(numberValue("led-brightness") * 100) + "%";
  }

  function bindGestures() {
    var drag = null;
    root.addEventListener("pointerdown", function (event) {
      revealControls();
      if (locked || activePanel !== "none" || event.target.closest("button,input,.led-panel")) return;
      drag = { x: event.clientX, y: event.clientY, lastX: event.clientX, axis: "", start: Date.now(), samples: [] };
      if (root.setPointerCapture) {
        try { root.setPointerCapture(event.pointerId); } catch (_) {}
      }
    });
    root.addEventListener("pointermove", function (event) {
      revealControls();
      if (!drag) return;
      var dx = event.clientX - drag.x;
      var dy = event.clientY - drag.y;
      if (!drag.axis && Math.max(Math.abs(dx), Math.abs(dy)) > 7) {
        drag.axis = Math.abs(dx) > Math.abs(dy) * 1.08 ? "x" : Math.abs(dy) > Math.abs(dx) * 1.08 ? "y" : "";
        if (drag.axis === "x") renderer.beginDrag();
      }
      if (drag.axis === "x") { renderer.dragBy(event.clientX - drag.lastX); event.preventDefault(); }
      drag.lastX = event.clientX;
      drag.samples.push({ x: event.clientX, t: Date.now() });
      if (drag.samples.length > 8) drag.samples.shift();
    });
    root.addEventListener("pointerup", function (event) {
      if (!drag) return;
      var elapsed = Date.now() - drag.start;
      var dx = event.clientX - drag.x;
      var dy = event.clientY - drag.y;
      var first = drag.samples[0] || { x: drag.x, t: drag.start };
      var velocity = (event.clientX - first.x) * 1000 / Math.max(1, Date.now() - first.t);
      if (drag.axis === "x") {
        renderer.endDrag(velocity);
        if (state && state.led.quickSwipeOpensQuickText && dx <= -42 && velocity <= -1100 && elapsed <= 420) openPanel("quick");
      } else {
        renderer.cancelDrag();
        if (drag.axis === "y" && dy <= -54) openPanel("input");
      }
      drag = null;
    });
    root.addEventListener("pointercancel", function () { drag = null; renderer.cancelDrag(); });
    root.addEventListener("click", function (event) {
      if (event.target === root || event.target === byId("led-canvas") || event.target === byId("adaptive-stage")) {
        if (activePanel !== "none") closePanel();
      }
    });
  }

  function toggleLock() {
    locked = !locked;
    hideGuide();
    closePanel();
    root.classList.toggle("led-locked", locked);
    var button = byId("led-lock-button");
    button.querySelector(".material-symbols").textContent = locked ? "lock_open" : "lock";
    button.setAttribute("aria-label", locked ? "解锁 LED 屏幕" : "锁定 LED 屏幕");
    revealControls();
    if (locked && tvRemote) tvRemote.focusUnlock();
  }

  function revealControls() {
    root.classList.remove("controls-dimmed");
    window.clearTimeout(controlsTimer);
    controlsTimer = window.setTimeout(function () { root.classList.add("controls-dimmed"); }, 3000);
  }
  function hideGuide() { byId("led-guide").classList.add("hidden"); window.clearTimeout(guideTimer); }
  function updateGuideText() {
    if (!state) return;
    var dragHint = state.led.adaptiveMultiLine ? "" : "左右拖动字幕 · ";
    byId("led-guide-text").textContent = dragHint + (state.led.quickSwipeOpensQuickText ?
      "快速左滑快捷文本 · 上滑输入文字" : "上滑输入文字");
  }
  function handleBack() {
    if (locked) return;
    if (byId("led-reset-dialog").classList.contains("active")) { setDialogVisible(false); return; }
    if (activePanel !== "none") { closePanel(); return; }
    if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen();
  }
  function toggleFullscreen() {
    if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen();
    else if (document.documentElement.requestFullscreen) document.documentElement.requestFullscreen();
    else if (document.documentElement.webkitRequestFullscreen) document.documentElement.webkitRequestFullscreen();
  }
  function setDialogVisible(visible) {
    byId("led-reset-dialog").classList.toggle("active", visible);
    if (visible && tvRemote) tvRemote.focusPanel();
  }

  function applyDisplayColors() {
    if (!state) return;
    var background = state.led.background || "#000000";
    var roles = state.darkTheme ? state.themeDark : state.themeLight;
    root.style.background = background;
    root.style.setProperty("--led-control", contrastColor(background));
    root.style.setProperty("--led-accent", roles && roles.primary ? roles.primary : (state.themeColor || "#038387"));
  }
  function contrastColor(hex) {
    var value = String(hex).replace("#", "");
    var r = parseInt(value.slice(0,2),16), g = parseInt(value.slice(2,4),16), b = parseInt(value.slice(4,6),16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? "#000000" : "#ffffff";
  }

  function updateScreenEffects() {
    if (!state) return;
    var led = state.led;
    root.classList.toggle("led-brightness-filter", !led.followSystemBrightness);
    root.style.setProperty("--led-brightness", led.followSystemBrightness ? 1 : led.screenBrightness);
    updateWakeLock();
  }
  function updateWakeLock() {
    if (!state || !state.led.keepScreenOn || document.hidden || !navigator.wakeLock) {
      if (wakeLock) { wakeLock.release(); wakeLock = null; }
      wakeLockPending = false;
      return;
    }
    if (!wakeLock && !wakeLockPending) {
      wakeLockPending = true;
      navigator.wakeLock.request("screen").then(function (lock) {
        wakeLock = lock;
        wakeLockPending = false;
        lock.addEventListener("release", function () { if (wakeLock === lock) wakeLock = null; });
      }).catch(function () { wakeLockPending = false; });
    }
  }
  function setAudioEnabled(enabled) { audioEnabled = enabled; if (byId("led-web-audio")) byId("led-web-audio").checked = enabled; }

  window.KigttsLedDisplay = { init: init, applyState: applyState, setAudioEnabled: setAudioEnabled };
})();
