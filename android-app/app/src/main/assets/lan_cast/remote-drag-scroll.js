(function () {
  "use strict";

  var selector = ".subtitle-actions, .quick-items, .group-tabs, .quick-dialog-groups";
  var drag = null;
  var suppressClickUntil = 0;

  function scrollAxis(element) {
    var canX = element.scrollWidth > element.clientWidth + 2;
    var canY = element.scrollHeight > element.clientHeight + 2;
    if (canX && canY) {
      return getComputedStyle(element).flexDirection === "column" ? "y" : "x";
    }
    return canY ? "y" : canX ? "x" : null;
  }

  function scrollPosition(element, axis) {
    return axis === "x" ? element.scrollLeft : element.scrollTop;
  }

  function setScrollPosition(element, axis, value) {
    if (axis === "x") element.scrollLeft = value;
    else element.scrollTop = value;
  }

  function scrollLimit(element, axis) {
    return axis === "x" ? element.scrollWidth - element.clientWidth : element.scrollHeight - element.clientHeight;
  }

  document.addEventListener("wheel", function (event) {
    var element = event.target.closest(selector);
    if (!element) return;
    var axis = scrollAxis(element);
    if (!axis) return;
    var delta = axis === "x"
      ? (Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY)
      : (Math.abs(event.deltaY) > Math.abs(event.deltaX) ? event.deltaY : event.deltaX);
    var current = scrollPosition(element, axis);
    var limit = scrollLimit(element, axis);
    if ((delta < 0 && current <= 0) || (delta > 0 && current >= limit - 1)) return;
    event.preventDefault();
    setScrollPosition(element, axis, current + delta);
  }, { passive: false });

  document.addEventListener("pointerdown", function (event) {
    if (event.pointerType !== "mouse" || event.button !== 0) return;
    var element = event.target.closest(selector);
    if (!element) return;
    var axis = scrollAxis(element);
    if (!axis) return;
    drag = {
      element: element,
      axis: axis,
      pointerId: event.pointerId,
      start: axis === "x" ? event.clientX : event.clientY,
      scroll: scrollPosition(element, axis),
      moved: false
    };
  });

  document.addEventListener("pointermove", function (event) {
    if (!drag || drag.pointerId !== event.pointerId) return;
    var current = drag.axis === "x" ? event.clientX : event.clientY;
    var delta = current - drag.start;
    if (!drag.moved && Math.abs(delta) < 4) return;
    if (!drag.moved) {
      drag.moved = true;
      drag.element.classList.add("drag-scroll-active");
      try { drag.element.setPointerCapture(event.pointerId); } catch (_) {}
    }
    event.preventDefault();
    setScrollPosition(drag.element, drag.axis, drag.scroll - delta);
  });

  function finishDrag(event) {
    if (!drag || (event.pointerId !== undefined && drag.pointerId !== event.pointerId)) return;
    if (drag.moved) {
      suppressClickUntil = Date.now() + 250;
      drag.element.classList.remove("drag-scroll-active");
      try { drag.element.releasePointerCapture(drag.pointerId); } catch (_) {}
    }
    drag = null;
  }

  document.addEventListener("pointerup", finishDrag);
  document.addEventListener("pointercancel", finishDrag);
  window.addEventListener("blur", function () { if (drag) finishDrag({ pointerId: drag.pointerId }); });
  document.addEventListener("click", function (event) {
    if (Date.now() >= suppressClickUntil || !event.target.closest(selector)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);
})();
