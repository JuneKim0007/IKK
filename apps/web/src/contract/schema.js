/**
 * TypeScript-free mirror of docs/json_contract.md.
 *
 * That document is normative. If this file disagrees with it, this file is
 * wrong. Kotlin (`packages/design-contract`) and the Java backend hold the
 * same model; `docs/fixtures/` is the corpus all three run.
 */

export const SCHEMA_VERSION = 1;
export const REFERENCE = { w: 375, h: 667, unit: 'dp' };
export const LINE_HEIGHT_RATIO = 1.3;

export const PALETTE = [
  '#65558F', '#4C6FBF', '#2E8B74', '#B4772A',
  '#C0504D', '#5E6462', '#E8E3F0', '#FFFFFF',
];

const COLOR = /^#[0-9A-F]{6}([0-9A-F]{2})?$/;

/** §4.1 — the key derives from the designer-facing name, never from the id. */
export function slug(raw) {
  const parts = String(raw).trim().split(/[^A-Za-z0-9]+/).filter(Boolean);
  if (!parts.length) return 'unnamed';
  return parts[0].toLowerCase() +
    parts.slice(1).map((p) => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase()).join('');
}

export function normalizeColor(raw) {
  if (raw == null) return null;
  const up = String(raw).toUpperCase();
  if (!COLOR.test(up)) throw new Error(`invalid colour "${raw}"`);
  return up;
}

/** §5 — percentages, one decimal place. More precision is false confidence. */
export function rel(x, y, w, h) {
  const r1 = (v) => Math.round(v * 10) / 10;
  return { x: r1(x), y: r1(y), w: r1(w), h: r1(h), unit: '%' };
}

export function resolvedLineHeight(text) {
  return text.lineHeight ?? Math.round(text.size * LINE_HEIGHT_RATIO * 100) / 100;
}

/** §9 — null means no text slot; an empty value means a slot that is empty. */
export function textPayload(value, size, align = 'start', color = '#1B1D1C', weight = 400) {
  return { value, size, align, color: normalizeColor(color), weight,
           lineHeight: null, fontFamily: 'Roboto', maxLines: null };
}

let seq = 0;
/** §4.1 — id is a UUID, never shown, never reused. */
export function newId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  seq += 1;
  return `n-${Date.now().toString(36)}-${seq}`;
}

const DEFAULTS = {
  rect:    { fill: '#E8E3F0', stroke: { color: '#B5AFBC', width: 1 }, radius: 8,  w: 160, h: 56 },
  ellipse: { fill: '#E8E3F0', stroke: { color: '#B5AFBC', width: 1 }, radius: '50%', w: 96, h: 96 },
  triangle: { fill: '#E8E3F0', stroke: null, radius: 0, w: 104, h: 92 },
  line: { fill: null, stroke: { color: '#1B1D1C', width: 2 }, radius: 0, w: 140, h: 80 },
  text:    { fill: null, stroke: null, radius: 0, w: 180, h: 28 },
  image:   { fill: '#DFDAE6', stroke: null, radius: 8, w: 180, h: 101 },
};

export const DRAWABLE_TYPES = ['rect', 'ellipse', 'triangle', 'line', 'text', 'image'];

/**
 * Which controls a type actually has. The contract envelope is uniform — every
 * node carries every field — so the editor cannot infer this from the data and
 * asks here instead. One table, so the two surfaces cannot disagree about
 * whether a triangle has a radius control.
 *
 * docs/component-model.md §5 explains why each cell is what it is.
 */
export const CAPABILITIES = {
  rect:     { fill: true,  stroke: true,  strokeRequired: false, radius: true,  text: true  },
  ellipse:  { fill: true,  stroke: true,  strokeRequired: false, radius: false, text: true  },
  triangle: { fill: true,  stroke: false, strokeRequired: false, radius: false, text: true  },
  line:     { fill: false, stroke: true,  strokeRequired: true,  radius: false, text: false },
  text:     { fill: false, stroke: false, strokeRequired: false, radius: false, text: true  },
  image:    { fill: false, stroke: true,  strokeRequired: false, radius: true,  text: false },
};

