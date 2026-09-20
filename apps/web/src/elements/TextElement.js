import { NodeElement } from './NodeElement.js';

/** A text node is a node whose only content is text: no fill, no stroke. */
export class TextElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-text-node');
    return el;
  }
}
