(function () {
  "use strict";

  var api = null;
  var state = null;
  var selectedGroupId = null;
  var themeMode = readCookie("kigtts_remote_theme") || "system";
  var compactQuickTextPreference = readCookie("kigtts_remote_compact_quick_text");
  var settingsTimer = 0;
  var quickCollapsed = false;
  var localInputPreview = "";
  var systemTheme = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;
  function byId(id) { return document.getElementById(id); }
  function all(selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); }
  function init(options) {
    api = options;
    bindNavigation();
    bindSubtitleControls();
    bindSettings();
    bindViewport();
    window.KigttsRemoteQuickDialog.init(options);
    if (systemTheme) {
      var listener = function () { if (themeMode === "system") applyRemoteTheme(); };
      if (systemTheme.addEventListener) systemTheme.addEventListener("change", listener);
      else if (systemTheme.addListener) systemTheme.addListener(listener);
    }
    applyRemoteTheme();
  }
  function bindNavigation() {
    byId("remote-menu-button").onclick = function () { setDrawer(true); };
    byId("remote-drawer-scrim").onclick = function () { setDrawer(false); };
    all("[data-remote-page]").forEach(function (button) {
      button.onclick = function () {
        showPage(button.getAttribute("data-remote-page"));
        setDrawer(false);
      };
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && byId("remote-drawer").classList.contains("open")) {
        event.preventDefault();
        setDrawer(false);
      } else if (event.key === "ContextMenu" || event.key === "Menu") {
        event.preventDefault();
        setDrawer(true);
      }
    });
  }
  function setDrawer(open) {
    byId("remote-drawer").classList.toggle("open", open);
    byId("remote-drawer-scrim").classList.toggle("open", open);
    if (open) {
      var active = document.querySelector(".drawer-item.active");
      if (active) active.focus();
    } else {
      byId("remote-menu-button").focus();
    }
  }
  function showPage(page) {
    var settings = page === "settings";
    byId("remote-control-page").classList.toggle("active", !settings);
    byId("remote-settings-page").classList.toggle("active", settings);
    byId("remote-page-title").textContent = settings ? "设置" : "字幕控制";
    all("[data-remote-page]").forEach(function (button) {
      button.classList.toggle("active", button.getAttribute("data-remote-page") === page);
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  function bindSubtitleControls() {
    byId("send-button").onclick = function () { submitInput(state ? state.playOnSend !== false : true); };
    byId("replay-button").onclick = function () { api.sendCommand("replay", { text: state ? state.text : "" }); };
    byId("clear-button").onclick = function () { api.sendCommand("clear"); };
    byId("remote-input-replay").onclick = function () { api.sendCommand("replay", { text: state ? state.text : "" }); };
    byId("open-app-button").onclick = function () { api.sendCommand("openApp"); };
    byId("remote-play-on-send").onclick = function () {
      var enabled = !(state && state.playOnSend);
      if (state) state.playOnSend = enabled;
      renderPlayOnSend();
      api.sendCommand("playOnSend", { enabled: enabled });
    };
    byId("remote-bold").onclick = function () { updateQuickStyle({ bold: !(state && state.bold) }); };
    byId("remote-align").onclick = function () { updateQuickStyle({ centered: !(state && state.centered) }); };
    byId("remote-rotate").onclick = function () { updateQuickStyle({ rotated180: !(state && state.rotated180) }); };
    byId("remote-cursor-left").onclick = function () { moveInputCursor(-1); };
    byId("remote-cursor-right").onclick = function () { moveInputCursor(1); };
    byId("remote-input-clear").onclick = function () {
      var input = byId("subtitle-input");
      input.value = "";
      input.focus();
      updateInputState();
    };
    byId("remote-quick-toggle").onclick = function () {
      quickCollapsed = !quickCollapsed;
      renderQuickVisibility();
    };
    byId("compact-group-previous").onclick = function () { selectAdjacentGroup(-1); };
    byId("compact-group-next").onclick = function () { selectAdjacentGroup(1); };
    var input = byId("subtitle-input");
    input.addEventListener("input", updateInputState);
    input.addEventListener("focus", updateInputState);
    input.addEventListener("blur", function () {
      localInputPreview = "";
      renderSubtitle(false);
    });
    input.addEventListener("keyup", renderInputCursor);
    input.addEventListener("click", renderInputCursor);
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        submitInput(state ? state.playOnSend !== false : true);
      }
    });
    updateInputState();
  }
  function submitInput(playVoice) {
    var input = byId("subtitle-input");
    var text = input.value.trim();
    if (!text) {
      api.showSnackbar("请输入字幕内容");
      input.focus();
      return;
    }
    api.sendCommand("submit", { text: text, playVoice: playVoice });
    input.value = "";
    localInputPreview = "";
    updateInputState();
  }
  function moveInputCursor(delta) {
    var input = byId("subtitle-input");
    var current = delta < 0 ? input.selectionStart : input.selectionEnd;
    var target = Math.max(0, Math.min(input.value.length, Number(current || 0) + delta));
    input.focus();
    input.setSelectionRange(target, target);
    updateInputState();
  }
  function updateInputState() {
    var input = byId("subtitle-input");
    var hasText = input.value.length > 0;
    byId("remote-input-clear").hidden = !hasText;
    byId("send-button").disabled = !input.value.trim();
    localInputPreview = document.activeElement === input && hasText ? input.value : "";
    renderSubtitle(false);
  }
  function renderInputCursor() {
    if (document.activeElement === byId("subtitle-input") && localInputPreview) renderSubtitle(false);
  }

  function updateQuickStyle(change) {
    if (!state) return;
    Object.keys(change).forEach(function (key) { state[key] = change[key]; });
    renderSubtitle(false);
    api.sendCommand("quickStyle", {
      bold: state.bold === true,
      centered: state.centered === true,
      rotated180: state.rotated180 === true,
      fontSizeSp: Number(state.fontSizeSp) || 56
    });
  }
  function bindSettings() {
    all("[data-theme-mode]").forEach(function (button) {
      button.onclick = function () {
        themeMode = button.getAttribute("data-theme-mode");
        writeCookie("kigtts_remote_theme", themeMode);
        applyRemoteTheme();
      };
    });
    byId("remote-compact-quick-text").onchange = function () {
      compactQuickTextPreference = this.checked ? "1" : "0";
      writeCookie("kigtts_remote_compact_quick_text", compactQuickTextPreference);
      renderQuickGroups();
    };
    all("[data-audio-output]").forEach(function (button) {
      button.onclick = function () {
        api.sendCommand("audioOutputMode", { mode: Number(button.getAttribute("data-audio-output")) });
      };
    });
    all("[data-cast-setting] button").forEach(function (button) {
      button.onclick = function () {
        if (!state) return;
        state.led[button.parentElement.getAttribute("data-cast-setting")] = Number(button.getAttribute("data-value"));
        renderCastSettings();
        scheduleCastSettings();
      };
    });
    [
      "remote-normal-font", "remote-adaptive-multiline", "remote-led-color", "remote-led-background", "remote-density",
      "remote-glow-enabled", "remote-glow", "remote-height", "remote-speed",
      "remote-quick-swipe", "remote-gap", "remote-keep-awake",
      "remote-follow-brightness", "remote-brightness"
    ].forEach(function (id) {
      var control = byId(id);
      control.addEventListener(control.type === "range" ? "input" : "change", settingsChanged);
    });
    byId("remote-reset-display").onclick = function () { api.sendCommand("resetLedSettings"); };
  }

  function settingsChanged() {
    if (!state) return;
    var led = state.led;
    led.dotMatrix = !byId("remote-normal-font").checked;
    led.color = byId("remote-led-color").value;
    led.background = byId("remote-led-background").value;
    led.dotDensity = Number(byId("remote-density").value);
    led.glowEnabled = byId("remote-glow-enabled").checked;
    led.glowStrength = Number(byId("remote-glow").value);
    led.displayHeightFraction = Number(byId("remote-height").value);
    led.adaptiveMultiLine = byId("remote-adaptive-multiline").checked;
    led.speed = Number(byId("remote-speed").value);
    led.quickSwipeOpensQuickText = byId("remote-quick-swipe").checked;
    led.loopGap = Number(byId("remote-gap").value);
    led.keepScreenOn = byId("remote-keep-awake").checked;
    led.followSystemBrightness = byId("remote-follow-brightness").checked;
    led.screenBrightness = Number(byId("remote-brightness").value);
    renderCastSettings();
    scheduleCastSettings();
  }

  function scheduleCastSettings() {
    window.clearTimeout(settingsTimer);
    var settings = Object.assign({}, state.led);
    settingsTimer = window.setTimeout(function () {
      api.sendCommand("ledSettings", { settings: settings });
    }, 100);
  }

  function applyState(next, textChanged) {
    state = next;
    if (!compactQuickTextPreference) {
      compactQuickTextPreference = next.compactQuickText === true ? "1" : "0";
      writeCookie("kigtts_remote_compact_quick_text", compactQuickTextPreference);
    }
    byId("remote-compact-quick-text").checked = compactQuickTextPreference === "1";
    if (selectedGroupId === null || !findGroup(next.groups || [], selectedGroupId)) {
      selectedGroupId = next.selectedGroupId || (next.groups && next.groups[0] ? next.groups[0].id : null);
    }
    window.KigttsRemoteQuickDialog.applyState(next);
    applyRemoteTheme();
    renderSubtitle(textChanged);
    renderQuickGroups();
    renderQuickVisibility();
    renderPlayOnSend();
    renderCastSettings();
    renderSegments();
    var input = byId("subtitle-input");
    if (document.activeElement !== input) {
      input.value = next.inputText || "";
      updateInputState();
    }
  }

  function renderSubtitle(animate) {
    if (!state) return;
    var preview = byId("now-showing");
    var input = byId("subtitle-input");
    var text = localInputPreview || displayText(state) || "等待字幕";
    preview.textContent = "";
    if (localInputPreview) {
      var cursor = Math.max(0, Math.min(text.length, Number(input.selectionStart || 0)));
      preview.appendChild(document.createTextNode(text.slice(0, cursor)));
      var caret = document.createElement("span");
      caret.className = "remote-preview-cursor";
      caret.setAttribute("aria-hidden", "true");
      preview.appendChild(caret);
      preview.appendChild(document.createTextNode(text.slice(cursor)));
    } else {
      preview.textContent = text;
    }
    preview.classList.toggle("centered", state.centered === true);
    preview.classList.toggle("rotated", state.rotated180 === true);
    preview.style.fontWeight = state.bold ? "700" : String(state.fontWeight || 400);
    byId("remote-bold").classList.toggle("active", state.bold === true);
    byId("remote-align").classList.toggle("active", state.centered === true);
    byId("remote-align").querySelector(".material-symbols").textContent =
      state.centered ? "format_align_center" : "format_align_left";
    byId("remote-rotate").classList.toggle("active", state.rotated180 === true);
    fitSubtitle();
    if (animate) {
      preview.classList.remove("changing");
      void preview.offsetWidth;
      preview.classList.add("changing");
    }
  }

  function fitSubtitle() {
    if (!state) return;
    var preview = byId("now-showing");
    var high = Math.max(28, Math.min(160, Number(state.fontSizeSp) || 56));
    if (state.autoFit === false) {
      preview.style.fontSize = high + "px";
      return;
    }
    var low = 18;
    for (var i = 0; i < 9; i++) {
      var mid = (low + high) / 2;
      preview.style.fontSize = mid + "px";
      if (preview.scrollHeight <= preview.clientHeight && preview.scrollWidth <= preview.clientWidth) low = mid;
      else high = mid;
    }
    preview.style.fontSize = Math.max(18, low - .5) + "px";
  }

  function renderQuickGroups() {
    var groups = state.groups || [];
    var tabs = byId("group-tabs");
    var items = byId("quick-items");
    tabs.innerHTML = "";
    items.innerHTML = "";
    if (!groups.length) {
      items.innerHTML = '<div class="remote-subtitle-empty">暂无快捷文本</div>';
      return;
    }
    var selected = findGroup(groups, selectedGroupId) || groups[0];
    selectedGroupId = selected.id;
    var quickCard = document.querySelector(".quick-card");
    quickCard.classList.toggle("compact", compactQuickTextPreference === "1");
    byId("compact-group-current").firstElementChild.textContent = selected.icon || "label";
    byId("compact-group-title").textContent = selected.title || "未命名分组";
    byId("compact-group-current").title = selected.title || "未命名分组";
    byId("compact-group-current").setAttribute("aria-label", "当前分组：" + (selected.title || "未命名分组"));
    groups.forEach(function (group) {
      var button = document.createElement("button");
      button.className = "group-tab" + (String(group.id) === String(selected.id) ? " active" : "");
      button.innerHTML = '<span class="material-symbols"></span><span></span>';
      button.firstElementChild.textContent = group.icon || "label";
      button.lastElementChild.textContent = group.title || "未命名分组";
      button.onclick = function () {
        selectedGroupId = group.id;
        api.sendCommand("selectGroup", { groupId: group.id });
        renderQuickGroups();
      };
      tabs.appendChild(button);
    });
    (selected.items || []).forEach(function (text) {
      var button = document.createElement("button");
      button.className = "quick-item";
      button.dataset.quickGroupId = selected.id;
      var label = document.createElement("span");
      label.textContent = text;
      button.appendChild(label);
      button.onclick = function () { api.sendCommand("submit", { text: text, playVoice: state.playOnSend !== false }); };
      items.appendChild(button);
    });
    var add = document.createElement("button");
    add.className = "quick-item quick-add-item";
    add.innerHTML = '<span class="material-symbols">add</span>';
    add.title = "添加当前字幕";
    add.setAttribute("aria-label", "添加当前字幕");
    add.onclick = function () { api.sendCommand("addCurrentText", { groupId: selected.id }); };
    items.appendChild(add);
    items.classList.remove("switching");
    void items.offsetWidth;
    items.classList.add("switching");
    renderResponsiveGroupSelector();
  }

  function selectAdjacentGroup(delta) {
    if (!state || !state.groups || !state.groups.length) return;
    var groups = state.groups;
    var index = groups.findIndex(function (group) { return String(group.id) === String(selectedGroupId); });
    if (index < 0) index = 0;
    var target = (index + delta + groups.length) % groups.length;
    selectedGroupId = groups[target].id;
    api.sendCommand("selectGroup", { groupId: selectedGroupId });
    renderQuickGroups();
  }

  function renderQuickVisibility() {
    var card = document.querySelector(".quick-card");
    var button = byId("remote-quick-toggle");
    card.classList.toggle("collapsed", quickCollapsed);
    button.classList.toggle("active", !quickCollapsed);
    button.querySelector(".material-symbols").textContent = quickCollapsed ? "subtitles_off" : "subtitles";
    button.title = quickCollapsed ? "展开快捷文本" : "收起快捷文本";
    button.setAttribute("aria-label", button.title);
  }

  function renderResponsiveGroupSelector() {
    var horizontal = window.matchMedia && window.matchMedia("(orientation: landscape) and (max-height: 600px)").matches;
    byId("compact-group-previous").querySelector(".material-symbols").textContent = horizontal ? "chevron_left" : "keyboard_arrow_up";
    byId("compact-group-next").querySelector(".material-symbols").textContent = horizontal ? "chevron_right" : "keyboard_arrow_down";
  }

  function bindViewport() {
    var update = function () {
      var viewport = window.visualViewport;
      var inset = viewport ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0;
      document.documentElement.style.setProperty("--remote-keyboard-inset", Math.round(inset) + "px");
      document.documentElement.style.setProperty("--remote-viewport-height", Math.round(viewport ? viewport.height : window.innerHeight) + "px");
      renderResponsiveGroupSelector();
      fitSubtitle();
    };
    window.addEventListener("resize", update);
    window.addEventListener("orientationchange", update);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", update);
      window.visualViewport.addEventListener("scroll", update);
    }
    update();
  }

  function renderPlayOnSend() {
    if (!state) return;
    var button = byId("remote-play-on-send");
    var enabled = state.playOnSend !== false;
    button.classList.toggle("active", enabled);
    button.querySelector(".material-symbols").textContent = enabled ? "volume_up" : "volume_off";
  }

  function renderSegments() {
    all("[data-audio-output]").forEach(function (button) {
      button.classList.toggle("active", Number(button.getAttribute("data-audio-output")) === Number(state.audioOutputMode));
    });
  }

  function renderCastSettings() {
    if (!state || !state.led) return;
    var led = state.led;
    byId("remote-normal-font").checked = !led.dotMatrix;
    byId("remote-led-color").value = led.color || "#ffffff";
    byId("remote-led-background").value = led.background || "#000000";
    byId("remote-density").value = led.dotDensity;
    byId("remote-glow-enabled").checked = led.glowEnabled;
    byId("remote-glow").value = led.glowStrength;
    byId("remote-height").value = led.displayHeightFraction;
    byId("remote-adaptive-multiline").checked = led.adaptiveMultiLine === true;
    byId("remote-speed").value = led.speed;
    byId("remote-quick-swipe").checked = led.quickSwipeOpensQuickText;
    byId("remote-gap").value = led.loopGap;
    byId("remote-keep-awake").checked = led.keepScreenOn;
    byId("remote-follow-brightness").checked = led.followSystemBrightness;
    byId("remote-brightness").value = led.screenBrightness;
    byId("remote-dot-options").hidden = !led.dotMatrix;
    byId("remote-glow-row").hidden = !led.glowEnabled;
    byId("remote-scroll-options").hidden = led.adaptiveMultiLine === true;
    byId("remote-brightness-row").hidden = led.followSystemBrightness;
    byId("remote-density-value").textContent = Math.round(led.dotDensity * 100) + "%";
    byId("remote-glow-value").textContent = Math.round(led.glowStrength * 100) + "%";
    byId("remote-height-value").textContent = Math.round(led.displayHeightFraction * 100) + "%";
    byId("remote-speed-value").textContent = Math.round(led.speed) + " dp/s";
    byId("remote-gap-value").textContent = Math.round(led.loopGap) + " dp";
    byId("remote-brightness-value").textContent = Math.round(led.screenBrightness * 100) + "%";
    all("[data-cast-setting]").forEach(function (row) {
      var value = Number(led[row.getAttribute("data-cast-setting")]);
      Array.prototype.forEach.call(row.querySelectorAll("button"), function (button) {
        button.classList.toggle("active", Number(button.getAttribute("data-value")) === value);
      });
    });
  }

  function applyRemoteTheme() {
    var dark = themeMode === "dark" || (themeMode === "system" && systemTheme && systemTheme.matches);
    document.documentElement.setAttribute("data-remote-theme", dark ? "dark" : "light");
    all("[data-theme-mode]").forEach(function (button) {
      button.classList.toggle("active", button.getAttribute("data-theme-mode") === themeMode);
    });
    if (api && api.applyTheme) api.applyTheme(state || {}, dark);
  }

  function displayText(value) {
    return value.previewActive && value.inputText ? value.inputText : (value.text || "");
  }

  function findGroup(groups, id) {
    for (var i = 0; i < groups.length; i++) {
      if (String(groups[i].id) === String(id)) return groups[i];
    }
    return null;
  }

  function readCookie(name) {
    var prefix = name + "=";
    var parts = document.cookie.split(";");
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i].trim();
      if (part.indexOf(prefix) === 0) return decodeURIComponent(part.slice(prefix.length));
    }
    return "";
  }

  function writeCookie(name, value) {
    document.cookie = name + "=" + encodeURIComponent(value) + "; Max-Age=31536000; Path=/; SameSite=Lax";
  }

  window.KigttsRemoteController = {
    init: init,
    applyState: applyState
  };
})();
