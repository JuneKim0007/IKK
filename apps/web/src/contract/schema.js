/**
 * TypeScript-free mirror of docs/json_contract.md.
 *
 * That document is normative. If this file disagrees with it, this file is
 * wrong. The Kotlin/JVM backend consumes `packages/design-contract` directly;
 * `docs/fixtures/` is the shared boundary corpus.
 */

export const SCHEMA_VERSION = 1;
export const REFERENCE = { w: 375, h: 667, unit: 'dp' };
export const LINE_HEIGHT_RATIO = 1.3;

/**
 * The default palette. One list, used by fill, stroke and text colour alike.
 *
 * Three separate colour lists is how a tool ends up where you can stroke a
 * shape in a green your text can never be. The rows are a neutral ramp and a
 * hue set, in that order, because a designer reaches for a neutral far more
 * often than a hue and the ramp should be where the eye lands first.
 *
 * Every value is uppercase, because json_contract.md §6 normalises to
 * uppercase and two spellings of one colour checksum differently.
 */
export const PALETTE = {
  neutral: ['#000000', '#141021', '#2A2440', '#8A86A6', '#E8E6F0', '#FFFFFF'],
  hue:     ['#4FA8E8', '#7C5CE6', '#D96BC4', '#EFB48A', '#5B8DEF', '#4ED97B'],
};

/**
 * Surfaces designers reach for first. Separate from the accent rows because a
 * background is chosen once per screen and an accent many times per screen —
 * mixing them makes the common case hunt through the rare one.
 */
export const SURFACES = {
  dark:  '#141021',
  light: '#FFFFFF',
};

/** Two defaults, matching the dark and light pair in the reference designs. */
export const BACKGROUND_PRESETS = [
  { name: 'Ink', fill: '#141021' },
  { name: 'Paper', fill: '#FFFFFF' },
  { name: 'Blush', fill: { type: 'linear', angle: 135,
      stops: [{ color: '#F49AA8', at: 0 }, { color: '#C86DD7', at: 100 }] } },
  { name: 'Dusk', fill: { type: 'linear', angle: 160,
      stops: [{ color: '#2A2440', at: 0 }, { color: '#141021', at: 100 }] } },
  { name: 'None', fill: null },
];

/** Flat form, for code that just needs "is this one of ours". */
export const PALETTE_COLORS = [...PALETTE.neutral, ...PALETTE.hue];

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
  image:   { fill: null, stroke: null, radius: 12, w: 200, h: 112 },
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
  const bg = contract.background;
  if (bg !== undefined && bg !== null) {
    const fill = bg.fill;
    if (fill !== null && fill !== undefined && typeof fill !== 'string') {
      if (fill.type !== 'linear') {
        out.push({ rule: 'V26', where: 'background', message: 'only linear gradients exist in v1' });
      } else if (!Array.isArray(fill.stops) || fill.stops.length < 2) {
        out.push({ rule: 'V25', where: 'background', message: 'a gradient needs at least two stops' });
      } else {
        let previous = -Infinity;
        for (const stop of fill.stops) {
          if (stop.at < 0 || stop.at > 100 || stop.at < previous) {
            out.push({ rule: 'V25', where: 'background', message: 'gradient stops must be 0..100 and non-decreasing' });
            break;
          }
          previous = stop.at;
        }
      }
    }
  }

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
      out.push({ rule: 'V23', where: key, message: 'a triangle carries no stroke or radius' });
    }
    if (n.type === 'line') {
      if (!n.stroke || n.stroke.width <= 0) out.push({ rule: 'V24', where: key, message: 'a line needs a positive stroke width' });
      if (n.fill) out.push({ rule: 'V24', where: key, message: 'a line has no fill' });
      if (!n.line?.orientation) out.push({ rule: 'V24', where: key, message: 'a line needs an orientation' });
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

/**
 * docs/json_contract.md §3.1 — a colour string or a linear gradient.
 * Returns a CSS value, or null when there is no background: absent is not
 * white, and painting white would silently change a dark design.
 */
export function backgroundCss(background) {
  const fill = background?.fill;
  if (!fill) return null;
  if (typeof fill === 'string') return fill;
  const stops = fill.stops.map((s) => `${s.color} ${s.at}%`).join(', ');
  return `linear-gradient(${fill.angle ?? 180}deg, ${stops})`;
}

export function emptyContract(screen = 'Home') {
  return {
    schemaVersion: SCHEMA_VERSION,
    checkpoint: 'cp_001',
    screen,
    reference: { ...REFERENCE },
    layout: 'relative',
    background: { fill: SURFACES.dark },
    components: {},
  };
}
