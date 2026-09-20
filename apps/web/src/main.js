/**
 * Composition root. The only file that knows how the pieces fit together —
 * everything else depends on the store or on its own arguments.
 */
import './elements/index.js';
import { NodeElement } from './elements/NodeElement.js';
import { Store } from './core/store.js';
import { SyncClient } from './core/sync.js';
import { Canvas } from './surfaces/Canvas.js';
import { Toolbar } from './surfaces/Toolbar.js';
import { LayersPanel } from './surfaces/LayersPanel.js';
import { Inspector } from './surfaces/Inspector.js';
import { StatusBar } from './surfaces/StatusBar.js';
import { createNode, emptyContract, REFERENCE, capabilities, textPayload } from './contract/schema.js';
import { DrawController } from './core/DrawController.js';
import { Keymap } from './core/keymap.js';

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

function warn(message, tone = 'error') {
  const box = document.getElementById('warning');
  box.textContent = message;
  box.dataset.tone = tone;
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
  canvas.capabilities = capabilities;
  canvas.newText = () => textPayload('Label', 16, 'center', '#1B1D1C', 500);
  const inspector = new Inspector('inspector', { store });
  inspector.upload = (file) => sync.uploadAsset(file);
  inspector.onWarn = warn;
  NodeElement.assetBase = BACKEND;
  const status = new StatusBar('status', { store });

  const toolbar = new Toolbar(store, {
    onTool: (t) => { canvas.frame.dataset.tool = t; },
    onUndo: () => store.undo(),
    onDelete: () => store.selectedId && store.remove(store.selectedId),
    // Deselecting IS opening the screen panel — the background lives there.
    onBackground: () => store.select(null),
    onGenerate: async () => {
      try {
        const out = await sync.generate();
        warn(`Generated at ${out.checkpoint}: ${out.artifacts.map((a) => a.name).join(', ')}`, 'success');
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

  new Keymap({
    onTool: (t) => toolbar.setTool(t),
    onBackground: () => store.select(null),
    onUndo: () => store.undo(),
    onDelete: () => store.selectedId && store.remove(store.selectedId),
    onEscape: () => {
      if (canvas.editingId) { canvas.stopEditing(store); return; }
      if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
      store.select(null);
    },
    onNudge: (dx, dy) => {
      const node = store.selected;
      if (!node) return;
      store.update(node.id, (n) => {
        n.rect = { ...n.rect,
          x: Math.round((n.rect.x + (dx * 100) / REFERENCE.w) * 10) / 10,
          y: Math.round((n.rect.y + (dy * 100) / REFERENCE.h) * 10) / 10 };
      });
    },
  });

  let lastSelection = store.selectedId;
  const paint = () => {
    if (store.selectedId !== lastSelection) {
      lastSelection = store.selectedId;
      toolbar.dismissPopovers();
    }
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
