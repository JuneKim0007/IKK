/**
 * Debounced sync to the backend.
 *
 * Debounced, not immediate: a drag emits roughly sixty mutations a second and
 * an immediate policy would hammer the service into uselessness. 400ms is the
 * figure in docs/roadmap-frontend.md F3.2.
 *
 * The production backend is Kotlin/JVM with Spring Boot and speaks
 * docs/api.md. If it is unavailable, the editor keeps working offline and the
 * dirty queue drains after a later edit.
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
    this._inFlight = null;
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
    if (this._inFlight) {
      await this._inFlight;
      return this.flush();
    }
    if (!this.store.dirtyIds.size) return;
    this._inFlight = this._flushOnce();
    let succeeded;
    try {
      succeeded = await this._inFlight;
    } finally {
      this._inFlight = null;
    }
    if (succeeded && this.store.dirtyIds.size) return this.flush();
  }

  async _flushOnce() {
    const sentVersions = new Map(
      [...this.store.dirtyIds].map((id) => [id, this.store.byId(id)?.version ?? null]),
    );
    const body = JSON.stringify(this.store.contract);
    try {
      const res = await fetch(
        `${this.baseUrl}/v1/projects/${this.projectId}/contract`,
        {
          method: 'PUT',
          headers: { 'content-type': 'application/json' },
          body,
        },
      );
      if (!res.ok) { this._setStatus('failed'); return false; }
      for (const [id, version] of sentVersions) {
        if ((this.store.byId(id)?.version ?? null) === version) this.store.dirtyIds.delete(id);
      }
      this._setStatus(this.store.dirtyIds.size ? 'pending' : 'clean');
      return true;
    } catch {
      this._setStatus('offline');  // queue survives; it drains on the next edit
      return false;
    }
  }

  /**
   * docs/api.md — store an image and return the ref the contract carries.
   *
   * The bytes go to the server, not into the contract. A data URL would put
   * base64 of every image into every sync payload and every generated file.
   */
  async uploadAsset(file) {
    const body = new FormData();
    body.append('file', file);
    const res = await fetch(`${this.baseUrl}/v1/projects/${this.projectId}/assets`, {
      method: 'POST', body,
    });
    if (!res.ok) {
      const detail = await res.json().catch(() => ({}));
      throw new Error(detail.message ?? `upload failed: ${res.status}`);
    }
    return res.json();   // { ref, mime, bytes }
  }

  /** Where the editor loads an uploaded asset from. */
  assetUrl(ref) { return `${this.baseUrl}/v1/assets/${encodeURIComponent(ref)}`; }

  /** docs/api.md — cut a checkpoint and return generated artifacts. */
  async generate() {
    await this.flush();
    if (this.store.dirtyIds.size) throw new Error('sync is not clean');
    const res = await fetch(`${this.baseUrl}/v1/projects/${this.projectId}/generate`, { method: 'POST' });
    if (!res.ok) throw new Error(`generate failed: ${res.status}`);
    const result = await res.json();
    this.store.contract.checkpoint = result.checkpoint;
    return result;
  }
}
