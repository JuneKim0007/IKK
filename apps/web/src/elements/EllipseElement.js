import { NodeElement } from './NodeElement.js';

/**
 * §8 — an ellipse is always `border-radius: 50%`, which on a non-square box
 * gives a true ellipse. The Compose counterpart is
 * RoundedCornerShape(percent = 50); CircleShape gives a pill and is the
 * obvious-looking wrong answer.
 */
export class EllipseElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-ellipse');
    return el;
  }
}
