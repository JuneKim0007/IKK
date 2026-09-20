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

  /**
   * `source.ref` is an asset id, not a URL — docs/api.md serves it from
   * /v1/assets/{ref}. A bare id in src would give a broken image.
   *
   * A source that has already failed is remembered, because render() runs on
   * every store change: without the guard the failed <img> is recreated on the
   * next paint and the browser's broken-image glyph flickers back.
   */
  renderContent(node) {
    this._failed ??= new Set();

    const ref = node.source?.ref;
    const src = !ref ? null
      : /^(https?:|data:|blob:|\/)/.test(ref) ? ref
      : `${NodeElement.assetBase ?? ''}/v1/assets/${encodeURIComponent(ref)}`;

    if (!src || this._failed.has(src)) { this._placeholder(node); return; }

    this.el.querySelector(':scope > .placeholder')?.remove();
    let img = this.el.querySelector(':scope > img');
    if (!img) {
      img = document.createElement('img');
      img.addEventListener('error', () => {
        this._failed.add(img.getAttribute('src'));
        this._placeholder(node);
      });
      this.el.appendChild(img);
    }
    if (img.getAttribute('src') !== src) img.src = src;
    img.alt = node.alt ?? '';
    img.style.objectFit = node.contentScale === 'fill' ? 'fill'
      : node.contentScale === 'fit' ? 'contain' : 'cover';
  }

  _placeholder(node) {
    this.el.querySelector(':scope > img')?.remove();
    let ph = this.el.querySelector(':scope > .placeholder');
    if (!ph) {
      ph = document.createElement('span');
      ph.className = 'placeholder';
      this.el.appendChild(ph);
    }
    ph.textContent = node.alt || 'image';
  }
}
