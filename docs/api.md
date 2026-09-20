# API

FastAPI. Base path `/v1`. All bodies JSON unless stated.
Contract object shapes: [json_contract.md](json_contract.md).

---

## Health

### `GET /healthz`
Liveness.

`200` → `{ "status": "ok" }`

---

## Projects

### `POST /v1/projects`
Create a project.

```json
{ "name": "Inbox app" }
```
`201` → `{ "id": "p_7", "name": "Inbox app", "createdAt": "..." }`

### `GET /v1/projects/{project_id}`
Project metadata.

`200` → `{ "id", "name", "createdAt", "updatedAt", "latestCheckpoint" }`
`404` → not found

### `GET /v1/projects/{project_id}/contract`
Current contract, all nodes.

`200` → contract object
`404` → not found

---

## Nodes

### `PUT /v1/projects/{project_id}/nodes`
Batch upsert. Per-node last-write-wins.

```json
{ "nodes": [ { "id", "type", "version", "updatedAt", "payload" } ] }
```
`200` → `{ "accepted": ["n1"], "rejected": [], "checksums": { "n1": "sha256:..." } }`
`409` → `{ "rejected": [ { "id": "n1", "reason": "stale_version", "server": { } } ] }`
`422` → validation failure

Checksums are computed server-side and returned; clients do not send them.

### `DELETE /v1/projects/{project_id}/nodes/{node_id}`
Remove a node.

`204` → deleted
`404` → not found

### `POST /v1/projects/{project_id}/validate`
Run V1–V12 without writing.

`200` → `{ "valid": true }`
`200` → `{ "valid": false, "violations": [ { "rule": "V10", "where": "ellipse_2", "message": "..." } ] }`

---

## Checkpoints

### `POST /v1/projects/{project_id}/checkpoints`
Freeze the current contract.

`201` → `{ "checkpoint": "cp_006", "createdAt": "..." }`
`409` → contract invalid

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
    { "name": "Home.generated.kt",  "target": "kotlin", "bytes": 1840 },
    { "name": "home.generated.css", "target": "css",    "bytes": 920 }
  ]
}
```
`409` → `{ "error": "dirty_contract" }` — unsynced nodes present
`422` → contract invalid

### `GET /v1/projects/{project_id}/artifacts/{name}`
Fetch one generated file.

`200` → file body, `text/plain`
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
{ "error": "stale_version", "message": "...", "requestId": "req_8f3a" }
```

| Status | Meaning |
|---|---|
| `400` | Malformed request |
| `401` | Missing or invalid credentials |
| `404` | Not found |
| `409` | Conflict — stale version, dirty contract, invalid checkpoint |
| `413` | Payload too large |
| `415` | Unsupported media type |
| `422` | Contract validation failed |
| `500` | Unhandled |
