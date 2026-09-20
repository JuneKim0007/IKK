# Design — planned work

Design for the open items in [roadmap-frontend.md](../roadmap-frontend.md)
(F1, F2, F3, G) and [roadmap-backend.md](../roadmap-backend.md) (B1–B3).

This file is **design, not rules**. Normative constraints stay in
[frontend-android.md](frontend-android.md), [frontend-web.md](frontend-web.md)
and [json_contract.md](../json_contract.md); this document decides how the
remaining work satisfies them, and in what order. When an item ships, its
section is deleted from here — same rule the roadmaps use.

---

## 1 · Sequencing

The 37 open items are not independent. Three of them are load-bearing: build
them in the wrong order and the rest gets built twice.

```
        F3.1 SyncQueue (per-node protocol)
              │
      ┌───────┴────────┐
      │                │
 F1.14 command    F2.1–F2.12
   stack           Android editor
      │                │
      └───────┬────────┘
              │
        G.1 golden harness
              │
        G.2–G.9 goldens + CI
```

**Why this order.** `F1.14` (undo) and every Android mutation both write
through the sync path, so the sync contract must settle first or both get
rewritten. The parity gate `G` diffs two renderers, so it needs a second
renderer to exist — `G.1` cannot be built before `F2.2`. Backend `B1`–`B3` are
independent of all of it and can run in parallel.

| Phase | Items | Blocked by | Parallel with |
|---|---|---|---|
| 0 | F3.1–F3.3 sync protocol | — | B1–B3 |
| 1 | F1.14, F3.4–F3.7 | phase 0 | F2.1–F2.5 |
| 2 | F2.1–F2.12 Android editor | F3.1 | F1.21–F1.24 |
| 3 | G.1–G.9 parity gate | F2.2 | — |
| 4 | F3.8, F1.22, F1.23 | phases 0–3 | — |

`F1.22` (outlined triangles) and `F1.23` (arbitrary-angle lines) are last
deliberately: both change the contract's geometry surface, and doing that
before the parity gate exists means shipping a cross-surface geometry change
with nothing to catch divergence.

---

## 2 · F3 · Client sync

### The problem with the current design

`apps/web/src/core/sync.js` sends the **whole contract** on every flush:

```js
PUT /v1/projects/{id}/contract   body: entire contract
```

That satisfies F3.2 (debounce) and F3.3 (one request per flush) and it is the
right thing for a single client. It cannot satisfy **F3.8** — "edit different
nodes on two clients, both converge" — and no amount of tuning will fix it.

The endpoint is not unguarded: `docs/api.md` says a submission whose checkpoint
is *older* than the project's current checkpoint is rejected in full with
`409 stale_checkpoint`. But that guard is checkpoint-granular. Two clients
sitting on the **same** current checkpoint — the normal case, since a
checkpoint is only cut by `[Generate]` — both pass it. Each then replaces the
complete working contract, so the second write reinstates its own stale copy
of the node the first client just edited. The edit is not conflicted, not
rejected, and not reported. It is silently gone.

The backend already has the endpoint that solves this:

```
PUT /v1/projects/{id}/nodes     409 on stale node version
```

Per-node upsert with the per-node `version` field that `DesignNode.touch()`
already maintains. The conflict surface moves from the document to the node,
which is where the contract models it.

**Decision: F3.1 targets `PUT /nodes`, not `PUT /contract`.** Whole-contract
PUT stays for initial load and for `[Generate]`, where a document-level
checkpoint is what you actually want.

### SyncQueue

One implementation per surface, one shared state machine. The states are the
four `status` values the UI already renders (F3.7).

```
        edit                    flush ok, queue empty
 clean ──────→ pending ──────────────────────────────→ clean
                 │  │
     flush 409   │  └── flush ok, queue non-empty ──→ pending
                 ↓
             conflict ──resolve──→ pending
                 │
        network error
                 ↓
              offline ──reconnect──→ pending
```

| Operation | Contract |
|---|---|
| `enqueue(nodeId)` | Idempotent. Records the node id only, never a value — the value is read from the store at flush time, so N edits to one node coalesce into one send. |
| `flush()` | Reads current node states for all queued ids, sends one `PUT /nodes`, awaits ack. Never overlaps: a flush in progress makes the next one wait, not race. |
| `onAck(accepted, rejected)` | `PUT /nodes` is **partial-success**: it returns `accepted`, `rejected` and per-node `details`. Dequeue only the accepted ids, and only if the node's version is unchanged since send — the existing `sentVersions` check in `sync.js`, which must survive the rewrite. It is what stops a mid-flight edit from being dropped. |
| `onConflict(rejected)` | Per-node, driven by the `details[].reason == "stale_version"` entries. Default: remote wins, local edit re-applied on top as a new version. |

