import { BaseElement } from '../core/BaseElement.js';
import { PALETTE, textPayload, normalizeColor, capabilities, REFERENCE } from '../contract/schema.js';

/** Properties for the selection. Controls only appear when the node has them. */
export class Inspector extends BaseElement {
  constructor(id, opts) {
    super(id, opts);
    /**
     * Display unit. The contract always stores percentages — §5 is normative
     * and relative geometry is the whole point. This only changes what the
     * fields show, so an Android developer can read the dp they will see in
     * the generated Compose without the wire format moving.
     * @type {'%'|'dp'}
     */
    this.unit = 'dp';
  }

  /** percentage -> displayed value */
  _out(v, axis) {
    if (this.unit === '%') return Math.round(v * 10) / 10;
    const base = axis === 'x' ? REFERENCE.w : REFERENCE.h;
    return Math.round((v / 100) * base);
  }

  /** displayed value -> percentage */
  _in(v, axis) {
    if (this.unit === '%') return v;
    const base = axis === 'x' ? REFERENCE.w : REFERENCE.h;
    return Math.round((v / base) * 1000) / 10;
  }

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

    const cap = capabilities(node.type);
    body.appendChild(this._geometry(store, node));
    if (cap.fill || cap.stroke || cap.radius) body.appendChild(this._appearance(store, node, cap));
    else body.appendChild(this._opacityOnly(store, node));
    if (cap.text) body.appendChild(this._text(store, node));
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
    input.step = this.unit === 'dp' ? '1' : '0.1';
    input.value = String(value);
    input.addEventListener('change', () => onChange(Number(input.value)));
    wrap.appendChild(input);
    return wrap;
  }

  _geometry(store, node) {
    const g = this._group('Position & size');

    // Unit toggle. dp is what an Android developer reads in the generated
    // Compose; % is what the contract stores. Neither is more true, so the
    // editor shows whichever the person is thinking in.
    const units = document.createElement('div');
    units.className = 'segmented units';
    for (const u of ['dp', '%']) {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = u;
      b.setAttribute('aria-pressed', String(this.unit === u));
      b.setAttribute('aria-label', `Show values in ${u === 'dp' ? 'density-independent pixels' : 'percent'}`);
      b.addEventListener('click', () => { this.unit = u; this.render(store); });
      units.appendChild(b);
    }
    g.querySelector('h3').after(units);

    const set = (k, axis) => (v) =>
      store.update(node.id, (n) => { n.rect = { ...n.rect, [k]: this._in(v, axis) }; });

    // Position and size are read as pairs, so they are laid out as pairs.
    const pos = document.createElement('div');
    pos.className = 'pair';
    pos.innerHTML = '<span class="pair-label">Position</span>';
    const posFields = document.createElement('div');
    posFields.className = 'pair-fields';
    posFields.append(
      this._number('X', this._out(node.rect.x, 'x'), set('x', 'x')),
      this._number('Y', this._out(node.rect.y, 'y'), set('y', 'y')),
    );
    pos.appendChild(posFields);

    const size = document.createElement('div');
    size.className = 'pair';
    size.innerHTML = '<span class="pair-label">Size</span>';
    const sizeFields = document.createElement('div');
    sizeFields.className = 'pair-fields';
    sizeFields.append(
      this._number('W', this._out(node.rect.w, 'x'), set('w', 'x')),
      this._number('H', this._out(node.rect.h, 'y'), set('h', 'y')),
    );
    size.appendChild(sizeFields);

    g.append(pos, size);

    if (this.unit === 'dp') {
      const note = document.createElement('p');
      note.className = 'hint hint-sm';
      note.textContent = `dp at the ${REFERENCE.w} × ${REFERENCE.h} reference viewport. Stored as %.`;
      g.appendChild(note);
    }
    return g;
  }

  _swatchRow(label, current, onPick, { allowNone = false } = {}) {
    const wrap = document.createElement('div');
    wrap.innerHTML = `<p class="sub">${label}</p>`;
    const sws = document.createElement('div');
    sws.className = 'swatches';

    if (allowNone) {
      const none = document.createElement('button');
      none.className = 'swatch swatch-none';
      none.title = 'None';
      none.setAttribute('aria-label', `${label}: none`);
      none.setAttribute('aria-pressed', String(current == null));
      none.addEventListener('click', () => onPick(null));
      sws.appendChild(none);
    }
    for (const hex of PALETTE) {
      const b = document.createElement('button');
      b.className = 'swatch';
      b.style.background = hex;
      b.title = hex;
      b.setAttribute('aria-label', `${label} ${hex}`);
      b.setAttribute('aria-pressed', String(current === hex));
      b.addEventListener('click', () => onPick(normalizeColor(hex)));
      sws.appendChild(b);
    }
    wrap.appendChild(sws);
    return wrap;
  }

  _appearance(store, node, cap) {
    const g = this._group('Appearance');

    if (cap.fill) {
      // "None" is a real choice, not an absence: an outlined shape is a shape
      // with no fill, and json_contract.md §6 makes null distinct from a
      // transparent colour.
      g.appendChild(this._swatchRow('Fill', node.fill,
        (hex) => store.update(node.id, (n) => { n.fill = hex; }), { allowNone: true }));
    }

    if (cap.stroke) {
      g.appendChild(this._swatchRow('Stroke', node.stroke?.color ?? null, (hex) =>
        store.update(node.id, (n) => {
          if (hex == null) {
            // A line without a stroke is invisible, so its stroke is required.
            n.stroke = cap.strokeRequired ? { ...(n.stroke ?? { width: 2 }), color: '#1B1D1C' } : null;
          } else {
            n.stroke = { color: hex, width: n.stroke?.width ?? 2 };
          }
        }), { allowNone: !cap.strokeRequired }));

      g.appendChild(this._number('Thickness', node.stroke?.width ?? 0, (v) =>
        store.update(node.id, (n) => {
          const width = Math.max(cap.strokeRequired ? 0.5 : 0, v);
          n.stroke = width === 0 ? null
            : { color: n.stroke?.color ?? '#1B1D1C', width };
        })));
    }

    if (cap.radius) {
      g.appendChild(this._number('Radius', node.radius,
        (v) => store.update(node.id, (n) => { n.radius = Math.max(0, v); })));
    }

    g.appendChild(this._number('Opacity', node.opacity,
      (v) => store.update(node.id, (n) => { n.opacity = Math.min(1, Math.max(0, v)); })));
    return g;
  }

  _opacityOnly(store, node) {
    const g = this._group('Appearance');
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
