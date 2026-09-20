# Roadmap

Full-coverage build plan for both surfaces of IKK: a mobile design tool whose
contract is the source of truth for generated Android and web output.

Companion to [component-model.md](component-model.md), which defines the class
tree this plan builds. Every item below traces to a node in that tree.

**100 tracked items.** Nothing in the component model is unaccounted for.

---

## 0 · Definition of done

The project is done when all four hold:

1. A designer draws a screen on either surface, and the other surface shows the
   same screen after a sync
2. `[Generate]` produces `.kt` and `.css` that render **visually identical to
   the editor canvas at the reference viewport, at default font scale**, within
   the golden tolerance in Phase 4.

   Not "pixel-identical": divergence D1 in `json_contract.md` already concedes
   that Android `sp` reflows with the user's font-scale setting while web `px`
   does not. Demanding pixel equality would be demanding something the spec
   says is false, and the gate would never go green.
3. Re-generating never destroys agent or hand-written code
4. Every `DesignNode` type round-trips through JSON without loss

Anything that does not serve one of those four is out of scope for v1.

---

## 1 · Coverage matrix

The axes that must all be filled. A cell is done when it has an implementation
**and** a test.

| Component | Model | Web render | Web edit | Android render | Android edit | Kotlin emit | CSS emit | Sync |
|---|---|---|---|---|---|---|---|---|
| RectNode | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| EllipseNode | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| TextNode | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| ImageNode | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| TextCarrier (on shapes) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

| Control | Behaviour | Web | Android |
|---|---|---|---|
| MoveTool | ☐ | ☐ | ☐ |
| ShapeTool (group) | ☐ | ☐ | ☐ |
| TextTool | ☐ | ☐ | ☐ |
| ImageTool | ☐ | ☐ | ☐ |
| Inspector | ☐ | ☐ | ☐ |
| Layers | ☐ | ☐ | ☐ |
| Delete / Undo | ☐ | ☐ | ☐ |
| Generate / Import | ☐ | ☐ | ☐ |

---

## 2 · Tracks

Three tracks, two of which can run in parallel once Phase 1 lands.

```
Phase 0 ──▶ Phase 1 ──┬──▶ Phase 2  web editor    ──┐
    foundations  core │                              ├──▶ Phase 4 parity
                      └──▶ Phase 3  android editor ──┘         │
                                                               ▼
                    Phase 5 backend + codegen ──▶ Phase 6 sync ──▶ Phase 7 generate
                           (FastAPI, Python)
```

Phases 2 and 3 are genuinely parallel — different people, no shared files
except `core`. Phase 4 is a gate, not a task: it cannot start until both land.

---

## Phase 0 · Foundations

> **Status:** 1, 3, 4, 8, 10 done · 2 deferred with reason · 5, 6, 7, 9 open
> (they belong to whoever takes the `frontend/web` branch).

Everything that makes the later phases possible. No product value; skipping it
costs more than doing it.

- [x] **1.** Replace the absolute JDK path in `gradle.properties` with a Gradle
      toolchain so the repo builds on any machine
- [~] **2.** ~~Promote `:core` to Kotlin Multiplatform~~ — **deferred, on purpose.**
      KMP buys sharing with a *Kotlin* target. The web surface is TypeScript and
      the backend is JVM, so a plain Kotlin/JVM `:core` already serves both
      consumers; Android depends on it directly. Revisit only if a Kotlin/JS or
      Compose Multiplatform target appears. Doing it now is ceremony with no consumer
- [x] **3.** Add `kotlinx.serialization` to `:core` with the JSON format pinned
      (`encodeDefaults = true`, `explicitNulls = true`)
- [x] **4.** Add `schemaVersion` constant and a migration hook that throws loudly
      on an unknown version
- [ ] **5.** Create `frontend/web/` with a real build (bundler, TS, dev server)
- [ ] **6.** Extract design tokens to one source consumed by both surfaces —
      colours, type scale, spacing, the 375×667 reference viewport
- [ ] **7.** Pin Roboto as a self-hosted webfont so text metrics match Android
- [x] **8.** CI: `./gradlew test` on every push
- [ ] **9.** CI: web typecheck + unit tests on every push
- [x] **10.** `docs/` index linking component-model, roadmap, and the prototypes

