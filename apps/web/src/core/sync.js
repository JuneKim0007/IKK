/**
 * Debounced sync to the backend.
 *
 * Debounced, not immediate: a drag emits roughly sixty mutations a second and
 * an immediate policy would hammer the service into uselessness. 400ms is the
 * figure in docs/roadmap-frontend.md F3.2.
 *
 * The backend is being ported from Python to Java. Both speak docs/api.md, so
 * this layer does not care which is running — if neither is, the editor works
 * offline and the queue drains when one appears.
 */
export class SyncClient {
  /**
   * @param {import('./store.js').Store} store
   * @param {{ baseUrl?: string, projectId?: string, debounceMs?: number }} [opts]
   */
  constructor(store, opts = {}) {
    this.store = store;
    this.baseUrl = opts.baseUrl ?? '';
    this.projectId = opts.projectId ?? 'demo';
    this.debounceMs = opts.debounceMs ?? 400;
    /** @type {'offline'|'clean'|'pending'|'failed'} */
    this.status = 'offline';
    this._timer = null;
    this._listeners = new Set();

    store.subscribe(() => this.schedule());
    // A tab being hidden is the last reliable moment to flush.
    addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') this.flush();
    });
  }

  onStatus(fn) { this._listeners.add(fn); return () => this._listeners.delete(fn); }
  _setStatus(s) { this.status = s; for (const fn of this._listeners) fn(s); }

  async probe() {
    try {
      const res = await fetch(`${this.baseUrl}/healthz`, { cache: 'no-store' });
      this._setStatus(res.ok ? 'clean' : 'offline');
      return res.ok;
    } catch {
      this._setStatus('offline');
      return false;
    }
  }

  schedule() {
    if (!this.store.dirtyIds.size) return;
    this._setStatus('pending');
    clearTimeout(this._timer);
    this._timer = setTimeout(() => this.flush(), this.debounceMs);
  }

  async flush() {
    clearTimeout(this._timer);
    if (!this.store.dirtyIds.size) return;
    try {
      const res = await fetch(
        `${this.baseUrl}/v1/projects/${this.projectId}/contract`,
        {
          method: 'PUT',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify(this.store.contract),
        },
      );
      if (!res.ok) { this._setStatus('failed'); return; }
      this.store.dirtyIds.clear();
      this._setStatus('clean');
    } catch {
      this._setStatus('offline');  // queue survives; it drains on the next edit
    }
  }

  /** docs/api.md — cut a checkpoint and return generated artifacts. */
  async generate() {
    await this.flush();
    const res = await fetch(`${this.baseUrl}/v1/projects/${this.projectId}/generate`, { method: 'POST' });
    if (!res.ok) throw new Error(`generate failed: ${res.status}`);
    return res.json();
  }
}
