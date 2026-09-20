import { NodeElement } from './NodeElement.js';

/**
 * A null `source` is an unfilled frame, not an error — you drop the frame and
 * pick the file later, as in any design tool. json_contract.md §10.
 */
export class ImageElement extends NodeElement {
  createElement() {
    const el = super.createElement();
    el.classList.add('node-image');
    return el;
  }

  renderContent(node) {
    let img = this.el.querySelector(':scope > img');
    let ph = this.el.querySelector(':scope > .placeholder');

    if (node.source?.ref) {
      ph?.remove();
      if (!img) { img = document.createElement('img'); this.el.appendChild(img); }
      img.src = node.source.ref;
      img.alt = node.alt ?? '';
      img.style.objectFit = node.contentScale === 'fill' ? 'fill'
        : node.contentScale === 'fit' ? 'contain' : 'cover';
    } else {
      img?.remove();
      if (!ph) { ph = document.createElement('span'); ph.className = 'placeholder'; this.el.appendChild(ph); }
      ph.textContent = node.alt || 'image';
    }
  }
}
