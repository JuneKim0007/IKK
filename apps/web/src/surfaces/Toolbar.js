import { BaseElement } from '../core/BaseElement.js';
import { icon } from '../ui/icons.js';

const SHAPES = [
  { type: 'rect', label: 'Rectangle', key: 'R', icon: 'rect' },
  { type: 'ellipse', label: 'Ellipse', key: 'O', icon: 'ellipse' },
  { type: 'triangle', label: 'Triangle', key: 'Y', icon: 'triangle' },
  { type: 'line', label: 'Line', key: 'L', icon: 'line' },
];

/** Tools, plus the grouped shape tool that remembers the last shape used. */
export class Toolbar extends BaseElement {
  constructor(store, { onTool, onGenerate, onUndo, onDelete }) {
    super('toolbar', { store });
    this.onTool = onTool;
    this.onGenerate = onGenerate;
    this.onUndo = onUndo;
    this.onDelete = onDelete;
    this.tool = 'move';
    this.lastShape = 'rect';
    this.flyoutOpen = false;
  }

  createElement() {
    const bar = document.createElement('header');
    bar.className = 'toolbar';
    bar.innerHTML = `
      <span class="logo">IKK</span>
      <div class="tools">
        <button class="tool" data-tool="move" aria-pressed="true" title="Move (V)" aria-label="Move">${icon('move')}</button>
        <span class="toolgroup">
          <button class="tool" id="shapeBtn" aria-haspopup="menu" aria-expanded="false"
                  title="Shape (S)" aria-label="Shape"><span class="glyph">${icon('shape')}</span><i class="caret"></i></button>
          <div class="flyout" id="shapeFly" role="menu" hidden></div>
        </span>
        <button class="tool" data-tool="text" title="Text (T)" aria-label="Text">${icon('text')}</button>
        <button class="tool" data-tool="image" title="Media (I)" aria-label="Media">${icon('media')}</button>
      </div>
      <span class="spacer"></span>
      <span class="sync" id="syncStatus" title="sync status">offline</span>
      <button class="btn" id="undoBtn">Undo</button>
      <button class="btn" id="deleteBtn">Delete</button>
      <button class="btn btn-primary" id="generateBtn">Generate</button>
    `;
    this._bind(bar);
    return bar;
  }

  _bind(bar) {
    bar.querySelector('.tools').addEventListener('click', (e) => {
      const shapeBtn = e.target.closest('#shapeBtn');
      if (shapeBtn) { this._toggleFlyout(bar); return; }
      const btn = e.target.closest('.tool[data-tool]');
      if (!btn) return;
      this._closeFlyout(bar);
      this.setTool(btn.dataset.tool, bar);
    });

    bar.querySelector('#undoBtn').addEventListener('click', () => this.onUndo?.());
    bar.querySelector('#deleteBtn').addEventListener('click', () => this.onDelete?.());
    bar.querySelector('#generateBtn').addEventListener('click', () => this.onGenerate?.());

    document.addEventListener('pointerdown', (e) => {
      if (!e.target.closest('.toolgroup')) this._closeFlyout(bar);
    });
  }

  _toggleFlyout(bar) {
    const fly = bar.querySelector('#shapeFly');
    this.flyoutOpen = !this.flyoutOpen;
    fly.hidden = !this.flyoutOpen;
    bar.querySelector('#shapeBtn').setAttribute('aria-expanded', String(this.flyoutOpen));
    if (!this.flyoutOpen) return;

    fly.innerHTML = '';
    for (const s of SHAPES) {
      const b = document.createElement('button');
      b.setAttribute('role', 'menuitemradio');
      b.setAttribute('aria-checked', String(this.lastShape === s.type));
      b.innerHTML = `${icon(s.icon, { size: 16 })}<span>${s.label}</span><kbd>${s.key}</kbd>`;
      b.addEventListener('click', () => { this.setTool(s.type, bar); this._closeFlyout(bar); });
      fly.appendChild(b);
    }
    this.setTool(this.lastShape, bar);
  }

  _closeFlyout(bar) {
    this.flyoutOpen = false;
    bar.querySelector('#shapeFly').hidden = true;
    bar.querySelector('#shapeBtn').setAttribute('aria-expanded', 'false');
  }

  setTool(tool, bar = this.el) {
    this.tool = tool;
    const isShape = SHAPES.some((s) => s.type === tool);
    if (isShape) this.lastShape = tool;
    bar.querySelectorAll('.tool[data-tool]').forEach((b) =>
      b.setAttribute('aria-pressed', String(b.dataset.tool === tool)));
    bar.querySelector('#shapeBtn').setAttribute('aria-pressed', String(isShape));
    // The button keeps the generic shape mark rather than morphing into the
    // last shape used: a button whose icon changes is a button you have to
    // read before pressing. The flyout shows which shape is active.
    const glyph = bar.querySelector('#shapeBtn .glyph');
    if (glyph && !glyph.firstChild) glyph.innerHTML = icon('shape');
    this.onTool?.(tool);
  }

  setSyncStatus(status) {
    const el = this.el?.querySelector('#syncStatus');
    if (!el) return;
    el.textContent = status;
    el.dataset.status = status;
  }

  render() { /* toolbar is not driven by contract state */ }
}