**Why coalesce by id rather than queue mutations.** A drag emits ~60
mutations/second (F3.2). Queuing mutations means the queue grows with gesture
duration and the flush payload grows with it. Queuing *ids* means a 10-second
drag on one node sends exactly one node. The cost is losing intermediate
states — which is correct, nobody wants the 600 intermediate positions.

**Conflict policy, and why it is deliberately dumb.** Remote-wins with local
replay is not a merge. A real merge needs field-level causality (CRDT or
OT-style), which is weeks of work and is listed out of scope
(`roadmap-backend.md`: real-time co-editing). Remote-wins converges, is two
dozen lines, and is honest: F3.7 shows the user a `conflict` state so a lost
edit is visible rather than silent. That is the whole improvement over today.

### Per-item

| Item | Design |
|---|---|
| **F3.1** | `SyncQueue` above. Web: `apps/web/src/core/sync.js` rewrite. Android: `apps/android/data`, same state machine, Kotlin. |
| **F3.2** | Debounce 400ms, unchanged. Add a **max-wait of 2s** — a continuous drag never stops resetting the timer, so pure debounce means a 30-second drag syncs nothing for 30 seconds. |
| **F3.3** | One `PUT /nodes` per flush carrying every queued node. Already the shape of the endpoint. |
| **F3.4** | Web: `localStorage`, queue ids + the contract, written on enqueue. Android: DataStore, written on enqueue, not on process death — `onStop` is not guaranteed to run. |
| **F3.5** | `visibilitychange → hidden` flush. Already present; keep it. Use `fetch(..., {keepalive: true})` so the request survives the tab closing. |
| **F3.6** | `WorkManager` periodic reconcile, 15-minute floor (the platform minimum). **It repairs drift; it is not the sync path.** Detect drift cheaply with the server-computed `checksums` that `PUT /nodes` already returns — compare against locally recomputed ones instead of refetching the contract. Instrument it: if reconcile regularly finds queued work, the debounce is broken and that is a bug, not a safety net doing its job. |
| **F3.7** | The five states above, rendered in the existing status bar. `conflict` is new and must be visually distinct from `failed` — one is "your edit lost", the other is "retry pending". |
| **F3.8** | Two-client test. See §6, it is a gate test, not a unit test. |

### Failure modes

| Mode | Consequence | Mitigation |
|---|---|---|
| Flush storm on reconnect | Every queued node flushes at once after an outage | Cap payload at N nodes/request, paginate the flush |
| Queue grows unbounded offline | Memory, then a multi-MB first request | Cap the queue; past the cap, force a whole-contract PUT instead |
| Clock skew on `updatedAt` | Two clients disagree on ordering | Order by server-assigned `version`, never by `updatedAt`. `updatedAt` is for humans. |
| Retry storm against a down backend | Hammers a recovering service | Exponential backoff with jitter, capped at 30s |

---

## 3 · F1.14 · Command stack

Today undo is a stack of whole-contract snapshots. The roadmap already states
the defect: restoring a snapshot does not bump per-node `version`, so an undo
that reverts a node the server already has produces a node identical in
version to the one the server holds. With per-node sync (§2) the server's
`version` check sees no change and the undo never leaves the client.

**Design.** Replace snapshots with commands:

```
interface Command {
  apply(store)    // returns the set of touched node ids
  invert()        // returns the inverse Command
}
```

`undo()` applies `invert()` **through the normal mutation path** — the same
`markDirty()` / `touch()` route a user edit takes. That is the entire point:
an undo becomes an ordinary versioned edit, indistinguishable to sync from a
forward edit, so it syncs for free.

Commands needed, one per mutating operation: `CreateNode`, `DeleteNode`,
`SetRect`, `SetProperty`, `SetText`, `Reorder`, `Rename`.

**Trade-off.** Snapshots are trivially correct and O(document) in memory;
commands are O(delta) but every new mutation must remember to add a command
or it becomes silently un-undoable. Mitigate with a test that asserts every
`markDirty()` call site has a corresponding command — otherwise this rots.

---

## 4 · F2 · Android editor

### State ownership

The single most important decision here, because getting it wrong is what
makes zoom/pan leak into the contract (forbidden by
`frontend-android.md` §1.5).

| State | Owner | Lifetime | Synced |
|---|---|---|---|
| Contract nodes | `ContractStore` in `apps/android/data` | Process, persisted | **Yes** |
| Selection | `EditorViewModel` | Process | No |
| Active tool | `EditorViewModel` | Process | No |
| Zoom / pan | Canvas composable `remember` | Composition | **Never** |
| Sheet detent | Scaffold state | Composition | No |

Zoom and pan live in the composable, not the ViewModel, so there is no
plausible path from a pinch gesture to `markDirty()`. That is a structural
guarantee, not a convention someone has to remember.

