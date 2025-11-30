// JSDom setup for Radix UI components

// PointerEvent - required for Radix UI interactions
class PointerEvent extends MouseEvent {
  constructor(type, props) {
    super(type, props);
    this.pointerId = props?.pointerId;
    this.pointerType = props?.pointerType || 'mouse';
    this.isPrimary = props?.isPrimary ?? true;
    this.width = props?.width ?? 1;
    this.height = props?.height ?? 1;
    this.pressure = props?.pressure ?? 0;
    this.tiltX = props?.tiltX ?? 0;
    this.tiltY = props?.tiltY ?? 0;
  }
}
global.PointerEvent = PointerEvent;

// ResizeObserver - used for positioning
global.ResizeObserver = class ResizeObserver {
  constructor(callback) {
    this.callback = callback;
  }
  observe() {}
  unobserve() {}
  disconnect() {}
};

// scrollIntoView - used for keyboard navigation
Element.prototype.scrollIntoView = function() {};

// Pointer capture methods - required for Radix UI
Element.prototype.hasPointerCapture = function() { return false; };
Element.prototype.setPointerCapture = function() {};
Element.prototype.releasePointerCapture = function() {};

// matchMedia - used for responsive behavior
window.matchMedia = window.matchMedia || function(query) {
  return {
    matches: false,
    media: query,
    onchange: null,
    addListener: function() {},
    removeListener: function() {},
    addEventListener: function() {},
    removeEventListener: function() {},
    dispatchEvent: function() { return true; }
  };
};

// DOMRect for getBoundingClientRect
class DOMRect {
  constructor(x = 0, y = 0, width = 0, height = 0) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.top = y;
    this.right = x + width;
    this.bottom = y + height;
    this.left = x;
  }
  toJSON() {
    return { x: this.x, y: this.y, width: this.width, height: this.height,
             top: this.top, right: this.right, bottom: this.bottom, left: this.left };
  }
}
global.DOMRect = DOMRect;

// Ensure elements have reasonable bounding rects
const originalGetBoundingClientRect = Element.prototype.getBoundingClientRect;
Element.prototype.getBoundingClientRect = function() {
  const rect = originalGetBoundingClientRect.call(this);
  if (rect.width === 0 && rect.height === 0) {
    return new DOMRect(0, 0, 100, 50);
  }
  return rect;
};