**Exit:** both builds green in CI on a fresh clone.
**Effort:** 1–2 days.

---

## Phase 1 · Core model

> **Status:** complete. `:core:test` — 23 tests green.
> Lives in `core/src/main/kotlin/com/ikk/core/contract/`.

Pure Kotlin, no Android, no DOM. Lives in `core/src/commonMain`.

- [x] **11.** `RelRect { x, y, w, h }` as fractions, with `translate`, `resize`,
      `contains`, `clampTo`
- [x] **12.** `Color` value type with hex parse/format, round-trip tested
- [x] **13.** `Stroke { color, width }`, alignment fixed as INSIDE
- [x] **14.** `TextPayload { value, size, align, color, weight, maxLines }`
- [x] **15.** `BaseUIComponent` — id, type, version, updatedAt, markDirty
- [x] **16.** `DesignNode` — geometry, z, visible, opacity, hitTest, bounds
- [x] **17.** Capability **predicates** on `DesignNode` — `acceptsText`,
      `acceptsFill`, `radiusEditable`. Marker interfaces would carry no
      information: the envelope is uniform, so every node has every field.
      What the editor needs is "should this control be shown"
- [x] **18.** `RectNode`
- [x] **19.** `EllipseNode`
- [x] **20.** `TextNode`
- [x] **21.** `ImageNode` + `AssetRef`
- [x] **22.** `Contract` root: version, checkpoint, reference viewport,
      `Map<String, DesignNode>`

**Tests, not optional:**

- [x] **23.** Round-trip property test per node type — `fromJson(toJson(x)) == x`
- [x] **24.** `markDirty()` bumps `version` and stamps `updatedAt` on every setter
- [x] **25.** `text = null` and `text = ""` are distinguishable after a round trip

**Exit:** `:core:test` green; a hand-written contract JSON deserialises.
**Effort:** 2–3 days.

---

## Phase 2 · Web editor

`frontend/web/`. Consumes `:core` types via generated TS definitions or a
hand-kept mirror — decide in Phase 0 item 5.

**Renderer**

- [ ] **26.** Canvas surface at the reference viewport, zoom, pan
- [ ] **27.** `RectNode` renderer — fill, stroke, radius, opacity
- [ ] **28.** `EllipseNode` renderer
- [ ] **29.** `TextNode` renderer — explicit line-height, `pre-wrap`
- [ ] **30.** `ImageNode` renderer — `object-fit`, clipped corners
- [ ] **31.** Text-on-shape rendering for any `TextCarrier`
- [ ] **32.** Selection outline + 8 resize handles

**Interaction**

- [ ] **33.** Click to select, click-empty to deselect
- [ ] **34.** Drag to move, clamped to frame
- [ ] **35.** Drag handles to resize, all 8 directions
- [ ] **36.** Draw-by-drag creation for each tool
- [ ] **37.** Double-click to edit text in place
- [ ] **38.** Arrow-key nudge, shift = 10
- [ ] **39.** Delete, undo (command stack, not JSON snapshots)

**Chrome**

- [ ] **40.** Toolbar with `ShapeTool` group (rect + ellipse, remembers last)
- [ ] **41.** Inspector: position, size, fill, stroke, radius, opacity
- [ ] **42.** Inspector: text section, shown only when `text != null`
- [ ] **43.** Inspector: "Add text to this shape" when `text == null`
- [ ] **44.** Layers panel: select, rename, visibility, reorder
- [ ] **45.** Status bar: frame size, zoom, selection readout

**Exit:** a designer builds the seeded screen from an empty canvas, unaided.
**Effort:** 5–7 days.

---

## Phase 3 · Android editor

`app/` + a new `:feature-editor` module. Compose, touch-first.

**Renderer**

- [ ] **46.** Canvas composable at reference viewport, pinch-zoom, two-finger pan
- [ ] **47.** `RectNode` renderer — `.background(shape)`, `.border()` inside
- [ ] **48.** `EllipseNode` — `RoundedCornerShape(percent = 50)`, **not**
      `CircleShape`
- [ ] **49.** `TextNode` — explicit `lineHeight`, `sp` sizing decision documented
- [ ] **50.** `ImageNode` — `.clip()` **before** the painter in the chain
- [ ] **51.** Text-on-shape for any `TextCarrier`
- [ ] **52.** Selection outline + handles, 48dp touch targets, 14dp visuals