### Composable tree

```
EditorScreen
├── ContextualAppBar          F2.10
├── CanvasWrapper             F2.1  ← graphicsLayer(scale, translate)
│   └── BoxWithConstraints    aspectRatio locked, rel() from templates/rel.kt.txt
│       ├── NodeRenderer × n  F2.2–F2.4
│       └── SelectionOverlay  F2.5, F2.6
├── BottomToolRail            F2.9
└── BottomSheetScaffold       F2.10
    ├── InspectorPane  (peek / half)   NudgePad F2.8
    └── LayersPane     (full)          F2.11
```

`CanvasWrapper` holds the transform; `BoxWithConstraints` is inside it and
therefore untransformed, so its `maxWidth`/`maxHeight` stay the locked-aspect
reference box exactly as `frontend-android.md` §1.1 requires.

### Per-item

| Item | Design note |
|---|---|
| **F2.1** | `detectTransformGestures` on the wrapper. Clamp scale to 0.25–4.0; clamp translation so the canvas cannot be panned entirely off screen. |
| **F2.2** | One `@Composable fun Node(node: DesignNode)` dispatching on the sealed class — exhaustive `when`, no `else`, so a new node type is a compile error rather than a blank rectangle. |
| **F2.3** | `RoundedCornerShape(percent = 50)`. Enforce with a lint rule or a grep test banning `CircleShape` under `apps/android` — the failure is invisible on a square node and the bug reappears otherwise. |
| **F2.4** | `.clip(shape)` **before** the painter modifier. Same enforcement problem; the golden `G.5` is what actually catches it. |
| **F2.5** | 48dp touch target, 14dp visual. Handle hit-testing happens in wrapper space *before* the inverse transform, so targets stay 48dp on screen at any zoom. Getting this backwards makes handles untappable when zoomed out. |
| **F2.6** | `detectDragGestures`. Capture `boundsInParent()` once on `dragStart` per §2.1 — re-querying mid-drag reads a mutated layout and the node jumps. |
| **F2.7** | Double-tap → `BasicTextField` overlaid at the node's rect. Canvas tap clears focus and dismisses the IME. |
| **F2.8** | `NudgePad`: 1dp per tap, auto-repeat after 400ms at 20/s. Each tap is one `SetRect` command (§3), but **coalesce a repeat burst into one command** or undo replays 60 single-dp steps. |
| **F2.9** | Bottom rail, `ShapeTool` popup remembers last selection (`component-model.md` §4). |
| **F2.10** | `BottomSheetScaffold`, peek ≈ 96dp, half = 50%. Contextual app bar swaps to selection actions when selection is non-empty. |
| **F2.11** | Layers at full detent. Reorder = drag; rename enforces V14 (non-blank). |
| **F2.12** | `SupportingPaneScaffold` for Expanded width. Inspector becomes a side pane; the canvas composable is unchanged — this is a container swap only, per `component-model.md` §4 "keep the divergence at one node of the tree". |

### Testing

Currently zero tests exist in `apps/android/app` — `testDebugUnitTest` reports
`NO-SOURCE` and CI runs it anyway. Three layers, cheapest first:

| Layer | Covers | Runs in CI |
|---|---|---|
| JVM unit | touch→percent normalization (§2 of frontend-android.md), capability predicates, command invert | Yes |
| Robolectric | composable state, no device | Yes |
| `androidTest` Compose | gestures, IME, sheet detents | No — local only, arm64 image |

The normalization math is pure and is where the subtle bugs live (zoom not
divided out, rounding at the wrong step). It must be a JVM test, not a device
test, so it runs on every push.

---

## 5 · G · Parity gate

### What makes this hard

The gate diffs a DOM/CSS render against a Compose render. They will never be
bit-identical: different rasterisers, different text shaping, different
antialiasing. A naive image diff fails on every run and gets disabled within a
week — which is the usual fate of golden tests and the thing to design against.

### Harness (G.1)

```
fixture.json
    │
    ├─→ headless Chrome ──→ web.png
    │      (apps/web, fixed viewport, fonts embedded, DPR=1)
    │
    └─→ Compose screenshot test ──→ android.png
           (fixed size, Robolectric or emulator, fonts embedded)
                  │
            compare(web.png, android.png, tolerance)
                  │
            pass / fail + diff.png artifact
```

Four things make it stable, and all four are required:

1. **Fixed viewport at DPR 1.** The reference viewport from the contract, no
   device scaling. Any DPR ≠ 1 introduces resampling differences.
2. **Embedded fonts.** Ship one WOFF/TTF and load it on both surfaces. System
   fonts differ per OS and per CI image; this is the single largest source of
   spurious diffs.
