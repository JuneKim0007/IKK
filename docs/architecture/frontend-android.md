# Frontend rules — Android

Normative rules for `apps/android`. Not a tutorial. See
[frontend-architecture.md](frontend-architecture.md) for why this surface
exists, [repository-layout.md](repository-layout.md) for the file boundary,
and [`json_contract.md`](../json_contract.md) for the wire format — this
document does not repeat any of them.

---

## 1 · The canvas composable

The canvas is a `BoxWithConstraints` whose aspect ratio is locked to
`reference.w / reference.h`, using the **same** `rel()` extension function
codegen emits into `HomeLayout.generated.kt`:

```kotlin
BoxWithConstraints(modifier.aspectRatio(referenceW / referenceH)) {
    fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this
        .offset(x = maxWidth * x, y = maxHeight * y)
        .size(width = maxWidth * w, height = maxHeight * h)
    ...
}
```

Do not hand-roll a second layout formula for the editor's live render. The
canonical body lives at `packages/codegen/templates/rel.kt.txt` — copy it
verbatim; do not retype it. `packages/codegen/generate.test.mjs` asserts the
generator's emitted Kotlin contains this exact text, indented 8; when the
editor canvas lands, extend that same test (or add an Android-side one) to
assert the editor's copy matches too. If the editor and `packages/codegen`
ever compute `rel()` differently, they will drift silently — that test is
what catches it before a demo does.

1. Pinch-zoom and pan transform a **wrapper** around the canvas
   (`graphicsLayer` scale/translate), never the `BoxWithConstraints` itself.
   Its own `maxWidth`/`maxHeight` always reflect the locked-aspect box.
2. The editor's own node renderer must produce the same visual result as
   `packages/codegen/generated/android/HomeLayout.generated.kt` for the same
   contract. Divergence here is a Parity Gate (`roadmap-frontend.md` §G)
   failure.
3. `RoundedCornerShape(percent = 50)` for ellipses. **Never** `CircleShape` —
   it produces a pill on a non-square box (`json_contract.md` §8).
4. `.clip(shape)` before the painter on image nodes — modifier order is
   semantic (`json_contract.md` §10).
5. Zoom and pan are **local UI state** — per-device, per-viewer, never
   synced. They live in the composable's own state (`remember` /
   `ViewModel`), never in a `DesignNode`, and never pass through
   `markDirty()`. A node's `rect` is unaffected by anyone's zoom level; only
   a `MoveTool`/`ShapeTool` gesture changes it.

---

## 2 · Touch → contract normalization

**Owner:** the active `ToolControl` (`MoveTool`, `ShapeTool`, `TextTool`,
`ImageTool` — `component-model.md` §4). No other layer performs this
conversion, and it never leaves this layer as anything but the result.

1. Read touch position via `Modifier.pointerInput` against the canvas's own
   `LayoutCoordinates` (`boundsInParent()`), captured once per gesture on
   `dragStart` — not re-queried mid-drag.
2. Divide out the wrapper's zoom/pan (`graphicsLayer` scale/translate) before
   converting to percent:
   ```kotlin
   val localPx = (touchPx - wrapperTranslate) / wrapperScale
   val xPct = ((localPx.x - canvasBounds.left) / canvasBounds.width  * 100).round1()
   val yPct = ((localPx.y - canvasBounds.top)  / canvasBounds.height * 100).round1()
   ```
3. Round to **one decimal place** at this point — `json_contract.md` §5's
   field precision; nothing downstream re-rounds it.
4. `rect.unit` is always written as `"%"`.
5. Non-geometric lengths (`radius`, `stroke.width`, `text.size`) are written
   as **raw numbers in dp/sp, unconverted** — §5 "Units" governs these; do
   not multiply or divide by density here. Compose's own `.dp`/`.sp` handle
   density and font-scale at render time; the contract stores the number
   only.
6. The backend is not a second place this conversion happens. It validates
   shape and rejects; it does not repair or re-derive geometry.

---

## 3 · Touch targets

Selection and resize handles: **48dp** touch target, **14dp** visual size
(`roadmap-frontend.md` F2.5). The visual/target mismatch is intentional —
Android accessibility minimums, not a rendering bug.

---

## 4 · Mutation path

Every edit routes through the same `markDirty()` contract the web editor
uses. This file does not restate the sync policies (`F3` /
`json_contract.md` §11); it only says where geometry enters that path.

---

## 5 · File boundary

| Path | Written by |
|---|---|
| `apps/android/**/generated/*.generated.kt` | codegen only — never edited here |
| `apps/android/**` (everything else) | this app |

Never edit a `.generated.kt` file expecting the change to survive the next
`[Generate]`. See `repository-layout.md` §"Generated and authored code".
