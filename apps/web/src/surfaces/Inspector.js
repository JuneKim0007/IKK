import { BaseElement } from '../core/BaseElement.js';
import { PALETTE, textPayload, normalizeColor } from '../contract/schema.js';

/** Properties for the selection. Controls only appear when the node has them. */
export class Inspector extends BaseElement {
  createElement() {
    const aside = document.createElement('aside');
    aside.className = 'panel panel-right';
    aside.innerHTML = `<div class="panel-head"><span>Properties</span><span id="selType">—</span></div>
                       <div class="inspector-body" id="body"></div>`;
    return aside;
  }

  render(store) {
    const body = this.el.querySelector('#body');
    const node = store.selected;
    this.el.querySelector('#selType').textContent = node?.type ?? '—';
    body.innerHTML = '';

    if (!node) {
      body.innerHTML = '<p class="hint">Nothing selected. Pick a tool and drag on the canvas, or click a shape.</p>';
      return;
    }

    body.appendChild(this._geometry(store, node));
    if (node.type !== 'text') body.appendChild(this._appearance(store, node));
    body.appendChild(this._text(store, node));
  }

  _group(title) {
    const g = document.createElement('section');
    g.className = 'group';
    g.innerHTML = `<h3>${title}</h3>`;
    return g;
  }

  _number(label, value, onChange) {
    const wrap = document.createElement('label');
    wrap.className = 'field field-num';
    wrap.innerHTML = `<span>${label}</span>`;
    const input = document.createElement('input');
    input.type = 'number';
    input.step = '0.1';
    input.value = String(value);
    input.addEventListener('change', () => onChange(Number(input.value)));
    wrap.appendChild(input);
    return wrap;
  }

  _geometry(store, node) {
    const g = this._group('Position & size');
    const grid = document.createElement('div');
    grid.className = 'grid-2';
    const set = (k) => (v) => store.update(node.id, (n) => { n.rect = { ...n.rect, [k]: v }; });
    grid.append(
      this._number('X', node.rect.x, set('x')),
      this._number('W', node.rect.w, set('w')),
      this._number('Y', node.rect.y, set('y')),
      this._number('H', node.rect.h, set('h')),
    );
    g.appendChild(grid);
    return g;
  }

  _appearance(store, node) {
    const g = this._group('Appearance');

    const sws = document.createElement('div');
    sws.className = 'swatches';
    for (const hex of PALETTE) {
      const b = document.createElement('button');
      b.className = 'swatch';
      b.style.background = hex;
      b.setAttribute('aria-label', `Fill ${hex}`);
      b.addEventListener('click', () => store.update(node.id, (n) => { n.fill = normalizeColor(hex); }));
      sws.appendChild(b);
    }
    g.appendChild(sws);

    if (node.radius !== '50%') {
      g.appendChild(this._number('Radius', node.radius,
        (v) => store.update(node.id, (n) => { n.radius = Math.max(0, v); })));
    }
    g.appendChild(this._number('Opacity', node.opacity,
      (v) => store.update(node.id, (n) => { n.opacity = Math.min(1, Math.max(0, v)); })));
    return g;
  }

  _text(store, node) {
    const g = this._group('Text');

    // §9 — null means no slot at all. Collapsing null and "" is what makes the
    // Add-text affordance flicker while someone is typing.
    if (node.text == null) {
      if (node.type === 'image') {
        g.innerHTML += '<p class="hint">An image node cannot carry text.</p>';
        return g;
      }
      const add = document.createElement('button');
      add.className = 'btn full';
      add.textContent = '+ Add text to this shape';
      add.addEventListener('click', () =>
        store.update(node.id, (n) => { n.text = textPayload('Label', 16, 'center', '#1B1D1C', 500); }));
      g.appendChild(add);
      return g;
    }

    const value = document.createElement('textarea');
    value.className = 'field-area';
    value.value = node.text.value;
    value.rows = 2;
    value.addEventListener('input', () =>
      store.update(node.id, (n) => { n.text = { ...n.text, value: value.value }; }));
    g.appendChild(value);

    g.appendChild(this._number('Size', node.text.size,
      (v) => store.update(node.id, (n) => { n.text = { ...n.text, size: Math.max(1, v) }; })));
    g.appendChild(this._number('Weight', node.text.weight,
      (v) => store.update(node.id, (n) => {
        n.text = { ...n.text, weight: Math.min(900, Math.max(100, Math.round(v / 100) * 100)) };
      })));

    const seg = document.createElement('div');
    seg.className = 'segmented';
    for (const [val, glyph] of [['start', '⇤'], ['center', '↔'], ['end', '⇥']]) {
      const b = document.createElement('button');
      b.textContent = glyph;
      b.setAttribute('aria-pressed', String(node.text.align === val));
      b.setAttribute('aria-label', `Align ${val}`);
      b.addEventListener('click', () =>
        store.update(node.id, (n) => { n.text = { ...n.text, align: val }; }));
      seg.appendChild(b);
    }
    g.appendChild(seg);
    return g;
  }
}
