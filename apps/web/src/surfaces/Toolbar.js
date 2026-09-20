import { BaseElement } from '../core/BaseElement.js';
import { icon } from '../ui/icons.js';
import { Popover } from '../ui/Popover.js';

const SHAPES = [
  { type: 'rect', label: 'Rectangle', key: 'R', icon: 'rect' },
  { type: 'ellipse', label: 'Ellipse', key: 'O', icon: 'ellipse' },
  { type: 'triangle', label: 'Triangle', key: 'Y', icon: 'triangle' },
  { type: 'line', label: 'Line', key: 'L', icon: 'line' },
];

/** Tools, plus the grouped shape tool that remembers the last shape used. */
export class Toolbar extends BaseElement {
  constructor(store, { onTool, onGenerate, onUndo, onDelete, onBackground }) {
    super('toolbar', { store });
    this.onTool = onTool;
    this.onGenerate = onGenerate;
    this.onUndo = onUndo;
    this.onDelete = onDelete;
    this.onBackground = onBackground;
    this.tool = 'move';
    this.lastShape = 'rect';
    this.flyoutOpen = false;
  }

  createElement() {
    const bar = document.createElement('header');
    bar.className = 'toolbar';
    bar.innerHTML = `
      <span class="brand"><strong class="logo">IKK</strong><span class="brand-meta">Contract studio</span></span>
      <div class="tools">
        <button class="tool" data-tool="move" aria-pressed="true" title="Move (V)" aria-label="Move">${icon('move')}</button>
        <span class="toolgroup">
          <button class="tool" id="shapeBtn" aria-haspopup="menu" aria-expanded="false"
                  title="Shape (S)" aria-label="Shape"><span class="glyph">${icon('shape')}</span><i class="caret"></i></button>
          <div class="flyout" id="shapeFly" role="menu" hidden></div>
        </span>
        <button class="tool" data-tool="text" title="Text (T)" aria-label="Text">${icon('text')}</button>
        <button class="tool" data-tool="image" title="Media (I)" aria-label="Media">${icon('media')}</button>
        <span class="tool-sep"></span>
        <button class="tool" id="backgroundBtn" title="Background (B)" aria-label="Background">${icon('background')}</button>
      </div>
      <span class="spacer"></span>
      <span class="sync" id="syncStatus" title="sync status">offline</span>
      <button class="btn" id="undoBtn">Undo</button>
      <button class="btn btn-quiet" id="deleteBtn">Delete</button>
      <button class="btn btn-primary" id="generateBtn"><span>Generate</span><span class="btn-arrow">↗</span></button>
    `;
    this._bind(bar);
    return bar;
  }

  _bind(bar) {
    this.shapePopover = new Popover(
      bar.querySelector('#shapeBtn'),
      bar.querySelector('#shapeFly'),
      { onOpen: () => this._renderFlyout(bar) },
    );

    bar.querySelector('.tools').addEventListener('click', (e) => {
      if (e.target.closest('#shapeBtn')) { this.shapePopover.toggle(); return; }
      if (e.target.closest('#backgroundBtn')) { this.onBackground?.(); return; }
      const btn = e.target.closest('.tool[data-tool]');
      if (!btn) return;
      this.setTool(btn.dataset.tool, bar);
    });

    bar.querySelector('#undoBtn').addEventListener('click', () => this.onUndo?.());
    bar.querySelector('#deleteBtn').addEventListener('click', () => this.onDelete?.());
    bar.querySelector('#generateBtn').addEventListener('click', () => this.onGenerate?.());
  }

  _renderFlyout(bar) {
    const fly = bar.querySelector('#shapeFly');
    fly.innerHTML = '';
    for (const shape of SHAPES) {
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('role', 'menuitemradio');
      // Checked follows the active shape, which is also what the trigger shows.
      b.setAttribute('aria-checked', String(this.lastShape === shape.type));
      b.innerHTML = `${icon(shape.icon, { size: 16 })}<span>${shape.label}</span><kbd>${shape.key}</kbd>`;
      b.addEventListener('click', () => {
        this.setTool(shape.type, bar);
        this.shapePopover.close();
      });
      fly.appendChild(b);
    }
  }

  setTool(tool, bar = this.el) {
    // Switching tools dismisses anything open — the shared rule, not a
    // special case for the shape group.
    if (tool !== this.lastShape || !this.shapePopover?.isOpen) Popover.closeAll();
    this.tool = tool;
    const isShape = SHAPES.some((s) => s.type === tool);
    if (isShape) this.lastShape = tool;
    bar.querySelectorAll('.tool[data-tool]').forEach((b) =>
      b.setAttribute('aria-pressed', String(b.dataset.tool === tool)));
    bar.querySelector('#shapeBtn').setAttribute('aria-pressed', String(isShape));
    // The trigger shows the active shape, and the flyout checks the same one.
    // When they disagree you cannot tell what pressing the button will do.
    const glyph = bar.querySelector('#shapeBtn .glyph');
    if (glyph) {
      const active = SHAPES.find((s) => s.type === this.lastShape);
      glyph.innerHTML = icon(isShape && active ? active.icon : 'shape');
    }
    if (this.shapePopover?.isOpen) this._renderFlyout(bar);
    this.onTool?.(tool);
  }

  setSyncStatus(status) {
    const el = this.el?.querySelector('#syncStatus');
    if (!el) return;
    el.textContent = status;
    el.dataset.status = status;
  }

  render() { /* toolbar is not driven by contract state */ }

  /** Called when the selection changes; a popover must not outlive it. */
  dismissPopovers() { Popover.closeAll(); }
}