**Interaction**

- [ ] **53.** Tap to select, tap-empty to deselect
- [ ] **54.** Drag to move
- [ ] **55.** Drag handles to resize
- [ ] **56.** Tap-tool-then-tap-canvas creation
- [ ] **57.** Double-tap to edit text, canvas scrolls clear of the keyboard
- [ ] **58.** Nudge pad — 1dp per tap, auto-repeat on hold
- [ ] **59.** Long-press context menu; undo in the app bar

**Chrome**

- [ ] **60.** Bottom tool rail with `ShapeTool` popup
- [ ] **61.** Contextual app bar on selection
- [ ] **62.** Bottom sheet, peek and half detents, chip navigation
- [ ] **63.** Inspector sections mirroring web: size, fill, text
- [ ] **64.** Layers at full detent, 52dp rows, long-press reorder
- [ ] **65.** `SupportingPaneScaffold` for Expanded window size class

**Exit:** the same screen built on a phone, one-handed where plausible.
**Effort:** 8–10 days.

---

## Phase 4 · Parity gate

A gate, not a feature. Nothing past here until it passes.

- [ ] **66.** Golden-image harness: render a contract on both surfaces, diff
- [ ] **67.** Golden: rect with stroke — catches the `box-sizing` divergence
- [ ] **68.** Golden: non-square ellipse — catches `CircleShape`
- [ ] **69.** Golden: multi-line text — catches `pre-wrap` and line-height
- [ ] **70.** Golden: image with radius — catches modifier order
- [ ] **71.** Golden: text-on-shape, all three alignments
- [ ] **72.** Golden: opacity compositing
- [ ] **73.** Tolerance policy written down — what % pixel delta fails the build
- [ ] **74.** Goldens wired into CI, failing the build on drift
- [ ] **75.** Divergence register in `docs/` — every accepted difference, with why

**Exit:** all goldens within tolerance in CI.
**Effort:** 3–4 days. **Do not skip.** This phase is the entire thesis.

---

## Phase 5 · Backend and codegen — FastAPI

`backend/`, Python + FastAPI. Feature-first: every slice owns its router,
schemas, service, models and tests. `app/contract/` is a shared kernel, not a
feature — sync, checkpoints and codegen all depend on it.

**Codegen lives here and nowhere else.** The backend cannot call Kotlin, so the
emitters are Jinja2 templates in this phase rather than methods on `DesignNode`.
*Rendering* a contract at runtime (both clients) and *generating* source from it
(backend only) are different jobs; Android never needs to emit Kotlin source.

**Foundations**

- [ ] **76.** `pyproject.toml`, app factory, `settings.py`, `GET /healthz`
- [ ] **77.** Database, session dependency, migrations
- [ ] **78.** `app/contract/models.py` — pydantic mirror of
      `docs/json_contract.md`. Must reject unknown fields (V11)
- [ ] **79.** `app/contract/validation.py` — rules V1–V12
- [ ] **80.** `app/contract/canonical.py` — canonical serialisation and
      checksum. **Server-owned**: clients send payload, the server computes and
      returns the checksum, so canonical serialisation exists in one place
      rather than three
- [ ] **81.** `docs/fixtures/` corpus + a conformance test that Kotlin,
      TypeScript and Python all run against the same files

**Features**

- [ ] **82.** `features/projects` — create, read, read contract
- [ ] **83.** `features/sync` — batch node upsert, per-node LWW, **409 on a
      version that is not greater than stored**
- [ ] **84.** `features/checkpoints` — immutable snapshots, `cp_00N`
- [ ] **85.** `features/assets` — image upload and serve for `ImageNode`

**Codegen**

- [ ] **86.** `templates/compose.kt.j2` — `Modifier.rel`, `.background`,
      `.border` inside, `RoundedCornerShape(percent = 50)` for ellipses
- [ ] **87.** `templates/styles.css.j2` — percentages, `box-sizing: border-box`,
      explicit `line-height`, `white-space: pre-wrap`
- [ ] **88.** `templates/markup.html.j2` — structure only, no inline geometry
- [ ] **89.** Golden test: every fixture renders to the expected artifacts

**Operations**

- [ ] **90.** AuthN/AuthZ, structured logging with request ids, integration
      tests against a real database rather than mocks, CI job

