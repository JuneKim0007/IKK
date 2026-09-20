import { BaseElement } from '../core/BaseElement.js';
import { resolvedLineHeight } from '../contract/schema.js';

/**
 * Shared behaviour for every DesignNode renderer.
 *
 * Geometry, fill, stroke, radius, opacity and text all live here because they
 * live on every node in the contract envelope — docs/json_contract.md §4 makes
 * the envelope uniform, so the renderer can be uniform too. Subclasses only
 * override what genuinely differs.
 */
export class NodeElement extends BaseElement {
  createElement() {
    const el = document.createElement('div');
    el.className = 'node';
    el.tabIndex = 0;
    return el;
  }

  /** @param {object} node */
  render(node) {
    const el = this.el;
    if (!el) return;

    el.hidden = !node.visible;
    el.style.left = `${node.rect.x}%`;
    el.style.top = `${node.rect.y}%`;
    el.style.width = `${node.rect.w}%`;
    el.style.height = `${node.rect.h}%`;
    el.style.opacity = String(node.opacity);

    el.style.background = node.fill ?? 'transparent';

    // §7 — stroke alignment is INSIDE on both surfaces. box-sizing: border-box
    // is set in the stylesheet; without it a 4dp stroke makes the web render
    // 8dp wider than Compose and both look correct in isolation.
    el.style.border = node.stroke ? `${node.stroke.width}px solid ${node.stroke.color}` : 'none';
    el.style.borderRadius = node.radius === '50%' ? '50%' : `${node.radius}px`;

    this.renderContent(node);
  }

  /** @param {object} node */
  renderContent(node) {
    this.renderText(node);
  }

  /** Any node may carry text — that is what makes a labelled shape work. */
  renderText(node) {
    const existing = this.el.querySelector(':scope > .node-text');
    if (!node.text) { existing?.remove(); return; }

    const t = existing ?? document.createElement('span');
    if (!existing) { t.className = 'node-text'; this.el.appendChild(t); }

    t.textContent = node.text.value;
    t.style.fontSize = `${node.text.size}px`;
    t.style.color = node.text.color;
    t.style.fontWeight = String(node.text.weight);
    t.style.fontFamily = `${node.text.fontFamily}, sans-serif`;
    // §9 — emit line-height explicitly. CSS "normal" is font-dependent and
    // differs from Compose's default, so the two surfaces would drift.
    t.style.lineHeight = `${resolvedLineHeight(node.text)}px`;
    t.style.textAlign = node.text.align === 'center' ? 'center'
      : node.text.align === 'end' ? 'right' : 'left';
    t.style.justifyContent = node.text.align === 'center' ? 'center'
      : node.text.align === 'end' ? 'flex-end' : 'flex-start';
    t.style.webkitLineClamp = node.text.maxLines ?? '';
  }
}
