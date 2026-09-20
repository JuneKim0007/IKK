# docs

| Document | What it is | Read when |
|---|---|---|
| [json_contract.md](json_contract.md) | **Normative** wire format | Before writing any code that touches the contract |
| [component-model.md](component-model.md) | Class tree and shared behaviours | Before implementing a surface |
| [roadmap.md](roadmap.md) | Roadmap index split by owner | Planning, or picking up the next task |
| [roadmap-backend.md](roadmap-backend.md) | Remaining Kotlin backend work | Backend planning |
| [roadmap-frontend.md](roadmap-frontend.md) | Remaining Web and Android work | Frontend planning |
| [architecture/repository-layout.md](architecture/repository-layout.md) | Folder ownership and dependency rules | Before adding or moving a module |
| [architecture/frontend-architecture.md](architecture/frontend-architecture.md) | Android/Web architecture decision | Before choosing frontend technology |

## Order of authority

When two documents disagree:

1. `json_contract.md`
2. `component-model.md`
3. `roadmap.md`
4. production implementations

Implementations must follow the contract; implementation behaviour never
silently changes the wire format.