**Exit:** both clients push and pull a contract; `/import` returns artifacts.
**Effort:** 6–8 days.

---

## Phase 6 · Client synchronisation

- [ ] **91.** `SyncQueue` — enqueue, flush, onAck, onConflict
- [ ] **92.** `Debounced(400ms)` as the default for `DesignNode`. A drag emits
      ~60 mutations a second; this is not an optimisation
- [ ] **93.** Batching — one request per flush, not one per node
- [ ] **94.** Offline queue survives reload (web) and process death (Android)
- [ ] **95.** Web: flush on `visibilitychange → hidden`
- [ ] **96.** Android: `WorkManager` reconcile job. The cron is a **reconciler**
      that repairs drift, not the primary path — if it is doing the real
      syncing, the debounce is broken
- [ ] **97.** Two-client test: edit different nodes, both converge

**Exit:** edit on a phone, see it on the web within one debounce plus latency.
**Effort:** 4–5 days.

---

## Phase 7 · Generate

- [ ] **98.** `POST /projects/{id}/generate` — cut a checkpoint, run codegen,
      return the artifact manifest
- [ ] **99.** `[Generate]` control on both surfaces, **disabled while the sync
      queue is dirty**. Importing against a stale contract produces output that
      does not match the screen, which users report as a broken generator
- [ ] **100.** Generated/authored boundary enforced: codegen writes only
      `*.generated.*`, never an authored file

**Exit:** press Import, get `.kt` and `.css` that pass the Phase 4 goldens.
**Effort:** 2–3 days.

---

## 3 · Schedule

| Phase | Effort | Cumulative | Parallel? |
|---|---|---|---|
| 0 Foundations | 1–2 d | 2 d | — |
| 1 Core model | 2–3 d | 5 d | — |
| 2 Web editor | 5–7 d | 12 d | ✔ with 3 |
| 3 Android editor | 8–10 d | 15 d | ✔ with 2 |
| 4 Parity gate | 3–4 d | 19 d | — |
| 5 Backend + codegen | 6–8 d | 27 d | ✔ with 2/3 |
| 6 Client sync | 4–5 d | 32 d | — |
| 7 Generate | 2–3 d | 35 d | — |

**Solo, sequential: ~35 working days.** Two people splitting web and Android
after Phase 1: **~23 days**.

---

## 4 · Cut lines

If the deadline compresses, cut in this order. Each line is a complete,
demonstrable product.

| Cut | Lose | Keep |
|---|---|---|
| **1** | Phase 6 sync | Manual export/import of the contract file |
| **2** | Android *editing* | Android as a read-only contract renderer — 2 days, still proves one contract two surfaces |
| **3** | Phase 5 persistence | Keep codegen — run FastAPI in-memory, contract posted per request |
| **4** | `ImageNode` | Rect, ellipse, text — the pipeline claim is intact |
| **5** | `EllipseNode` | Rect with radius covers most of it |

**Never cut:** Phase 1 round-trip tests, Phase 4 goldens, the
generated/authored file boundary. Those three are what make the result a system
rather than a demo.

---

## 5 · Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Renderer drift between surfaces | **High** | **High** | Phase 4 goldens in CI. This is why the gate exists |
| Compose Multiplatform web is Beta | Med | Med | Not on the critical path — web is TS, not Wasm |
| Python contract model drifts from Kotlin | **High** | **High** | `docs/fixtures/` corpus, run by all three implementations in CI |
| Text metrics differ despite same font | **High** | Med | Self-host Roboto, emit explicit line-height, golden-test it |
| Sync conflicts corrupt a screen | Low | **High** | Node-level LWW, checksum, immutable checkpoints to roll back to |
| Android editor overruns its estimate | **High** | Med | Cut line 2 is pre-agreed, not a crisis decision |
| Scope creep into behaviour/bindings | **High** | **High** | `Bindable` is explicitly out of v1. Say no twice |

---

## 6 · Out of scope for v1

Named so they can be declined quickly, not re-litigated:

- `GroupNode` and nesting
- `Bindable` — data binding, state, behaviour
- The AI implementation stage and its validation gates
- Real-time multi-user co-editing
- Components, variants, design-system tokens as user-facing features
- Animation, transitions, prototyping links
- iOS or desktop targets
