# Frontend rules — Web

Normative rules for `apps/web`. Not a tutorial. See
[frontend-architecture.md](frontend-architecture.md) for why this surface
exists, [repository-layout.md](repository-layout.md) for the file boundary,
and [`json_contract.md`](../json_contract.md) for the wire format — this
document does not repeat any of them.

---

## 1 · The canvas

The canvas is a DOM element whose aspect ratio is locked to
`reference.w / reference.h` from the contract (375 / 667 in the current
fixture). Everything drawn inside it is positioned in `%`, matching the
generated CSS exactly:

```css
.canvas { position: relative; width: 100%; aspect-ratio: var(--frame-w) / var(--frame-h); overflow: hidden; }
```

`--frame-w` / `--frame-h` are already declared in `src/styles/tokens.css`,
sourced from the contract's `reference` object — do not hardcode `375`/`667`
a second time anywhere else.

1. Zoom and pan transform a **wrapper** around the canvas, never the canvas
   itself. The canvas's own box always equals 100% of its layout slot.
2. Every rendered node carries both `ikk-node` and its `{type}_{slug}` class,
   per `packages/codegen/README.md` — the editor's live DOM and the generated
   HTML use the same class contract.
3. The editor's own node renderer must produce the same visual result as
   `packages/codegen/generated/web/home.generated.css` for the same contract.
   Divergence here is a Parity Gate (`roadmap-frontend.md` §G) failure.
4. Zoom and pan are **local UI state** — per-device, per-viewer, never
   synced. They live in the wrapper component's own state (module state or a
   signal, not the contract), never in a `DesignNode`, and never pass through
   `markDirty()`. A node's `rect` is unaffected by anyone's zoom level; only
   a `MoveTool`/`ShapeTool` gesture changes it.

---

## 2 · Pointer → contract normalization

**Owner:** the active `ToolControl` (`MoveTool`, `ShapeTool`, `TextTool`,
`ImageTool` — `component-model.md` §4). No other layer performs this
conversion, and it never crosses the network as anything but the result.

1. On `pointerdown`, capture the canvas's `getBoundingClientRect()` once for
   the gesture. Re-read it on scroll/resize, never mid-drag.
2. Convert the raw event point to canvas-local pixels **before** converting
   to percent — invert any zoom/pan transform on the wrapper first:
   ```
   localPx = (screenPx - wrapperTransform.translate) / wrapperTransform.scale
   xPct    = round1((localPx.x - canvasRect.left) / canvasRect.width  * 100)
   yPct    = round1((localPx.y - canvasRect.top)  / canvasRect.height * 100)
   ```
3. Round to **one decimal place** at this point, not later — `json_contract.md`
   §5 defines the field precision; nothing downstream re-rounds it.
4. `rect.unit` is always written as `"%"`. No other unit is ever produced by
   this layer.
5. Non-geometric lengths (`radius`, `stroke.width`, `text.size`) are written
   as **raw numbers, unconverted** — §5 "Units" already governs these; this
   layer does not touch them.
6. The backend is not a second place this conversion happens. It validates
   shape (`POST /v1/projects/{id}/validate`) and rejects; it does not repair
   or re-derive geometry.

---

## 3 · Mutation path

Every edit routes through `markDirty()` on the element — `roadmap-frontend.md`
F1's own rule. A field that can change without it is a field sync silently
drops. This file does not restate the sync policies (`F3` /
`json_contract.md` §11); it only says where geometry enters that path.

---

## 4 · File boundary

| Path | Written by |
|---|---|
| `apps/web/src/generated/*.generated.css` | codegen only — never edited here |
| `apps/web/src/**` (everything else) | this app |

Never import a `.generated.*` file's contents into editor source expecting to
mutate it. The editor writes the contract; codegen writes generated files;
they do not share a writer. See `repository-layout.md` §"Generated and
authored code".
