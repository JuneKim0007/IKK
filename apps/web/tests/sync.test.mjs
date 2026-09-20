import test from 'node:test';
import assert from 'node:assert/strict';
import { Store } from '../src/core/store.js';
import { SyncClient } from '../src/core/sync.js';
import { createNode, emptyContract } from '../src/contract/schema.js';

const until = async (predicate) => {
  for (let i = 0; i < 20 && !predicate(); i += 1) await new Promise((resolve) => setTimeout(resolve, 0));
  assert.ok(predicate(), 'condition was not reached');
};

test('full-contract flushes are serialized and preserve mid-flight edits', async () => {
  const originalFetch = globalThis.fetch;
  const originalAddEventListener = globalThis.addEventListener;
  const pending = [];
  globalThis.addEventListener = () => {};
  globalThis.fetch = (_url, options) => new Promise((resolve) => {
    pending.push({ body: JSON.parse(options.body), resolve });
  });

  try {
    const store = new Store(emptyContract());
    const node = createNode('rect', 'Card', 0, 0, 0);
    store.add(node);
    const sync = new SyncClient(store, { debounceMs: 60_000 });

    const first = sync.flush();
    await until(() => pending.length === 1);
    store.update(node.id, (value) => { value.opacity = 0.5; });
    const second = sync.flush();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.equal(pending.length, 1, 'a second PUT must wait for the first');

    pending[0].resolve({ ok: true });
    await until(() => pending.length === 2);
    assert.equal(Object.values(pending[0].body.components)[0].opacity, 1);
    assert.equal(Object.values(pending[1].body.components)[0].opacity, 0.5);
    pending[1].resolve({ ok: true });

    await Promise.all([first, second]);
    assert.equal(store.dirtyIds.size, 0);
    assert.equal(sync.status, 'clean');
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.addEventListener = originalAddEventListener;
  }
});
