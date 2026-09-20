import { NodeElement } from './NodeElement.js';

export class RectElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-rect');
    return el;
  }
}
