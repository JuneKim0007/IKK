/**
 * Toolbar iconography.
 *
 * Inline SVG rather than glyphs or an icon font: glyphs render differently on
 * every platform and an icon font is a network dependency for four shapes.
 * Everything is stroked in `currentColor` so the active and dark states come
 * from CSS rather than a second set of assets.
 *
 * All icons share a 20×20 box and a 1.6 stroke so they sit on the same optical
 * weight in a row.
 */

const svg = (body, { size = 18 } = {}) =>
  `<svg viewBox="0 0 20 20" width="${size}" height="${size}" fill="none"
        stroke="currentColor" stroke-width="1.6" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true">${body}</svg>`;

export const icons = {
  /** Pointer. Filled, because a hollow cursor reads as an outline shape. */
  move: () => svg(`
    <path d="M4.5 2.5 L15.5 9.2 L10.4 10.4 L8.4 15.4 Z"
          fill="currentColor" stroke-width="1.2"/>`),

  /** Square overlapping a circle — the conventional "shapes" mark. */
  shape: () => svg(`
    <rect x="2.6" y="2.6" width="9.2" height="9.2" rx="1.6"/>
    <circle cx="13" cy="13" r="4.4"/>`),

  /** Ellipse alone, for the flyout row. */
  ellipse: () => svg(`<ellipse cx="10" cy="10" rx="7.4" ry="6.2"/>`),

  /** Triangle alone, for the flyout row. */
  triangle: () => svg(`<path d="M10 3.4 17.2 16.2H2.8Z"/>`),

  /** Line: a bare diagonal stroke. */
  line: () => svg(`<path d="M3.4 16.6 16.6 3.4"/>`),

  /** Rectangle alone, for the flyout row. */
  rect: () => svg(`<rect x="2.6" y="4.4" width="14.8" height="11.2" rx="1.8"/>`),

  /** Text. The small second T marks it as type, not a single letter. */
  text: () => svg(`
    <path d="M3 4.6h8.4M7.2 4.6V16"/>
    <path d="M12.4 9.4h4.8M14.8 9.4V16" stroke-width="1.4"/>`),

  /**
   * Media. A framed picture with a magnifier — the picker affordance, which
   * reads as "choose an image" rather than "here is an image".
   */
  media: () => svg(`
    <path d="M16.6 10.4V4.2a1.6 1.6 0 0 0-1.6-1.6H3.8a1.6 1.6 0 0 0-1.6 1.6v9.2a1.6 1.6 0 0 0 1.6 1.6h6"/>
    <path d="M2.2 11.6 6 8.2l2.8 2.3"/>
    <circle cx="12.4" cy="6.2" r="1.25" fill="currentColor" stroke="none"/>
    <circle cx="13.9" cy="13.9" r="3.1"/>
    <path d="M16.2 16.2 18.4 18.4"/>`),
};

/** @param {keyof typeof icons} name */
export function icon(name, opts) {
  const make = icons[name];
  if (!make) throw new Error(`unknown icon "${name}"`);
  return make(opts);
}
