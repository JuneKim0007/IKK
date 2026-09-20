/**
 * A trigger button and the panel it opens, with the closing rules in one
 * place.
 *
 * Every popover in the editor has to close for the same reasons — a click
 * elsewhere, Escape, a different tool, a different selection. Implementing
 * that per-popover is how one of them ends up staying open over an unrelated
 * panel. The static registry is what lets unrelated code say "something
 * changed, close whatever is open" without knowing what is open.
 */
const openPopovers = new Set();

export class Popover {
  /**
   * @param {HTMLElement} trigger
   * @param {HTMLElement} panel
   * @param {{ onOpen?: () => void, onClose?: () => void }} [hooks]
   */
  constructor(trigger, panel, hooks = {}) {
    this.trigger = trigger;
    this.panel = panel;
    this.hooks = hooks;
    this.isOpen = false;

    panel.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');

    // Capture, so a click that also changes tools closes this before the tool
    // handler runs rather than after.
    document.addEventListener('pointerdown', (e) => {
      if (!this.isOpen) return;
      if (panel.contains(e.target) || trigger.contains(e.target)) return;
      this.close();
    }, true);

    document.addEventListener('keydown', (e) => {
      if (this.isOpen && e.key === 'Escape') { this.close(); this.trigger.focus(); }
    });
  }

  open() {
    // One popover at a time. Two open panels is always a bug, never a feature.
    Popover.closeAll(this);
    this.isOpen = true;
    this.panel.hidden = false;
    this.trigger.setAttribute('aria-expanded', 'true');
    openPopovers.add(this);
    this.hooks.onOpen?.();
  }

  close() {
    if (!this.isOpen) return;
    this.isOpen = false;
    this.panel.hidden = true;
    this.trigger.setAttribute('aria-expanded', 'false');
    openPopovers.delete(this);
    this.hooks.onClose?.();
  }

  toggle() { this.isOpen ? this.close() : this.open(); }

  /** Close everything open, optionally sparing one. */
  static closeAll(except = null) {
    for (const p of [...openPopovers]) if (p !== except) p.close();
  }
}
