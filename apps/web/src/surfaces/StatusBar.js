import { BaseElement } from '../core/BaseElement.js';
import { validate } from '../contract/schema.js';

export class StatusBar extends BaseElement {
  createElement() {
    const bar = document.createElement('footer');
    bar.className = 'statusbar';
    bar.innerHTML = `<span id="frame"></span><span class="spacer"></span>
                     <span id="violations"></span><span id="sel"></span>`;
    return bar;
  }

  render(store) {
    const { w, h, unit } = store.contract.reference;
    this.el.querySelector('#frame').textContent =
      `${store.contract.screen} · ${w} × ${h} ${unit} · ${store.nodes().length} nodes`;

    const v = validate(store.contract);
    const vEl = this.el.querySelector('#violations');
    vEl.textContent = v.length ? `${v.length} violation${v.length > 1 ? 's' : ''}: ${v[0].rule}` : '';
    vEl.dataset.bad = String(v.length > 0);

    const n = store.selected;
    this.el.querySelector('#sel').textContent =
      n ? `${n.name} — ${store.keyOf(n)}` : 'no selection';
  }
}
