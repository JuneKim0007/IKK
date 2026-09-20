# API

FastAPI. Base path `/v1`. All bodies JSON unless stated.
Contract object shapes: [json_contract.md](json_contract.md).

**This file previously described only the target design and read as current
fact. It wasn't: `apps/backend/app/main.py` implements five routes, not the
fourteen below.** Every section is now marked. Do not build a client against
a "Planned" route — it will 404.

---

## Implemented

### `GET /healthz`
Liveness.

`200` → `{ "status": "ok", "codegen": "present" | "missing" }`

### `POST /v1/contracts/validate`
Run V1–V15 without storing anything. Not project-scoped — no `project_id`
in the path, unlike every other route here.

`200` → `{ "valid": true, "screen": "Home", "components": 7 }`
`200` → `{ "valid": false, "violations": [ { "rule", "where", "message" } ] }`

### `PUT /v1/projects/{project_id}/contract`
Replace the **whole** contract. There is no node-level route — the web
client's `SyncClient.flush()` PUTs the full document on every debounce tick,
not a per-node diff.

`200` → `{ "projectId", "checkpoint", "components" }`
`422` → `{ "error": "contract_invalid", "message", "violations", "requestId" }`

**No version or conflict check.** This call overwrites whatever was stored,
whole-document, no matter how stale the caller's copy is. `json_contract.md`
§11's per-node last-write-wins is not implemented — it can't diverge from the
spec because nothing here reads `version` at all yet. Safe today because
there is exactly one writer (no Android editor exists yet — `apps/android` is
still the Hello World scaffold). Revisit before a second concurrent editor
lands; see `docs/roadmap-frontend.md` F3.

### `GET /v1/projects/{project_id}/contract`
Current contract, as last PUT.

`200` → contract object
`404` → `{ "error": "not_found", ... }`

### `POST /v1/projects/{project_id}/generate`
Shells out to `packages/codegen/generate.mjs` — the same emitter the CLI and
its own test suite use; there is only one code generator in the repository.

`200` →
```json
{
  "checkpoint": "cp_006",
  "artifacts": [
    { "name": "HomeLayout.generated.kt", "target": "kotlin", "bytes": 1840, "content": "..." },
    { "name": "home.generated.css",      "target": "css",    "bytes": 920,  "content": "..." }
  ]
}
```
File contents are inlined in the response — there is no separate
fetch-by-name call.

`404` → no contract stored for this project yet
`422` → `{ "error": "codegen_failed", "message": "<generator stderr>" }`
`500` → `{ "error": "codegen_missing" }` — the `packages/codegen` checkout is absent

**Does not check for a dirty sync queue.** `component-model.md` §7 requires
`[Generate]` to refuse while nodes are still unsynced, so the artifacts
always match what the designer sees. That guard is not implemented — calling
this immediately after an edit generates from whatever was last PUT, which
may be behind the editor's local, not-yet-flushed state.

---

## Planned — not implemented

Everything below is target design for later roadmap phases. Calling any of
these routes today returns FastAPI's default 404, not the shape documented
here.

### Projects

- `POST /v1/projects` — create a project. `201` → `{ "id", "name", "createdAt" }`
- `GET /v1/projects/{project_id}` — metadata. `200` → `{ "id", "name", "createdAt", "updatedAt", "latestCheckpoint" }`

Today a `project_id` is just whatever string a client passes to `PUT
.../contract` — there is no creation step and no listing.

### Nodes

- `PUT /v1/projects/{project_id}/nodes` — batch upsert, per-node
  last-write-wins. `{ "nodes": [ { "id", "type", "version", "updatedAt",
  "payload" } ] }` → `200 { "accepted", "rejected", "checksums" }` / `409`
  per stale node.
- `DELETE /v1/projects/{project_id}/nodes/{node_id}`

This is the route set that makes §11's per-node conflict resolution real.
Until it lands, use the whole-contract PUT above.

### Checkpoints

- `POST /v1/projects/{project_id}/checkpoints` — freeze the current contract
  without generating.
- `GET /v1/projects/{project_id}/checkpoints` — list, newest first.
- `GET /v1/projects/{project_id}/checkpoints/{checkpoint}` — contract as
  frozen at that checkpoint.

Today `checkpoint` is just a field already present on the stored contract
object; there is no separate checkpoint store or history.

### Assets

- `POST /v1/projects/{project_id}/assets` — upload an image for an
  `ImageNode`. `multipart/form-data`, field `file`. `201` → `{ "ref",
  "mime", "bytes" }`.
- `GET /v1/assets/{ref}` — fetch one back.

No image upload path exists yet; an `ImageNode.source` has nowhere to point.

---

## Errors

Implemented and accurate — matches `apps/backend/app/shared/errors.py`.

```json
{ "error": "stale_version", "message": "...", "requestId": "req_8f3a" }
```

`ContractInvalid` (422) additionally carries `"violations"`.

| Status | Meaning |
|---|---|
| `400` | Malformed request |
| `404` | Not found |
| `409` | Conflict — stale version, dirty contract (once those checks exist) |
| `413` | Payload too large |
| `415` | Unsupported media type |
| `422` | Contract validation failed |
| `500` | Unhandled |

`401` is listed nowhere in code — there is no auth on this backend. Remove
from this table if it stays that way; add back only once a route enforces it.
