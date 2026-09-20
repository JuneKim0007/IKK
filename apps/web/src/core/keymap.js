/**
 * One keyboard handler for the whole editor.
 *
 * Two rules, both of which were being broken:
 *
 * 1. A shortcut never fires while text is being entered. Bare-letter tool
 *    shortcuts are the convention in every design tool, but they are only
 *    tolerable because they go quiet the moment a caret exists. `input` and
 *    `textarea` are the obvious cases; `contenteditable` is the one that gets
 *    forgotten, and it is exactly where on-canvas text editing happens.
 *
 * 2. A modifier means the shortcut belongs to the browser or the OS, not to
 *    us. Swallowing Cmd+R to select the rectangle tool is hostile.
 */

/** True when the event target is somewhere a person is entering text. */
export function isTextEntry(target) {
  if (!(target instanceof Element)) return false;
  if (target.closest('[contenteditable=""], [contenteditable="true"], [contenteditable="plaintext-only"]')) {
    return true;
  }
  return Boolean(target.closest('input, textarea, select'));
}

export class Keymap {
  /**
   * @param {{ onTool: (t: string) => void, onNudge: (dx: number, dy: number) => void,
   *           onDelete: () => void, onUndo: () => void, onEscape: () => void }} handlers
   */
  constructor(handlers) {
    this.handlers = handlers;
    this.tools = { v: 'move', r: 'rect', o: 'ellipse', y: 'triangle', l: 'line', t: 'text', i: 'image' };
    this.actions = { b: () => this.handlers.onBackground?.() };
    addEventListener('keydown', (e) => this._onKey(e));
  }

  _onKey(e) {
    // Escape works everywhere: it is how you get out of a field and back to
    // the canvas, so it must fire even while typing.
    if (e.key === 'Escape') { this.handlers.onEscape?.(e); return; }

    if (isTextEntry(e.target)) return;

    const mod = e.metaKey || e.ctrlKey;

    if (mod && e.key.toLowerCase() === 'z') {
      e.preventDefault();
      this.handlers.onUndo?.();
      return;
    }

    // Anything else with a modifier belongs to the browser.
    if (mod || e.altKey) return;

    const action = this.actions[e.key.toLowerCase()];
    if (action) { e.preventDefault(); action(); return; }

    const tool = this.tools[e.key.toLowerCase()];
    if (tool) { e.preventDefault(); this.handlers.onTool?.(tool); return; }

    const step = e.shiftKey ? 10 : 1;
    const nudges = {
      ArrowLeft: [-step, 0], ArrowRight: [step, 0],
      ArrowUp: [0, -step], ArrowDown: [0, step],
    };
    if (nudges[e.key]) { e.preventDefault(); this.handlers.onNudge?.(...nudges[e.key]); return; }

    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault();
      this.handlers.onDelete?.();
    }
  }
}
