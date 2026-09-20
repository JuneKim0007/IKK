import { BaseElement } from '../core/BaseElement.js';
import { create } from '../core/registry.js';
import { rel } from '../contract/schema.js';

const HANDLES = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];

/** The design surface. Owns hit-testing, drag, resize and selection. */
export class Canvas extends BaseElement {
  constructor(store) {
    super('canvas', { store });
    this.elements = new Map();   // node id -> NodeElement
    this.drag = null;
  }

  createElement() {
    const wrap = document.createElement('div');
    wrap.className = 'canvas-wrap';
    this.frame = document.createElement('div');
    this.frame.className = 'frame';
    wrap.appendChild(this.frame);
    this._bindPointer();
    return wrap;
  }

  render(store) {
    const seen = new Set();

    for (const node of store.nodes()) {
      seen.add(node.id);
      let el = this.elements.get(node.id);
      if (!el) {
        el = create(node.type, node.id, { store });
        el.mount(this.frame);
        this.elements.set(node.id, el);
      }
      el.render(node);
      el.el.classList.toggle('is-selected', node.id === store.selectedId);
    }

    // Drop elements whose nodes are gone. Without this, a delete leaves a
    // ghost that still hit-tests.
    for (const [id, el] of this.elements) {
      if (!seen.has(id)) { el.destroy(); this.elements.delete(id); }
    }

    this._renderHandles(store);
  }

  _renderHandles(store) {
    this.frame.querySelectorAll('.handle').forEach((h) => h.remove());
    const el = store.selectedId ? this.elements.get(store.selectedId) : null;
    if (!el?.el) return;
    for (const dir of HANDLES) {
      const h = document.createElement('span');
      h.className = `handle handle-${dir}`;
      h.dataset.dir = dir;
      el.el.appendChild(h);
    }
  }

  _bindPointer() {
    this.frame.addEventListener('pointerdown', (e) => {
      const store = this.store;
      const handle = e.target.closest('.handle');
      const nodeEl = e.target.closest('.node');

      if (!nodeEl) { store.select(null); return; }

      const id = nodeEl.dataset.elementId;
      if (store.selectedId !== id) store.select(id);

      const node = store.byId(id);
      if (!node) return;
      const box = this.frame.getBoundingClientRect();

      this.drag = {
        id,
        dir: handle?.dataset.dir ?? null,
        startX: e.clientX, startY: e.clientY,
        box,
        origin: { ...node.rect },
      };
      e.preventDefault();
      this.frame.setPointerCapture(e.pointerId);
    });

    this.frame.addEventListener('pointermove', (e) => {
      if (!this.drag) return;
      const d = this.drag;
      const dx = ((e.clientX - d.startX) / d.box.width) * 100;
      const dy = ((e.clientY - d.startY) / d.box.height) * 100;
      const o = d.origin;

      let { x, y, w, h } = o;
      if (!d.dir) {
        x = o.x + dx; y = o.y + dy;
      } else {
        if (d.dir.includes('e')) w = Math.max(1, o.w + dx);
        if (d.dir.includes('s')) h = Math.max(1, o.h + dy);
        if (d.dir.includes('w')) { w = Math.max(1, o.w - dx); x = o.x + (o.w - w); }
        if (d.dir.includes('n')) { h = Math.max(1, o.h - dy); y = o.y + (o.h - h); }
      }
      // markDirty via the store is the only mutation path.
      this.store.update(d.id, (n) => { n.rect = rel(x, y, w, h); });
    });

    const end = (e) => {
      if (!this.drag) return;
      this.frame.releasePointerCapture?.(e.pointerId);
      this.drag = null;
    };
    this.frame.addEventListener('pointerup', end);
    this.frame.addEventListener('pointercancel', end);

    this.frame.addEventListener('dblclick', (e) => {
      const nodeEl = e.target.closest('.node');
      if (!nodeEl) return;
      const id = nodeEl.dataset.elementId;
      const node = this.store.byId(id);
      if (!node || node.type === 'image') return;
      this.dispatchEditText?.(id);
    });
  }
}
