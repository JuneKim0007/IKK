# API

Kotlin/JVM with Spring Boot 4.1.1. Base path `/v1`. All bodies are JSON unless
stated otherwise. Contract bodies are parsed and validated by the shared
`:packages:design-contract` `ContractJson` and `ContractValidator`.
Contract object shapes: [json_contract.md](json_contract.md).

When `IKK_API_TOKEN` is set, every route except `/healthz` requires
`Authorization: Bearer <token>`. When it is empty (the local default), those
routes are open. Every response includes `X-Request-ID`; a valid incoming
`X-Request-ID` is preserved.

The local server binds to `127.0.0.1:8000` by default. `IKK_BIND_ADDRESS`
changes the bind address, and `PORT` changes the port. Browser access is
limited to the comma-separated `IKK_ALLOWED_ORIGINS` list, whose local default
is `http://127.0.0.1:5173,http://localhost:5173`.

---

## Health

### `GET /healthz`
Process and database readiness.

`200` → `{ "status": "ok" }`

---

## Projects

### `POST /v1/projects`
Create a project.

```json
{ "name": "Inbox app" }
```
`201` → `{ "id": "p_7", "name": "Inbox app", "createdAt": "...", "updatedAt": "...", "latestCheckpoint": "cp_000" }`

### `GET /v1/projects/{project_id}`
Project metadata.

`200` → `{ "id", "name", "createdAt", "updatedAt", "latestCheckpoint" }`
`404` → not found

### `GET /v1/projects/{project_id}/contract`
Current contract, all nodes.

`200` → contract object
`404` → not found

### `PUT /v1/projects/{project_id}/contract`
Validate and replace the complete working contract atomically. A valid missing
project id is created on first import, which is how the local Web editor
bootstraps `demo`. The first occurrence of a checkpoint name is stored as an
immutable snapshot; later working edits with that same name do not overwrite
the frozen snapshot. The submitted contract's checkpoint must not be older
than the project's current checkpoint. A stale submission is rejected in full
and does not partially replace nodes or create a snapshot.

`200` → `{ "projectId": "p_7", "checkpoint": "cp_005", "components": 4 }`
`400` → invalid project id
`409` → `{ "error": "stale_checkpoint", "message": "...", "requestId": "...", "details": [] }`
`422` → contract failed strict parsing or validation

---

## Nodes

### `PUT /v1/projects/{project_id}/nodes`
Batch upsert. Each incoming node version must be strictly greater than its
stored version; `updatedAt` is metadata, not a cross-device clock.

```json
{ "nodes": [ { "id", "type", "schemaVersion": 1, "version", "updatedAt", "payload" } ] }
```
`200` → `{ "accepted": ["n1"], "rejected": [], "checksums": { "n1": "sha256:..." } }`
`409` → `{ "error": "stale_version", "message": "...", "requestId": "...", "details": [ { "id": "n1", "reason": "stale_version", "server": { } } ] }`
`422` → validation failure

Checksums are computed server-side and returned; clients do not send them.

### `DELETE /v1/projects/{project_id}/nodes/{node_id}`
Remove a node.

`204` → deleted
`404` → not found

### `POST /v1/projects/{project_id}/validate`
Validate the request body without writing. If the body is omitted, validate the
project's currently stored contract.

`200` → `{ "valid": true, "violations": [], "screen": "Home", "components": 4 }`
`200` → `{ "valid": false, "violations": [ { "rule": "V10", "where": "ellipse_2", "message": "..." } ], "screen": "Home", "components": 4 }`

### `POST /v1/contracts/validate`
Validate a complete contract without first creating a project and without
writing anything. The response shape is the same as project validation.

---

## Checkpoints

### `POST /v1/projects/{project_id}/checkpoints`
Freeze the current contract.

`201` → `{ "checkpoint": "cp_006", "createdAt": "..." }`
`422` → contract invalid

### `GET /v1/projects/{project_id}/checkpoints`
List, newest first.

`200` → `{ "checkpoints": [ { "checkpoint", "createdAt" } ] }`

### `GET /v1/projects/{project_id}/checkpoints/{checkpoint}`
Contract as frozen at that checkpoint.

`200` → contract object
`404` → not found

---

## Generate

### `POST /v1/projects/{project_id}/generate`
Cut a checkpoint, run codegen, return artifacts.

```json
{ "targets": ["kotlin", "css", "html"] }
```
`200` →
```json
{
  "checkpoint": "cp_006",
  "artifacts": [
    { "name": "HomeLayout.generated.kt", "target": "kotlin", "bytes": 1840 },
    { "name": "home.generated.css", "target": "css", "bytes": 920 },
    { "name": "home.generated.html", "target": "html", "bytes": 480 }
  ]
}
```
`422` → contract invalid

Only the requested targets remain current: a successful partial generation
removes artifacts from older checkpoints for omitted targets, so the artifact
endpoint can never return a mixed-checkpoint set.

### `GET /v1/projects/{project_id}/artifacts/{name}`
Fetch one generated file.

`200` → file body with `text/css`, `text/html`, or `text/plain`
`404` → not generated

---

## Assets

### `POST /v1/projects/{project_id}/assets`
Upload an image for an `ImageNode`.

`multipart/form-data`, field `file`

`201` → `{ "ref": "asset_12", "mime": "image/png", "bytes": 48210 }`
`413` → too large
`415` → unsupported type

### `GET /v1/assets/{ref}`
Fetch an asset.

`200` → binary, original content type
`404` → not found

---

## Errors

All non-2xx share one shape:

```json
{ "error": "stale_version", "message": "...", "requestId": "req_8f3a", "details": [] }
```

| Status | Meaning |
|---|---|
| `400` | Malformed request |
| `401` | Missing or invalid credentials |
| `404` | Not found |
| `409` | Conflict — stale node version |
| `413` | Payload too large |
| `415` | Unsupported media type |
| `422` | Contract validation failed |
| `500` | Unhandled |
