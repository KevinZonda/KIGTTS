(function () {
  "use strict";

  var remotePage = location.pathname.indexOf("/remote") === 0;
  document.body.className = remotePage ? "remote-page" : "display-page";

  var socket = null;
  var reconnectDelay = 250;
  var reconnectTimer = 0;
  var heartbeatTimer = 0;
  var socketGeneration = 0;
  var lastSocketActivity = 0;
  var pageClosing = false;
  var audioContext = null;
  var audioEnabled = false;
  var audioRequested = !remotePage;
  var audioActivationRevision = 0;
  var nextAudioTime = 0;
  var fileSources = {};
  var currentState = {
    text: "点击下方快捷文本或输入文字",
    inputText: "",
    previewActive: false,
    compactQuickText: false,
    groups: [],
    led: {
      color: "#ffffff",
      background: "#000000",
      dotMatrix: false,
      dotShape: 0,
      dotRowsPerLine: 24,
      dotSizeFraction: 0.58,
      speed: 72,
      direction: 0,
      loopGap: 96,
      shortTextAlignment: 1,
      adaptiveMultiLine: true
    }
  };
  var snackbarTimer = 0;

  function byId(id) { return document.getElementById(id); }

  function connect(force) {
    if (pageClosing) return;
    if (navigator.onLine === false) {
      setConnectionLabel("等待网络连接");
      return;
    }
    if (!force && socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;
    window.clearTimeout(reconnectTimer);
    reconnectTimer = 0;
    var generation = ++socketGeneration;
    var previous = socket;
    if (previous) {
      previous.onopen = previous.onmessage = previous.onclose = previous.onerror = null;
      try { previous.close(); } catch (_) {}
    }
    var scheme = location.protocol === "https:" ? "wss:" : "ws:";
    var candidate = new WebSocket(scheme + "//" + location.host + "/ws");
    socket = candidate;
    candidate.binaryType = "arraybuffer";
    setConnectionLabel("正在连接");
    candidate.onopen = function () {
      if (socket !== candidate || generation !== socketGeneration) return;
      reconnectDelay = 250;
      lastSocketActivity = Date.now();
      sendRaw({ type: "hello", role: remotePage ? "remote" : "display", audioEnabled: audioEnabled });
      setConnectionLabel("已连接");
      startHeartbeat();
    };
    candidate.onmessage = function (event) {
      if (socket !== candidate || generation !== socketGeneration) return;
      lastSocketActivity = Date.now();
      if (typeof event.data === "string") handleMessage(event.data);
      else handlePcm(event.data);
    };
    candidate.onclose = function () {
      if (socket !== candidate || generation !== socketGeneration) return;
      socket = null;
      stopHeartbeat();
      if (pageClosing) return;
      setConnectionLabel(navigator.onLine === false ? "等待网络连接" : "连接已断开，正在重试");
      scheduleReconnect(false);
    };
    candidate.onerror = function () {
      if (socket !== candidate || generation !== socketGeneration) return;
      try { candidate.close(); } catch (_) { scheduleReconnect(false); }
    };
  }

  function scheduleReconnect(immediate) {
    if (pageClosing || reconnectTimer || navigator.onLine === false) return;
    var delay = immediate ? 80 : reconnectDelay + Math.floor(Math.random() * 180);
    reconnectTimer = window.setTimeout(function () {
      reconnectTimer = 0;
      connect(false);
    }, delay);
    if (!immediate) reconnectDelay = Math.min(reconnectDelay * 1.7, 2000);
  }

  function startHeartbeat() {
    stopHeartbeat();
    heartbeatTimer = window.setInterval(function () {
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        scheduleReconnect(true);
        return;
      }
      if (Date.now() - lastSocketActivity > 32000) {
        try { socket.close(); } catch (_) {}
        return;
      }
      sendRaw({ type: "ping", at: Date.now() });
    }, 10000);
  }

  function stopHeartbeat() {
    window.clearInterval(heartbeatTimer);
    heartbeatTimer = 0;
  }

  function recoverConnection() {
    if (pageClosing || document.visibilityState === "hidden" || navigator.onLine === false) return;
    if (socket && socket.readyState === WebSocket.OPEN) {
      sendRaw({ type: "ping", at: Date.now() });
    } else if (socket && socket.readyState === WebSocket.CONNECTING) {
      return;
    } else {
      connect(true);
    }
  }

  function sendRaw(payload) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    try {
      socket.send(JSON.stringify(payload));
      return true;
    } catch (_) {
      try { socket.close(); } catch (_) {}
      scheduleReconnect(true);
      return false;
    }
  }

  function sendCommand(type, extra) {
    var payload = Object.assign({}, extra || {});
    payload.type = type;
    payload.requestId = Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
    if (!sendRaw(payload)) showSnackbar("网页遥控器尚未连接");
  }

  function handleMessage(raw) {
    var message;
    try { message = JSON.parse(raw); } catch (_) { return; }
    if (message.type === "pong") {
      return;
    } else if (message.type === "state") {
      applyState(message);
    } else if (message.type === "ack" && message.ok === false) {
      showSnackbar(message.error || "操作失败");
    } else if (message.type === "audioStart") {
      nextAudioTime = audioContext ? audioContext.currentTime + 0.05 : 0;
    } else if (message.type === "audioFile") {
      playAudioFile(message);
    } else if (message.type === "audioFileEnd") {
      stopAudioFile(message.mediaId);
    }
  }

  function applyState(next) {
    var previousText = displayText(currentState);
    currentState = next;
    updateWebFont(next.fontUrl, next.fontWeight);
    if (remotePage && window.KigttsRemoteController) {
      window.KigttsRemoteController.applyState(next, previousText !== displayText(next));
    } else if (window.KigttsLedDisplay) {
      applyTheme(next, next.darkTheme === true);
      window.KigttsLedDisplay.applyState(next, previousText !== displayText(next));
    }
  }

  function displayText(state) {
    return state.previewActive && state.inputText ? state.inputText : (state.text || "");
  }

  function applyTheme(themeState, dark) {
    var roles = dark ? themeState.themeDark : themeState.themeLight;
    var primary = roles && roles.primary ? roles.primary : (themeState.themeColor || "#038387");
    var onPrimary = roles && roles.onPrimary ? roles.onPrimary : contrastingTextColor(primary);
    var accent = roles && roles.accentText ? roles.accentText : primary;
    var style = document.documentElement.style;
    style.setProperty("--primary", primary);
    style.setProperty("--on-primary", onPrimary);
    style.setProperty("--accent", accent);
    style.setProperty("--background", dark ? "#121212" : "#fafafa");
    style.setProperty("--surface", dark ? "#1e1e1e" : "#ffffff");
    style.setProperty("--on-surface", dark ? "#f1f3f4" : "#202124");
    style.setProperty("--muted", dark ? "#bdc1c6" : "#5f6368");
    style.setProperty("--divider", dark ? "rgba(255,255,255,.16)" : "rgba(0,0,0,.12)");
    style.setProperty("--field-border", dark ? "rgba(255,255,255,.38)" : "rgba(0,0,0,.38)");
    style.setProperty("--outlined-border", dark ? "rgba(255,255,255,.28)" : "rgba(0,0,0,.24)");
    style.setProperty("--shadow", dark ? "0 2px 5px rgba(0,0,0,.52)" : "0 2px 4px rgba(0,0,0,.09), 0 1px 2px rgba(0,0,0,.06)");
    document.documentElement.setAttribute("data-remote-theme", dark ? "dark" : "light");
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", primary);
  }

  function contrastingTextColor(hex) {
    var value = String(hex || "").replace("#", "");
    if (value.length !== 6) return "#ffffff";
    var rgb = [0, 2, 4].map(function (offset) {
      var channel = parseInt(value.slice(offset, offset + 2), 16) / 255;
      return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    });
    var luminance = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    return luminance > 0.42 ? "#151515" : "#ffffff";
  }

  var loadedFontUrl = "";
  function updateWebFont(url, weight) {
    if (!url || url === loadedFontUrl || !("FontFace" in window)) return;
    loadedFontUrl = url;
    var face = new FontFace("KIGTTS Web Font", "url(" + url + ")", { weight: "100 900" });
    face.load().then(function (loaded) {
      document.fonts.add(loaded);
      if (remotePage && window.KigttsRemoteController) {
        window.KigttsRemoteController.applyState(currentState, false);
      }
      else if (window.KigttsLedDisplay) window.KigttsLedDisplay.applyState(currentState, false);
    }).catch(function () { loadedFontUrl = ""; });
  }

  function setConnectionLabel(text) {
    var label = byId("connection-label");
    if (label) label.textContent = text;
  }

  function showSnackbar(text) {
    var bar = byId("snackbar");
    if (!bar) return;
    bar.textContent = text;
    bar.classList.add("visible");
    window.clearTimeout(snackbarTimer);
    snackbarTimer = window.setTimeout(function () { bar.classList.remove("visible"); }, 2200);
  }

  function setAudioEnabled(enabled, automatic) {
    if (remotePage) return;
    audioRequested = enabled;
    var revision = ++audioActivationRevision;
    if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioEnabled(enabled);
    if (!enabled) {
      audioEnabled = false;
      Object.keys(fileSources).forEach(stopAudioFile);
      if (audioContext && audioContext.state === "running") audioContext.suspend();
      sendRaw({ type: "audioReady", enabled: false });
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioEnabled(false);
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioActivationRequired(false);
      showSnackbar("投屏端音频已关闭");
      return;
    }
    var AudioCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtor) {
      showSnackbar("投屏端浏览器不支持音频");
      audioRequested = false;
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioEnabled(false);
      return;
    }
    var resumeRequest;
    try {
      if (!audioContext || audioContext.state === "closed") audioContext = new AudioCtor();
      resumeRequest = audioContext.resume();
    } catch (_) {
      audioEnabled = false;
      audioRequested = false;
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioEnabled(false);
      showSnackbar("投屏端浏览器无法启动音频");
      return;
    }
    Promise.resolve(resumeRequest).then(function () {
      if (revision !== audioActivationRevision) return;
      if (audioContext.state !== "running") throw new Error("AudioContext is not running");
      audioEnabled = true;
      nextAudioTime = audioContext.currentTime + 0.08;
      sendRaw({ type: "audioReady", enabled: true });
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioEnabled(true);
      if (window.KigttsLedDisplay) window.KigttsLedDisplay.setAudioActivationRequired(false);
      if (!automatic) showSnackbar("投屏端音频已开启");
    }).catch(function () {
      if (revision !== audioActivationRevision) return;
      audioEnabled = false;
      sendRaw({ type: "audioReady", enabled: false });
      if (window.KigttsLedDisplay) {
        window.KigttsLedDisplay.setAudioEnabled(audioRequested);
        window.KigttsLedDisplay.setAudioActivationRequired(audioRequested);
      }
      if (!automatic) showSnackbar("请确认启用投屏端音频");
    });
  }

  function handlePcm(arrayBuffer) {
    if (!audioEnabled || !audioContext || !arrayBuffer || arrayBuffer.byteLength < 16) return;
    if (audioContext.state === "suspended") Promise.resolve(audioContext.resume()).catch(function () {});
    var view = new DataView(arrayBuffer);
    if (view.getUint8(0) !== 75 || view.getUint8(1) !== 73 || view.getUint8(2) !== 71 || view.getUint8(3) !== 65) return;
    var sampleRate = view.getInt32(8, true);
    var count = Math.min(view.getInt32(12, true), (arrayBuffer.byteLength - 16) / 2);
    if (sampleRate <= 0 || count <= 0) return;
    var buffer = audioContext.createBuffer(1, count, sampleRate);
    var channel = buffer.getChannelData(0);
    for (var i = 0; i < count; i++) channel[i] = view.getInt16(16 + i * 2, true) / 32768;
    var source = audioContext.createBufferSource();
    source.buffer = buffer;
    source.connect(audioContext.destination);
    var now = audioContext.currentTime;
    if (nextAudioTime < now || nextAudioTime > now + 1.2) nextAudioTime = now + 0.045;
    source.start(nextAudioTime);
    nextAudioTime += buffer.duration;
  }

  function playAudioFile(message) {
    if (!audioEnabled || !audioContext) return;
    if (audioContext.state === "suspended") Promise.resolve(audioContext.resume()).catch(function () {});
    fetch(message.url).then(function (response) { return response.arrayBuffer(); }).then(function (data) {
      audioContext.decodeAudioData(data, function (buffer) {
        var source = audioContext.createBufferSource();
        var gain = audioContext.createGain();
        gain.gain.value = Number(message.gain) || 1;
        source.buffer = buffer;
        source.connect(gain);
        gain.connect(audioContext.destination);
        var offset = Math.max(0, Number(message.startMs) || 0) / 1000;
        var end = Math.max(0, Number(message.endMs) || 0) / 1000;
        var duration = end > offset ? Math.min(buffer.duration - offset, end - offset) : buffer.duration - offset;
        source.start(audioContext.currentTime + 0.03, offset, Math.max(0.01, duration));
        fileSources[String(message.mediaId)] = source;
      }, function () { showSnackbar("投屏端无法解码该音频文件"); });
    }).catch(function () { showSnackbar("投屏端音频加载失败"); });
  }

  function stopAudioFile(id) {
    var key = String(id);
    var source = fileSources[key];
    if (source) { try { source.stop(); } catch (_) {} delete fileSources[key]; }
  }

  function bindEvents() {
    if (remotePage && window.KigttsRemoteController) {
      window.KigttsRemoteController.init({
        sendCommand: sendCommand,
        showSnackbar: showSnackbar,
        applyTheme: applyTheme
      });
    } else if (window.KigttsLedDisplay) {
      window.KigttsLedDisplay.init({
        sendCommand: sendCommand,
        setAudioEnabled: setAudioEnabled,
        showSnackbar: showSnackbar
      });
    }
  }

  function createRipple(button, clientX, clientY) {
    if (!button || button.disabled) return;
    var rect = button.getBoundingClientRect();
    var x = clientX === null ? rect.width / 2 : clientX - rect.left;
    var y = clientY === null ? rect.height / 2 : clientY - rect.top;
    var radius = Math.hypot(Math.max(x, rect.width - x), Math.max(y, rect.height - y));
    var ripple = document.createElement("i");
    ripple.className = "mui-ripple";
    ripple.setAttribute("aria-hidden", "true");
    ripple.style.width = ripple.style.height = radius * 2 + "px";
    ripple.style.left = x - radius + "px";
    ripple.style.top = y - radius + "px";
    button.appendChild(ripple);
    ripple.addEventListener("animationend", function () { ripple.remove(); });
  }

  document.addEventListener("pointerdown", function (event) {
    var button = event.target.closest ? event.target.closest("button") : null;
    if (button) createRipple(button, event.clientX, event.clientY);
  });
  document.addEventListener("keydown", function (event) {
    if ((event.key === "Enter" || event.key === " ") && event.target.tagName === "BUTTON" && !event.repeat) {
      createRipple(event.target, null, null);
    }
  });

  window.addEventListener("online", function () {
    reconnectDelay = 250;
    recoverConnection();
  });
  window.addEventListener("offline", function () {
    setConnectionLabel("等待网络连接");
    stopHeartbeat();
    if (socket) try { socket.close(); } catch (_) {}
  });
  window.addEventListener("pageshow", function () {
    pageClosing = false;
    recoverConnection();
  });
  window.addEventListener("pagehide", function (event) {
    if (event.persisted) return;
    pageClosing = true;
    window.clearTimeout(reconnectTimer);
    stopHeartbeat();
    if (socket) {
      socket.onclose = null;
      try { socket.close(); } catch (_) {}
    }
  });
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") recoverConnection();
  });

  bindEvents();
  if (!remotePage && audioRequested) setAudioEnabled(true, true);
  connect(false);
})();