export function capabilities(type) {
  return CAPABILITIES[type] ?? CAPABILITIES.rect;
}

/**
 * Creates a spec-valid node.
 * @param {string} type
 * @param {string} name  from Store.nextName, so defaults never collide
 * @param {number} x     dp at the reference viewport
 * @param {number} y     dp at the reference viewport
 * @param {number} z
 * @param {object} [drawnRect] percentages from DrawController; when absent the
 *                             type's default size is used at (x, y)
 */
export function createNode(type, name, x, y, z, drawnRect) {
  const d = DEFAULTS[type];
  const node = {
    id: newId(),
    type,
    name,
    z,
    visible: true,
    opacity: 1,
    rect: drawnRect ?? rel((x / REFERENCE.w) * 100, (y / REFERENCE.h) * 100,
                           (d.w / REFERENCE.w) * 100, (d.h / REFERENCE.h) * 100),
    fill: d.fill,
    stroke: d.stroke,
    radius: d.radius,
    text: type === 'text' ? textPayload('Text', 18) : null,
    version: 1,
    updatedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
  };
  if (type === 'image') {
    node.source = null;          // a frame can exist before a file is chosen
    node.contentScale = 'crop';
    node.alt = name;
  }
  if (type === 'line') {
    // The bounding box plus which diagonal it runs along. Keeping the box
    // means selection, nudge and resize need no special case for lines.
    node.line = { orientation: 'topLeftToBottomRight' };
  }
  return node;
}

/** A subset of §12 — enough to keep the editor honest before the server sees it. */
export function validate(contract) {
  const out = [];
  const seenNames = new Set();
  const seenZ = new Set();

  if (contract.schemaVersion !== SCHEMA_VERSION) {
    out.push({ rule: 'V1', where: 'root', message: `schemaVersion ${contract.schemaVersion}` });
  }
  for (const [key, n] of Object.entries(contract.components)) {
    if (key !== `${n.type}_${slug(n.name)}`) {
      out.push({ rule: 'V2', where: key, message: 'key disagrees with {type}_{slug(name)}' });
    }
    if (n.opacity < 0 || n.opacity > 1) out.push({ rule: 'V4', where: key, message: 'opacity out of range' });
    if (n.rect.w <= 0 || n.rect.h <= 0) out.push({ rule: 'V5', where: key, message: 'non-positive size' });
    if (n.type === 'text' && !n.text) out.push({ rule: 'V7', where: key, message: 'text node without text' });
    if (n.type === 'ellipse' && n.radius !== '50%') out.push({ rule: 'V10', where: key, message: 'ellipse radius' });
    if (n.type === 'triangle' && (n.stroke || n.radius !== 0)) {
      out.push({ rule: 'V16', where: key, message: 'a triangle carries no stroke or radius' });
    }
    if (n.type === 'line') {
      if (!n.stroke) out.push({ rule: 'V17', where: key, message: 'a line must have a stroke' });
      if (n.fill) out.push({ rule: 'V17', where: key, message: 'a line has no fill' });
      if (!n.line?.orientation) out.push({ rule: 'V17', where: key, message: 'a line needs an orientation' });
    }
    if (!n.name?.trim()) out.push({ rule: 'V13', where: key, message: 'blank name' });
    const folded = n.name?.trim().toLowerCase();
    if (seenNames.has(folded)) out.push({ rule: 'V14', where: key, message: `duplicate name "${n.name}"` });
    seenNames.add(folded);
    if (seenZ.has(n.z)) out.push({ rule: 'V12', where: key, message: `duplicate z ${n.z}` });
    seenZ.add(n.z);
  }
  return out;
}

export function emptyContract(screen = 'Home') {
  return {
    schemaVersion: SCHEMA_VERSION,
    checkpoint: 'cp_001',
    screen,
    reference: { ...REFERENCE },
    layout: 'relative',
    components: {},
  };
}
