(function () {
  "use strict";

  function createRenderer(canvas, stage, target) {
    var state = null;
    var surface = null;
    var surfaceKey = "";
    var marqueeX = 0;
    var lastFrameAt = 0;
    var dragActive = false;
    var inertiaVelocity = 0;
    var localPreview = null;
    var localCursor = 0;

    function displayText(value) {
      return value.previewActive && value.inputText ? value.inputText : (value.text || "");
    }

    function applyState(next, animateText) {
      var previous = state ? displayText(state) : "";
      state = next;
      applyRotation();
      renderMode();
      if (previous !== displayText(next) || animateText) {
        surfaceKey = "";
        animate(canvas);
        animate(target);
      }
    }

    function setLocalPreview(text, cursor) {
      localPreview = text;
      localCursor = Math.max(0, Math.min(Number(cursor) || 0, text.length));
      renderMode();
    }

    function clearLocalPreview() {
      localPreview = null;
      localCursor = 0;
      renderMode();
    }

    function renderMode() {
      if (!state) return;
      var editing = localPreview !== null && localPreview.length > 0;
      var adaptive = editing || state.previewActive;
      canvas.style.display = adaptive ? "none" : "block";
      stage.style.display = adaptive ? "flex" : "none";
      if (adaptive) {
        renderAdaptive(editing ? localPreview : displayText(state), editing ? localCursor : null);
      } else {
        resize();
      }
    }

    function renderAdaptive(text, cursor) {
      target.textContent = "";
      if (cursor === null) {
        target.textContent = text || " ";
      } else {
        target.appendChild(document.createTextNode(text.slice(0, cursor)));
        var caret = document.createElement("span");
        caret.className = "led-preview-cursor";
        target.appendChild(caret);
        target.appendChild(document.createTextNode(text.slice(cursor)));
      }
      target.style.color = state.led.color || "#ffffff";
      target.style.fontWeight = state.bold ? String(state.fontWeight || 700) : String(state.fontWeight || 400);
      target.style.textAlign = state.centered ? "center" : "left";
      fitAdaptive();
    }

    function fitAdaptive() {
      if (stage.style.display === "none") return;
      var style = getComputedStyle(stage);
      var availableHeight = Math.max(
        1,
        stage.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
      );
      var low = 18;
      var heightFraction = Number(state.led && state.led.displayHeightFraction) || 0.72;
      var high = Math.max(32, Math.min(800, availableHeight * heightFraction));
      if (state.autoFit === false) high = Math.min(high, state.fontSizeSp || 56);
      for (var i = 0; i < 11; i++) {
        var mid = (low + high) / 2;
        target.style.fontSize = mid + "px";
        if (target.scrollHeight <= availableHeight && target.scrollWidth <= target.clientWidth + 1) low = mid;
        else high = mid;
      }
      target.style.fontSize = Math.max(18, low - 0.5) + "px";
    }

    function applyRotation() {
      var transform = state && state.rotated180 ? "rotate(180deg)" : "none";
      canvas.style.transform = transform;
      stage.style.transform = transform;
    }

    function resize() {
      var ratio = Math.min(window.devicePixelRatio || 1, 2);
      var width = Math.max(1, window.innerWidth);
      var height = Math.max(1, window.innerHeight);
      if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        canvas.style.width = width + "px";
        canvas.style.height = height + "px";
        surfaceKey = "";
      }
      if (stage.style.display !== "none") fitAdaptive();
    }

    function buildSurface(text, height) {
      var led = state.led || {};
      var fontSize = Math.max(40, height * (Number(led.displayHeightFraction) || 0.72));
      var font = (state.bold ? "700 " : "400 ") + fontSize + 'px "KIGTTS Web Font", sans-serif';
      var measure = document.createElement("canvas").getContext("2d");
      measure.font = font;
      var padding = fontSize * 0.14;
      var totalWidth = Math.max(1, Math.ceil(measure.measureText(text).width + padding * 2));
      var chunks = [];
      var chunkSize = 3072;
      for (var start = 0; start < totalWidth; start += chunkSize) {
        chunks.push(buildChunk(text, font, height, padding, start, Math.min(chunkSize, totalWidth - start), led));
      }
      return { width: totalWidth, height: Math.ceil(height), chunks: chunks, lineHeight: fontSize * 1.15 };
    }

    function buildAdaptiveSurface(text, width, height) {
      var led = state.led || {};
      var padding = Math.max(8, Math.min(width, height) * 0.04);
      var availableWidth = Math.max(1, width - padding * 2);
      var availableHeight = Math.max(1, height - padding * 2);
      var low = 6;
      var high = Math.max(12, Math.min(800, availableHeight * (Number(led.displayHeightFraction) || 0.72) * 0.78));
      var layout = null;
      for (var i = 0; i < 12; i++) {
        var middle = (low + high) / 2;
        var candidate = adaptiveLayout(text, middle, availableWidth);
        if (candidate.height <= availableHeight) { low = middle; layout = candidate; }
        else high = middle;
      }
      layout = adaptiveLayout(text, low, availableWidth);
      var alphaCanvas = document.createElement("canvas");
      alphaCanvas.width = Math.max(1, Math.ceil(width));
      alphaCanvas.height = Math.max(1, Math.ceil(height));
      var alpha = alphaCanvas.getContext("2d");
      alpha.font = layout.font;
      alpha.textBaseline = "middle";
      alpha.fillStyle = "#fff";
      var alignment = Number(led.shortTextAlignment);
      alpha.textAlign = alignment === 0 ? "left" : (alignment === 2 ? "right" : "center");
      var x = alignment === 0 ? padding : (alignment === 2 ? width - padding : width / 2);
      var blockTop = (height - layout.height) / 2;
      for (var line = 0; line < layout.lines.length; line++) {
        alpha.fillText(layout.lines[line], x, blockTop + layout.lineHeight * (line + 0.5));
      }
      return {
        width: width,
        height: height,
        chunks: [{ x: 0, canvas: colorizeAlpha(alphaCanvas, led, layout.lineHeight) }],
        lineHeight: layout.lineHeight
      };
    }

    function adaptiveLayout(text, fontSize, maxWidth) {
      var font = (state.bold ? "700 " : String(state.fontWeight || 400) + " ") +
        fontSize + 'px "KIGTTS Web Font", sans-serif';
      var measure = document.createElement("canvas").getContext("2d");
      measure.font = font;
      var lines = [];
      String(text || " ").replace(/\r\n?/g, "\n").split("\n").forEach(function (paragraph) {
        var current = "";
        Array.from(paragraph || " ").forEach(function (character) {
          var candidate = current + character;
          if (current && measure.measureText(candidate).width > maxWidth) {
            lines.push(current); current = character;
          } else current = candidate;
        });
        lines.push(current || " ");
      });
      var lineHeight = fontSize * 1.15;
      return { font: font, lines: lines, lineHeight: lineHeight, height: lines.length * lineHeight };
    }

    function buildChunk(text, font, height, padding, start, width, led) {
      var alphaCanvas = document.createElement("canvas");
      alphaCanvas.width = Math.max(1, width);
      alphaCanvas.height = Math.max(1, Math.ceil(height));
      var alpha = alphaCanvas.getContext("2d");
      alpha.font = font;
      alpha.textBaseline = "middle";
      alpha.fillStyle = "#fff";
      alpha.fillText(text, padding - start, height / 2);
      var fontSize = Number((/([0-9.]+)px/.exec(font) || [0, 40])[1]);
      return { x: start, canvas: colorizeAlpha(alphaCanvas, led, fontSize * 1.15) };
    }

    function colorizeAlpha(alphaCanvas, led, lineHeight) {
      var alpha = alphaCanvas.getContext("2d");
      if (!led.dotMatrix) {
        alpha.globalCompositeOperation = "source-in";
        alpha.fillStyle = led.color || "#ffffff";
        alpha.fillRect(0, 0, alphaCanvas.width, alphaCanvas.height);
        return alphaCanvas;
      }
      var result = document.createElement("canvas");
      result.width = alphaCanvas.width;
      result.height = alphaCanvas.height;
      var out = result.getContext("2d");
      var pixels = alpha.getImageData(0, 0, alphaCanvas.width, alphaCanvas.height).data;
      var rows = Math.max(8, Math.min(256, Number(led.dotRowsPerLine) || 24));
      var pitch = Math.max(2, Number(lineHeight) / rows);
      var sizeFraction = Math.max(0.1, Math.min(1, Number(led.dotSizeFraction) || 0.58));
      var dot = pitch * sizeFraction * (Number(led.dotShape) === 1 ? 1 : Math.SQRT2);
      out.fillStyle = led.color || "#ffffff";
      for (var y = pitch / 2; y < result.height; y += pitch) {
        for (var x = pitch / 2; x < result.width; x += pitch) {
          var px = (Math.floor(y) * result.width + Math.floor(x)) * 4 + 3;
          if (pixels[px] < 72) continue;
          if (Number(led.dotShape) === 1) out.fillRect(x - dot / 2, y - dot / 2, dot, dot);
          else { out.beginPath(); out.arc(x, y, dot / 2, 0, Math.PI * 2); out.fill(); }
        }
      }
      return result;
    }

    function drawSurface(ctx, value, left, top, viewportWidth) {
      for (var i = 0; i < value.chunks.length; i++) {
        var chunk = value.chunks[i];
        var x = left + chunk.x;
        if (x > viewportWidth || x + chunk.canvas.width < 0) continue;
        ctx.drawImage(chunk.canvas, x, top);
      }
    }

    function drawFrame(timestamp) {
      requestAnimationFrame(drawFrame);
      if (!state || localPreview !== null || state.previewActive) return;
      resize();
      var ratio = Math.min(window.devicePixelRatio || 1, 2);
      var width = canvas.width / ratio;
      var height = canvas.height / ratio;
      var text = displayText(state) || " ";
      var led = state.led || {};
      var key = [text, width, height, led.color, led.dotMatrix, led.dotShape, led.dotRowsPerLine,
        led.dotSizeFraction, led.displayHeightFraction, led.adaptiveMultiLine, led.shortTextAlignment,
        led.loopGap, state.bold, state.fontWeight].join("|");
      if (key !== surfaceKey) {
        surfaceKey = key;
        surface = led.adaptiveMultiLine ? buildAdaptiveSurface(text, width, height) : buildSurface(text, height);
        marqueeX = 0;
        inertiaVelocity = 0;
        lastFrameAt = timestamp;
      }
      var ctx = canvas.getContext("2d");
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.fillStyle = led.background || "#000000";
      ctx.fillRect(0, 0, width, height);
      if (!surface) return;
      var y = (height - surface.height) / 2;
      ctx.shadowColor = led.glowEnabled ? (led.color || "#ffffff") : "transparent";
      ctx.shadowBlur = led.glowEnabled ? 4 + 22 * (Number(led.glowStrength) || 0) : 0;
      if (surface.width <= width) {
        var align = Number(led.shortTextAlignment);
        var fixedX = align === 0 ? 0 : (align === 2 ? width - surface.width : (width - surface.width) / 2);
        drawSurface(ctx, surface, fixedX, y, width);
        return;
      }
      var elapsed = Math.min(80, Math.max(0, timestamp - lastFrameAt)) / 1000;
      lastFrameAt = timestamp;
      if (!dragActive) {
        if (Math.abs(inertiaVelocity) > 5) {
          marqueeX += inertiaVelocity * elapsed;
          inertiaVelocity *= Math.exp(-4.2 * elapsed);
        } else {
          var speed = Math.max(12, Number(led.speed) || 72);
          marqueeX += (Number(led.direction) === 1 ? speed : -speed) * elapsed;
        }
      }
      var gap = Math.max(24, Number(led.loopGap) || 96);
      var cycle = surface.width + gap;
      wrapMarquee(cycle);
      var copyX = marqueeX;
      while (copyX > 0) copyX -= cycle;
      while (copyX + surface.width < 0) copyX += cycle;
      while (copyX < width) {
        drawSurface(ctx, surface, copyX, y, width);
        copyX += cycle;
      }
    }

    function wrapMarquee(cycle) {
      if (!surface) return;
      while (marqueeX <= -cycle) marqueeX += cycle;
      while (marqueeX > 0) marqueeX -= cycle;
    }

    function beginDrag() { dragActive = true; inertiaVelocity = 0; }
    function dragBy(dx) { if (dragActive && surface) marqueeX += dx; }
    function endDrag(velocityX) { dragActive = false; inertiaVelocity = Number(velocityX) || 0; }
    function cancelDrag() { dragActive = false; inertiaVelocity = 0; }
    function animate(element) {
      element.classList.remove("text-changing");
      void element.offsetWidth;
      element.classList.add("text-changing");
    }

    window.addEventListener("resize", resize);
    requestAnimationFrame(drawFrame);
    return {
      applyState: applyState,
      setLocalPreview: setLocalPreview,
      clearLocalPreview: clearLocalPreview,
      beginDrag: beginDrag,
      dragBy: dragBy,
      endDrag: endDrag,
      cancelDrag: cancelDrag,
      resize: resize
    };
  }

  window.KigttsLedRenderer = { create: createRenderer };
})();
