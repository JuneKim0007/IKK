import { BaseElement } from '../core/BaseElement.js';
import { create } from '../core/registry.js';
import { rel, backgroundCss } from '../contract/schema.js';

const HANDLES = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];

/** The design surface. Owns hit-testing, drag, resize and selection. */
export class Canvas extends BaseElement {
  constructor(store) {
    super('canvas', { store });
    this.elements = new Map();   // node id -> NodeElement
    this.drag = null;
    /** Node currently being typed into, if any. @type {string|null} */
    this.editingId = null;
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
    // Absent background means absent, not white: the frame keeps its own
    // checkerboard-free neutral so an undefined surface is visibly undefined.
    const surface = backgroundCss(store.contract.background);
    this.frame.style.background = surface ?? '';
    this.frame.classList.toggle('no-surface', !surface);

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
      el.el.classList.toggle('is-editing', node.id === this.editingId);
      if (node.id === this.editingId) this._attachEditor(store, node, el);
    }

    // Drop elements whose nodes are gone. Without this, a delete leaves a
    // ghost that still hit-tests.
    for (const [id, el] of this.elements) {
      if (!seen.has(id)) { el.destroy(); this.elements.delete(id); }
    }

    this._renderHandles(store);
  }

  /**
   * Turns the node's text span into a caret. Editing in place rather than in a
   * side panel is the difference between a design tool and a form, and it is
   * also why the keymap has to go quiet: this span is contenteditable, so a
   * bare "r" here must type an r, not select the rectangle tool.
   */
  _attachEditor(store, node, el) {
    const span = el.el.querySelector('.node-text');
    if (!span || span.dataset.editing === '1') return;

    span.dataset.editing = '1';
    span.contentEditable = 'plaintext-only';
    span.spellcheck = false;
    span.focus();

    const range = document.createRange();
    range.selectNodeContents(span);
    const sel = getSelection();
    sel.removeAllRanges();
    sel.addRange(range);

    span.addEventListener('input', () => {
      store.update(node.id, (n) => { n.text = { ...n.text, value: span.textContent }; });
    });
    span.addEventListener('blur', () => this.stopEditing(store), { once: true });
  }

  startEditing(store, id) {
    this.editingId = id;
    store.select(id);   // triggers a paint, which attaches the editor
  }

  stopEditing(store) {
    if (!this.editingId) return;
    const el = this.elements.get(this.editingId);
    const span = el?.el?.querySelector('.node-text');
    if (span) {
      span.removeAttribute('contenteditable');
      delete span.dataset.editing;
    }
    this.editingId = null;
    store._emit();
  }

  _renderHandles(store) {
    this.frame.querySelectorAll('.handle').forEach((h) => h.remove());
    // Handles over a caret are noise, and dragging one mid-sentence is worse.
    if (this.editingId) return;
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
      // A click inside the caret is a caret move, not the start of a drag.
      if (this.editingId && e.target.closest('.node-text[contenteditable]')) return;
      if (this.editingId) this.stopEditing(store);

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
      if (!node) return;

      const cap = this.capabilities?.(node.type);
      if (cap && !cap.text) return;

      // Double-clicking a shape with no text gives it one: "add a label"
      // should feel like typing on the thing, not filling in a side panel.
      if (node.text == null) {
        this.store.update(id, (n) => { n.text = this.newText(); });
      }
      this.startEditing(this.store, id);
    });
  }
}
