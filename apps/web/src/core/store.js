/**
 * Contract state, and the only writer of it.
 *
 * Everything reads from here and nothing mutates a node in place. Updates go
 * through `update()`, which bumps `version` and stamps `updatedAt` exactly as
 * docs/json_contract.md §4 requires — that is what makes an edit survive a
 * sync rather than being silently overwritten by a stale copy.
 */
import { slug } from '../contract/schema.js';

export class Store {
  /** @param {object} contract */
  constructor(contract) {
    this.contract = structuredClone(contract);
    /** @type {string|null} */
    this.selectedId = null;
    /** @type {Set<Function>} */
    this._subscribers = new Set();
    /** @type {Set<string>} */
    this.dirtyIds = new Set();
    /** @type {Array<object>} */
    this._undo = [];
  }

  subscribe(fn) {
    this._subscribers.add(fn);
    return () => this._subscribers.delete(fn);
  }

  _emit() {
    for (const fn of this._subscribers) fn(this);
  }

  /** Paint order is z ascending, never map order. json_contract.md §3. */
  nodes() {
    return Object.values(this.contract.components).sort(
      (a, b) => a.z - b.z || a.id.localeCompare(b.id),
    );
  }

  byId(id) {
    return this.nodes().find((n) => n.id === id) ?? null;
  }

  keyOf(node) {
    return `${node.type}_${slug(node.name)}`;
  }

  get selected() {
    return this.selectedId ? this.byId(this.selectedId) : null;
  }

  select(id) {
    this.selectedId = id;
    this._emit();
  }

  /**
   * The single mutation entry point.
   * @param {string} id
   * @param {(node: object) => void} mutate
   */
  update(id, mutate) {
    const node = this.byId(id);
    if (!node) return null;

    this._pushUndo();
    const oldKey = this.keyOf(node);
    mutate(node);
    node.version += 1;
    node.updatedAt = new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');

    // The key derives from the name, so a rename rekeys the map.
    const newKey = this.keyOf(node);
    if (newKey !== oldKey) {
      delete this.contract.components[oldKey];
      this.contract.components[newKey] = node;
    }

    this.dirtyIds.add(id);
    this._emit();
    return node;
  }

  /**
   * json_contract.md §4.1 — names are unique within the scope. A collision is
   * rejected and the caller shows the warning. Silently appending a suffix is
   * worse: the designer believes they named it X and the generated identifier
   * disagrees with the layers panel.
   *
   * @returns {{ ok: true } | { ok: false, message: string }}
   */
  rename(id, nextName) {
    const trimmed = nextName.trim();
    if (!trimmed) return { ok: false, message: 'A name cannot be empty.' };

    const clash = this.nodes().some(
      (n) => n.id !== id && n.name.trim().toLowerCase() === trimmed.toLowerCase(),
    );
    if (clash) {
      return {
        ok: false,
        message: `Can't create "${trimmed}" — a component with that name already exists in this group.`,
      };
    }
    this.update(id, (n) => { n.name = trimmed; });
    return { ok: true };
  }

  /** Next free ordinal name for a type, so defaults never collide. */
  nextName(type) {
    const label = { rect: 'Rectangle', ellipse: 'Ellipse', text: 'Text', image: 'Image' }[type] ?? 'Node';
    const taken = new Set(this.nodes().map((n) => n.name.toLowerCase()));
    let i = 1;
    while (taken.has(`${label} ${i}`.toLowerCase())) i += 1;
    return `${label} ${i}`;
  }

  add(node) {
    this._pushUndo();
    this.contract.components[this.keyOf(node)] = node;
    this.dirtyIds.add(node.id);
    this.selectedId = node.id;
    this._emit();
    return node;
  }

  remove(id) {
    const node = this.byId(id);
    if (!node) return;
    this._pushUndo();
    delete this.contract.components[this.keyOf(node)];
    this.dirtyIds.add(id);
    if (this.selectedId === id) this.selectedId = null;
    this._emit();
  }

  _pushUndo() {
    this._undo.push(structuredClone(this.contract));
    if (this._undo.length > 50) this._undo.shift();
  }

  undo() {
    const prev = this._undo.pop();
    if (!prev) return false;
    const affectedIds = new Set([
      ...this.nodes().map((node) => node.id),
      ...Object.values(prev.components).map((node) => node.id),
    ]);
    this.contract = prev;
    for (const id of affectedIds) this.dirtyIds.add(id);
    this.selectedId = null;
    this._emit();
    return true;
  }
}
