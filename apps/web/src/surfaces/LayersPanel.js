import { BaseElement } from '../core/BaseElement.js';

/** Layers. Rename lives here, and rename is where V14 gets enforced. */
export class LayersPanel extends BaseElement {
  constructor(store, { onWarn }) {
    super('layers', { store });
    this.onWarn = onWarn;
  }

  createElement() {
    const aside = document.createElement('nav');
    aside.className = 'panel panel-left';
    aside.innerHTML = `<div class="panel-head"><span>Layers</span><span id="layerCount">0</span></div>
                       <div class="layer-list" id="layerList"></div>`;
    return aside;
  }

  render(store) {
    const list = this.el.querySelector('#layerList');
    list.innerHTML = '';
    const nodes = store.nodes().reverse();      // front-most first
    this.el.querySelector('#layerCount').textContent = String(nodes.length);

    for (const node of nodes) {
      const row = document.createElement('div');
      row.className = 'layer';
      row.setAttribute('aria-current', String(node.id === store.selectedId));

      const eye = document.createElement('button');
      eye.className = 'eye';
      eye.textContent = node.visible ? '◉' : '○';
      eye.setAttribute('aria-label', `${node.visible ? 'Hide' : 'Show'} ${node.name}`);
      eye.addEventListener('click', (e) => {
        e.stopPropagation();
        store.update(node.id, (n) => { n.visible = !n.visible; });
      });

      const name = document.createElement('input');
      name.className = 'layer-name';
      name.value = node.name;
      name.addEventListener('focus', () => store.select(node.id));
      name.addEventListener('change', () => {
        const result = store.rename(node.id, name.value);
        if (!result.ok) {
          // §4.1 — reject and warn. A silent suffix makes the designer believe
          // they named it X while the generated identifier says otherwise.
          this.onWarn?.(result.message);
          name.value = node.name;
        }
      });

      const type = document.createElement('span');
      type.className = 'layer-type';
      type.textContent = node.type;

      row.append(eye, name, type);
      row.addEventListener('click', () => store.select(node.id));
      list.appendChild(row);
    }
  }
}
