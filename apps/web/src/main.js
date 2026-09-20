/**
 * Composition root. The only file that knows how the pieces fit together —
 * everything else depends on the store or on its own arguments.
 */
import './elements/index.js';
import { Store } from './core/store.js';
import { SyncClient } from './core/sync.js';
import { Canvas } from './surfaces/Canvas.js';
import { Toolbar } from './surfaces/Toolbar.js';
import { LayersPanel } from './surfaces/LayersPanel.js';
import { Inspector } from './surfaces/Inspector.js';
import { StatusBar } from './surfaces/StatusBar.js';
import { createNode, emptyContract, REFERENCE } from './contract/schema.js';
import { DrawController } from './core/DrawController.js';

const params = new URLSearchParams(location.search);
const BACKEND = params.get('api') ?? 'http://127.0.0.1:8000';
const FIXTURE = '../../docs/fixtures/valid/all_node_types.json';

async function loadContract() {
  try {
    const res = await fetch(FIXTURE, { cache: 'no-store' });
    if (res.ok) return await res.json();
  } catch { /* falls through to an empty screen */ }
  return emptyContract();
}

function warn(message) {
  const box = document.getElementById('warning');
  box.textContent = message;
  box.hidden = false;
  clearTimeout(warn._t);
  warn._t = setTimeout(() => { box.hidden = true; }, 4000);
}

async function boot() {
  const store = new Store(await loadContract());
  const sync = new SyncClient(store, { baseUrl: BACKEND, projectId: 'demo' });

  const root = document.getElementById('app');
  const layers = new LayersPanel(store, { onWarn: warn });
  const canvas = new Canvas(store);
  const inspector = new Inspector('inspector', { store });
  const status = new StatusBar('status', { store });

  const toolbar = new Toolbar(store, {
    onTool: (t) => { canvas.frame.dataset.tool = t; },
    onUndo: () => store.undo(),
    onDelete: () => store.selectedId && store.remove(store.selectedId),
    onGenerate: async () => {
      try {
        const out = await sync.generate();
        warn(`Generated at ${out.checkpoint}: ${out.artifacts.map((a) => a.name).join(', ')}`);
      } catch (e) {
        warn(`Generate failed — is the backend running? (${e.message})`);
      }
    },
  });

  toolbar.mount(root);
  const middle = document.createElement('div');
  middle.className = 'columns';
  root.appendChild(middle);
  layers.mount(middle);
  canvas.mount(middle);
  inspector.mount(middle);
  status.mount(root);

  // Drag to draw. One controller for every drawable type, so rect, triangle,
  // text and image cannot drift apart in how they feel.
  new DrawController(canvas.frame, {
    getTool: () => canvas.frame.dataset.tool,
    onCreate: (type, drawnRect) => {
      const z = Math.max(0, ...store.nodes().map((n) => n.z)) + 1;
      // Without a drag, place at the press point with the type's default size.
      const x = drawnRect ? 0 : (REFERENCE.w / 2);
      const y = drawnRect ? 0 : (REFERENCE.h / 3);
      store.add(createNode(type, store.nextName(type), x, y, z, drawnRect));
    },
    onDone: () => toolbar.setTool('move'),
  });

  addEventListener('keydown', (e) => {
    if (e.target.matches('input, textarea')) return;
    const map = { v: 'move', r: 'rect', o: 'ellipse', t: 'text', i: 'image' };
    if (map[e.key.toLowerCase()]) { toolbar.setTool(map[e.key.toLowerCase()]); return; }
    const node = store.selected;
    if (!node) return;
    const step = e.shiftKey ? 10 : 1;
    const nudge = (dx, dy) => {
      e.preventDefault();
      store.update(node.id, (n) => {
        n.rect = { ...n.rect,
          x: Math.round((n.rect.x + (dx * 100) / REFERENCE.w) * 10) / 10,
          y: Math.round((n.rect.y + (dy * 100) / REFERENCE.h) * 10) / 10 };
      });
    };
    if (e.key === 'ArrowLeft') nudge(-step, 0);
    if (e.key === 'ArrowRight') nudge(step, 0);
    if (e.key === 'ArrowUp') nudge(0, -step);
    if (e.key === 'ArrowDown') nudge(0, step);
    if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); store.remove(node.id); }
  });

  const paint = () => {
    canvas.render(store);
    layers.render(store);
    inspector.render(store);
    status.render(store);
  };
  store.subscribe(paint);
  sync.onStatus((s) => toolbar.setSyncStatus(s));

  paint();
  toolbar.setTool('move');
  sync.probe();
}

boot();