3. **Perceptual comparison, not exact.** Compare in a perceptual space with a
   per-pixel threshold, then fail on the *count* of differing pixels, not on
   any single one.
4. **Tolerance written down (G.7)** before the goldens land, not tuned
   afterwards to whatever the current output happens to be.

### Tolerance policy (G.7)

| Region | Threshold | Why |
|---|---|---|
| Geometry edges | ≤ 1px position, ≤ 2% area | Rounding in `rel()` at different pixel grids |
| Flat fills | exact colour match | No excuse for a wrong fill; contract stores hex |
| Text | ≤ 8% differing pixels in the text bounding box | Shaping and hinting genuinely differ; position must still match |
| Antialiased edges | excluded | 1px dilation of every edge mask before comparison |

Text is the honest compromise: asserting glyph-identical rendering across two
engines is not achievable, so the gate asserts text is *in the right box at
the right size*, and `G.4` specifically targets line-height and `pre-wrap`
because those shift the box, which is detectable.

### The goldens

`G.2`–`G.6` and `G.9` each exist to catch one named defect. That is the right
design — a golden that "checks the editor looks right" tells you nothing when
it fails.

| Golden | Catches |
|---|---|
| G.2 rect + stroke | CSS `box-sizing` — border inside vs outside the box |
| G.3 non-square ellipse | `CircleShape` producing a pill (F2.3) |
| G.4 multi-line text | `pre-wrap` and line-height divergence |
| G.5 image + radius | `.clip()` after the painter (F2.4) |
| G.6 text-on-shape × 3 aligns | Baseline and alignment box |
| G.9 drag while zoomed | Transform not divided out of the percent conversion |

**G.9 is not an image test.** It asserts that dragging a node to a visual
position while zoomed writes the same `%` as dragging it there un-zoomed. That
is a numeric assertion on the contract, runnable as a JVM/unit test, and it is
the cheapest of the nine. Build it first — it catches the most likely bug in
F2.1 and needs no rendering at all.

### CI (G.8)

Goldens run on **Linux x86_64 with a pinned container image**. Fonts,
rasteriser and browser version are then fixed; a macOS runner and a Linux
runner will not agree, and letting both run means the gate fails on whichever
machine did not generate the goldens.

Android side in CI uses Robolectric with native graphics rather than an
emulator — the repo's system image is `arm64-v8a`, which will not run on
x86_64 runners at all. Device-based screenshot tests stay local.

Diff images upload as artifacts on failure. A red gate with no picture of what
changed is a gate people learn to ignore.

---

## 6 · F3.8 · Two-client convergence

Not a unit test. Sequence:

```
A: PUT /nodes  {rect_1 @v3}      → 200, v4
B: PUT /nodes  {text_1 @v2}      → 200, v3
A: GET /contract                 → must contain text_1 @v3
B: GET /contract                 → must contain rect_1 @v4
```

Run it against the real backend via `make e2e`. With the whole-contract PUT of
today this test fails, which is the point of §2 — it is the executable form of
that finding.

---

## 7 · Backend

| Item | Design |
|---|---|
| **B1** PostgreSQL in CI | Service container, run the existing integration suite twice — once H2 compatibility mode (fast, every push), once real PostgreSQL. H2's PostgreSQL mode is a compatibility layer, not PostgreSQL: it diverges on `JSONB` operators, `ON CONFLICT` and collation. Run both, keep H2 as the fast gate. |
| **B2** Per-user authz | Replace the shared bearer with per-user identity and a project-ownership check. Ordering matters: **authorization must land before the MCP endpoint or any hosted deploy**, because both widen who can reach `PUT /nodes`. Add a test asserting 403 for a non-owner — a 401 test alone passes with no authz at all. |
| **B3** Retention | Checkpoints are immutable and generated artifacts are derivable from them, so retention differs: keep checkpoint metadata indefinitely (cheap, it is the audit trail), garbage-collect artifact blobs past N checkpoints (expensive, and regenerable). Decide N when multi-screen projects exist; the schema should carry the retention marker now so the migration is not a rewrite later. |

---

## 8 · Open risks

| Risk | Why it matters | Where it lands |
|---|---|---|
| Golden tolerance is guessed, not measured | Too tight → flaky gate, disabled within a week. Too loose → catches nothing. | Measure real cross-surface variance on G.2 first, then set G.7 from data |
| Remote-wins loses edits | Converges, but a user's work vanishes | Visible `conflict` state (F3.7) is the mitigation, not a fix. Revisit only if users hit it. |
| Android editor is ~12 items with no tests today | Largest single block of work on the board, on the surface with the weakest safety net | JVM-testable normalization first (§4 Testing), UI last |
| F1.22 / F1.23 change geometry after the gate is built | Goldens must be regenerated, and a regenerated golden hides a regression | Land both with a new golden each, reviewed as a picture |
