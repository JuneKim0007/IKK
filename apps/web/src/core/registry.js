/** type -> element class. Adding a node type is one line here. */
const registry = new Map();

export function register(type, ctor) {
  registry.set(type, ctor);
}

export function create(type, ...args) {
  const Ctor = registry.get(type);
  if (!Ctor) throw new Error(`no element registered for type "${type}"`);
  return new Ctor(...args);
}

export function knownTypes() {
  return [...registry.keys()];
}
