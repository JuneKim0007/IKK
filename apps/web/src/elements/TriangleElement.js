import { NodeElement } from './NodeElement.js';

/**
 * A triangle is a clip-path on the same box every other node uses, so it
 * inherits geometry, fill, opacity and text without a second code path.
 *
 * CSS `clip-path` clips the border away, so a stroked triangle needs the
 * outline drawn rather than bordered — noted in docs/json_contract.md as the
 * one place triangle differs from rect.
 */
export class TriangleElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-triangle');
    return el;
  }
}
