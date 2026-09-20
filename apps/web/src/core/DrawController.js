/**
 * Drag-to-draw, once, for every drawable type.
 *
 * Every design tool works this way: press, drag out the bounds, release. Doing
 * it per-tool is how rect and text end up feeling different for no reason, so
 * the press/move/release machine lives here and the tools only supply a type.
 *
 * A press with no meaningful drag still creates a node at the type's default
 * size — dismissing a click as "too small" would make the tool feel broken.
 */
import { rel } from '../contract/schema.js';

const CLICK_THRESHOLD_PX = 4;

export class DrawController {
  /**
   * @param {HTMLElement} frame the canvas frame; drawing is in its coordinates
   * @param {{ getTool: () => string, onCreate: (type: string, rect: object) => void,
   *           onDone: () => void }} hooks
   */
  constructor(frame, hooks) {
    this.frame = frame;
    this.hooks = hooks;
    /** @type {null | { type: string, x0: number, y0: number, px0: number, py0: number }} */
    this.session = null;
    this.ghost = null;
    this._bind();
  }

  get active() { return this.session !== null; }

  _toPercent(clientX, clientY) {
    const box = this.frame.getBoundingClientRect();
    return {
      x: ((clientX - box.left) / box.width) * 100,
      y: ((clientY - box.top) / box.height) * 100,
    };
  }

  _bind() {
    // Capture phase: the draw tool must win over node hit-testing, otherwise
    // starting a drag on top of an existing node selects it instead.
    this.frame.addEventListener('pointerdown', (e) => {
      const type = this.hooks.getTool();
      if (!type || type === 'move') return;

      e.preventDefault();
      e.stopPropagation();

      const p = this._toPercent(e.clientX, e.clientY);
      this.session = { type, x0: p.x, y0: p.y, px0: e.clientX, py0: e.clientY };

      this.ghost = document.createElement('div');
      this.ghost.className = 'draw-ghost';
      if (type === 'ellipse') this.ghost.classList.add('is-ellipse');
      if (type === 'triangle') this.ghost.classList.add('is-triangle');
      if (type === 'line') this.ghost.classList.add('is-line');
      this.frame.appendChild(this.ghost);
      this._paintGhost(p.x, p.y, 0, 0);

      this.frame.setPointerCapture?.(e.pointerId);
    }, true);

    this.frame.addEventListener('pointermove', (e) => {
      if (!this.session) return;
      const r = this._rectFrom(e.clientX, e.clientY);
      this._paintGhost(r.x, r.y, r.w, r.h);
    }, true);

    const finish = (e) => {
      if (!this.session) return;
      const { type, px0, py0 } = this.session;
      const moved = Math.hypot(e.clientX - px0, e.clientY - py0) > CLICK_THRESHOLD_PX;
      const r = moved ? this._rectFrom(e.clientX, e.clientY) : null;

      this.ghost?.remove();
      this.ghost = null;
      this.session = null;
      this.frame.releasePointerCapture?.(e.pointerId);

      // null rect means "use the type's default size at this point".
      this.hooks.onCreate(type, r);
      this.hooks.onDone?.();
    };

    this.frame.addEventListener('pointerup', finish, true);
    this.frame.addEventListener('pointercancel', finish, true);
  }

  /** Normalised so dragging up or left works as well as down and right. */
  _rectFrom(clientX, clientY) {
    const p = this._toPercent(clientX, clientY);
    const { x0, y0 } = this.session;
    return rel(Math.min(x0, p.x), Math.min(y0, p.y),
               Math.max(0.1, Math.abs(p.x - x0)), Math.max(0.1, Math.abs(p.y - y0)));
  }

  _paintGhost(x, y, w, h) {
    if (!this.ghost) return;
    this.ghost.style.left = `${x}%`;
    this.ghost.style.top = `${y}%`;
    this.ghost.style.width = `${w}%`;
    this.ghost.style.height = `${h}%`;
  }
}
