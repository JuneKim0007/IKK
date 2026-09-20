import { NodeElement } from './NodeElement.js';

/**
 * A line is its bounding box plus which diagonal it runs along.
 *
 * Modelling it as a box rather than two points is what lets it reuse every
 * other behaviour unchanged — drag, resize handles, nudge, z-order, the
 * inspector's X/Y/W/H. The cost is that a line cannot be arbitrary-angle
 * beyond its box diagonal, which no one has asked for.
 */
export class LineElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-line');
    return el;
  }

  render(node) {
    super.render(node);
    const w = node.stroke?.width ?? 1;
    const color = node.stroke?.color ?? '#000000';
    const dir = node.line?.orientation === 'bottomLeftToTopRight' ? 'to top right' : 'to bottom right';
    // A gradient band along the diagonal: one element, no SVG, and the same
    // geometry the Compose emitter draws with a Canvas stroke.
    this.el.style.background =
      `linear-gradient(${dir}, transparent calc(50% - ${w}px), ${color} calc(50% - ${w}px), ` +
      `${color} calc(50% + ${w}px), transparent calc(50% + ${w}px))`;
    this.el.style.border = 'none';
  }

  renderContent() { /* a line carries no text */ }
}
