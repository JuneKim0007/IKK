/**
 * The one base class. Everything the editor puts on screen extends it.
 *
 * It exists to guarantee three things, which are the same three
 * `BaseUIComponent` guarantees in docs/component-model.md: stable identity, an
 * idempotent render, and a single mutation path.
 *
 * The single mutation path is the load-bearing one. `markDirty()` is the only
 * way state changes. If a field can be written without it, the sync layer never
 * hears about the edit and the change is silently lost on the next flush.
 */
export class BaseElement {
  /**
   * @param {string} id
   * @param {{ store?: import('./store.js').Store, syncPolicy?: 'debounced'|'manual' }} [opts]
   */
  constructor(id, opts = {}) {
    if (new.target === BaseElement) {
      throw new TypeError('BaseElement is abstract');
    }
    /** @type {string} */
    this.id = id;
    /** @type {HTMLElement|null} */
    this.el = null;
    /** @type {boolean} */
    this._dirty = false;
    /** @type {boolean} */
    this._mounted = false;
    this.store = opts.store ?? null;
    this.syncPolicy = opts.syncPolicy ?? 'debounced';
  }

  /** Build the DOM node. Called once. Subclasses override `createElement`. */
  mount(parent) {
    if (this._mounted) return this.el;
    this.el = this.createElement();
    this.el.dataset.elementId = this.id;
    parent.appendChild(this.el);
    this._mounted = true;
    return this.el;
  }

  /** @returns {HTMLElement} */
  createElement() {
    throw new Error(`${this.constructor.name} must implement createElement()`);
  }

  /**
   * Data in, DOM out. Must be idempotent: calling it twice with the same model
   * produces the same result and no side effects. The canvas re-renders on
   * every store change and relies on that.
   */
  render(_model) {
    throw new Error(`${this.constructor.name} must implement render()`);
  }

  /** Remove from the DOM and drop listeners. */
  destroy() {
    this.el?.remove();
    this.el = null;
    this._mounted = false;
  }

  get isDirty() {
    return this._dirty;
  }

  /**
   * The only mutation path. Routes the change into the store, which is the
   * single writer of contract state, and marks this element for the next sync
   * flush.
   *
   * @protected
   * @param {(node: any) => any} mutate
   */
  markDirty(mutate) {
    this._dirty = true;
    if (this.store && typeof mutate === 'function') {
      this.store.update(this.id, mutate);
    }
  }

  /** Called by the sync layer on acknowledgement. Never by UI code. */
  clearDirty() {
    this._dirty = false;
  }
}
