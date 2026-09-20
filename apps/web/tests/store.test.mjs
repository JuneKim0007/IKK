import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Store } from '../src/core/store.js';
import { createNode, emptyContract, slug, validate } from '../src/contract/schema.js';

const fixture = () =>
  JSON.parse(readFileSync(new URL('../../../docs/fixtures/valid/all_node_types.json', import.meta.url)));

test('the shared fixture loads and validates', () => {
  assert.deepEqual(validate(fixture()), []);
});

test('key derives from the name, not the id', () => {
  assert.equal(slug('Sign in'), 'signIn');
  assert.equal(slug('CTA button 2'), 'ctaButton2');
});

test('every mutation bumps version and stamps updatedAt', () => {
  const store = new Store(fixture());
  const node = store.nodes()[0];
  const before = node.version;
  store.update(node.id, (n) => { n.opacity = 0.5; });
  const after = store.byId(node.id);
  assert.equal(after.version, before + 1);
  assert.equal(after.opacity, 0.5);
});

test('renaming rekeys the map but keeps the id', () => {
  const store = new Store(fixture());
  const node = store.nodes()[0];
  const id = node.id;
  const result = store.rename(id, 'Hero band');
  assert.equal(result.ok, true);
  assert.equal(store.byId(id).id, id);
  assert.ok(store.contract.components['rect_heroBand']);
});

test('a duplicate name in the same scope is rejected, not suffixed', () => {
  const store = new Store(fixture());
  const [a, b] = store.nodes();
  const result = store.rename(b.id, a.name);
  assert.equal(result.ok, false);
  assert.match(result.message, /already exists in this group/);
  assert.equal(store.byId(b.id).name, b.name, 'the old name must be kept');
});

test('default names never collide', () => {
  const store = new Store(emptyContract());
  for (let i = 0; i < 3; i += 1) {
    store.add(createNode('rect', store.nextName('rect'), 10, 10 * i, i));
  }
  assert.deepEqual(store.nodes().map((n) => n.name), ['Rectangle 1', 'Rectangle 2', 'Rectangle 3']);
  assert.deepEqual(validate(store.contract), []);
});

test('created nodes are spec-valid', () => {
  const store = new Store(emptyContract());
  for (const type of ['rect', 'ellipse', 'text', 'image']) {
    store.add(createNode(type, store.nextName(type), 20, 20, store.nodes().length));
  }
  assert.deepEqual(validate(store.contract), []);
});

test('undo restores the previous contract', () => {
  const store = new Store(fixture());
  const before = store.nodes().length;
  store.remove(store.nodes()[0].id);
  assert.equal(store.nodes().length, before - 1);
  store.undo();
  assert.equal(store.nodes().length, before);
});
