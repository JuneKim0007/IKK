# JSON contract — normative spec

The wire format between the editors, the backend, and the code generators.

**This document is the authority.** If an implementation disagrees with it, the
implementation is wrong. If the spec is wrong, change this file first and the
implementations second.

Audience: anyone building the web editor, the Android editor, the backend, or
a generator. You should be able to implement any one of those from this file
alone, without reading another party's source.

Related: [component-model.md](component-model.md) for the class tree,
[roadmap.md](roadmap.md) for build order.

---

## 1 · Principles

1. **Relative geometry.** Every position and size is a fraction of the
   reference viewport, never a pixel. One contract lays out at any screen size
2. **One writer per file.** The contract is produced only by an editor;
   generated artifacts are produced only by the generator. Neither edits the
   other's output
3. **Null is meaningful.** `null` and `""` are different states and must
   survive a round trip distinctly
4. **Round-trip exact.** `parse(serialise(x)) == x` for every object here. This
   is a test, not an aspiration
5. **Unknown fields are an error, not a warning.** Silently ignoring a field
   you do not understand is how two implementations drift apart

---

## 2 · Versioning

| Field | Meaning | On mismatch |
|---|---|---|
| `schemaVersion` | Shape of this document | **Reject.** Do not guess |
| `version` (per node) | Mutation counter, monotonic | Used for conflict resolution |
| `checkpoint` | Immutable snapshot label | Informational |

`schemaVersion` is currently **1**. Bump it for any breaking change: a removed
field, a changed type, a changed unit. Adding an optional field with a
documented default is **not** breaking.

---

## 3 · Top-level object

```json
{
  "schemaVersion": 1,
  "checkpoint": "cp_005",
  "screen": "Home",
  "reference": { "w": 375, "h": 667, "unit": "dp" },
  "layout": "relative",
  "components": { }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | int | ✔ | Must be 1 |
| `checkpoint` | string | ✔ | `cp_` + 3+ digits, zero-padded |
| `screen` | string | ✔ | PascalCase. Becomes the Compose function name |
| `reference` | object | ✔ | Viewport the design was authored against |
| `layout` | string | ✔ | Only `"relative"` in v1 |
| `components` | object | ✔ | Key → component. Key is stable, see §4 |

`reference` is **documentation of intent, not a constraint**. Renderers must
not assume 375×667; they lay out into whatever box they are given. It exists so
a human can tell what the designer was looking at, and so absolute values can
be recovered for debugging.

### `components` is a map, not an array

Keys are `{type}_{n}`, e.g. `rect_1`, `text_2`. The key is **stable for the
life of the node** and is what the generated CSS class and the Compose
identifier are derived from.

Paint order is **`z`, ascending** — not map iteration order. JSON object key
order is not guaranteed to survive a round trip through every parser, so
relying on it is a bug waiting for a different language.

---

## 4 · Component envelope

Every component, whatever its type, has this shape:

```json
{
  "id": "0b4c1e6a-6f9d-4e27-9a3e-1f2c5d7e8a90",
  "type": "rect",
  "name": "Sign in",
  "z": 3,
  "visible": true,
  "opacity": 1.0,
  "rect": { "x": 7.5, "y": 11.1, "w": 85.1, "h": 5.1, "unit": "%" },
  "fill": "#65558F",
  "stroke": null,
  "radius": 8,
  "text": null,
  "version": 12,
  "updatedAt": "2026-09-20T14:22:31Z"
}
```

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `id` | string | ✔ | — | UUID. Stable, never reused, never shown |
| `type` | enum | ✔ | — | `rect` · `ellipse` · `triangle` · `line` · `text` · `image` |
| `name` | string | ✔ | — | Designer-facing. Unique within scope. §4.1 |
| `z` | int | ✔ | — | Paint order, ascending. Ties broken by `id` |
| `visible` | bool | ✔ | `true` | Hidden nodes stay in the contract and are **not** emitted |
| `opacity` | float | ✔ | `1.0` | 0.0–1.0 inclusive |
| `rect` | RelRect | ✔ | — | §5 |
| `fill` | Color \| null | ✔ | — | `null` = no fill, not transparent black |
| `stroke` | Stroke \| null | ✔ | — | §7 |
| `radius` | number \| `"50%"` | ✔ | — | §8 |
| `text` | TextPayload \| null | ✔ | — | §9. `null` ≠ empty |
| `version` | int | ✔ | `1` | Bumped on every mutation |
| `updatedAt` | RFC 3339 UTC | ✔ | — | Stamped on every mutation |

`id` vs the map key: `id` is the identity used by sync and conflict
resolution. The map key is the identity used by code generation. They are
deliberately separate so a node can be renamed in generated output without
breaking sync history.

---

## 4.1 · Identity: `id`, `name`, `key`

Three identifiers, three jobs. Conflating any two is the scaling bug.

| | Who sets it | Mutable | Unique within | Used by |
|---|---|---|---|---|
| `id` | the editor, automatically | **never** | the whole document | sync, conflict resolution |
| `name` | the designer | yes | **its parent scope** | the layers panel, the generated key |
| `key` | derived from `name` | follows the name | the whole document | generated CSS class, Compose identifier |

### `id`

A UUID, generated on creation, **never** shown to the designer and **never**
reused. It is the only identifier sync trusts. Renaming a node does not change
it, which is what lets a rename survive a sync without looking like a delete
plus an insert.

### `name`

What the designer sees and types. Required, non-empty.

**Unique within its parent scope** — the screen today, a group once groups
exist. Two nodes in the same scope may not share a name; the same name in two
different groups is legal and is the intended escape hatch. Scoping the rule
rather than globalising it is what keeps it usable: a designer should be able
to call the button in each card "Action" without the tool arguing.

Defaults on creation are generated to be collision-free:

```
Rectangle 1, Rectangle 2, …      per type, next free ordinal in scope
```

The editor must **reject** a rename that would collide, keep the previous name,
and surface the standard warning rather than silently appending a suffix:

```
base_pop_up_warning("Can't create \"{name}\" — a component with that name
                     already exists in this group.")
```

Silently renaming to `Action 2` is worse than refusing: the designer believes
they named it `Action`, and the generated identifier disagrees with the layers
panel.

### `key`

The map key, and the identifier that reaches generated code. Derived:

```
key = "{type}_{slug(name)}"        →  rect_signIn, text_greeting
```

`slug` lower-camel-cases the name and strips anything outside `[A-Za-z0-9]`.
Because `name` is unique within its scope and v1 has exactly one scope, `key`
is unique within the document. **When groups land, the key must gain the scope
path** (`group_hero__rect_card`) or two groups' identically-named children will
collide in a flat CSS file.

`key` changing on rename is safe: generated files are rewritten wholesale, and
`id` carries the sync identity.

---

## 5 · RelRect

```json
{ "x": 7.5, "y": 11.1, "w": 85.1, "h": 5.1, "unit": "%" }
```

- `unit` is always `"%"` in v1. Present so a future absolute mode is not a
  breaking change
- Values are percentages, **not** fractions: `85.1` means 85.1%, not 8510%
- `x`/`y` are the **top-left corner**, relative to the frame's top-left
- Negative `x`/`y` and values over 100 are **legal** — a node may hang off the
  frame. Renderers clip; they do not clamp
- Precision: **one decimal place.** Serialise with exactly one. More precision
  is false confidence given the input came from a finger or a mouse

Generators convert to their own form:

| Target | From `x: 7.5` |
|---|---|
| CSS | `left: 7.5%` |
| Compose | `maxWidth * 0.075f` |

Note Compose takes a **fraction** (0.075), CSS takes a **percentage** (7.5%).
Dividing by 100 in exactly one of the two generators is the most likely
arithmetic bug in this project.

### Units. Normative.

**1 dp = 1 px at the reference viewport.** Every non-percentage length in this
document — `stroke.width`, a numeric `radius`, `text.size` — is expressed in
that unit and emitted as:

| Target | Unit | Behaviour away from the reference viewport |
|---|---|---|
| CSS | `px` | fixed; the frame scales, these do not |
| Compose | `.dp` | scales with display density |
| Compose text | `.sp` | also scales with the user's font-scale setting — divergence D1 |

A generator must not convert between dp and px. It writes the number through
unchanged and appends the target's unit. Any other rule makes a 14dp radius a
different size on the two surfaces at density ≠ 1.

Percentages in `rect` are unaffected — they are resolved against the frame at
layout time on both targets.

---

## 6 · Color

Uppercase hex, `#RRGGBB` or `#RRGGBBAA`.

```json
"fill": "#65558F"
"fill": "#65558F80"
"fill": null
```

- Always 7 or 9 characters including `#`
- **Uppercase.** `#65558f` and `#65558F` must not both appear or the checksum
  differs for identical designs
- `null` means *no fill* — the shape is not painted. It is **not** the same as
  `#00000000`, which paints a fully transparent black and still participates in
  hit-testing decisions downstream

Compose: `Color(0xFF65558F)` — note the `FF` alpha prefix for a 6-digit hex.
For an 8-digit hex the alpha moves to the front: `#RRGGBBAA` → `0xAARRGGBB`.
**The byte order differs between CSS and Compose.** Get this wrong and colours
look right until someone uses transparency.

---

## 7 · Stroke

```json
"stroke": { "color": "#B5AFBC", "width": 1 }
"stroke": null
```

`width` is in **dp at the reference viewport**, not a percentage. Strokes do
not scale with the frame — a 1dp hairline stays a hairline.

### Alignment is INSIDE. Normative.

The stroke is drawn **inside** the node's bounds. The node's `rect` is its
outer edge.

- Compose `.border()` already draws inside — no adjustment
- CSS `border` grows the box outward by default. Generated CSS **must** set
  `box-sizing: border-box` on `.c`

Without this, a 4dp stroke makes the web render 8dp wider than the Android
render, and both look correct in isolation.

---

## 8 · Radius

```json
"radius": 8
"radius": 0
"radius": "50%"
```

- A **number** is dp at the reference viewport
- The **string `"50%"`** means a full ellipse and is the only legal string

### `"50%"` mapping. Normative.

| Target | Correct | Wrong |
|---|---|---|
| CSS | `border-radius: 50%` | — |
| Compose | `RoundedCornerShape(percent = 50)` | `CircleShape` |

`CircleShape` on a non-square box produces a **pill**, not an ellipse. It is
the obvious-looking answer and it is wrong. A 200×100 ellipse is the golden
test that catches it.

---

## 9 · TextPayload

```json
"text": {
  "value": "Good morning",
  "size": 26,
  "align": "start",
  "color": "#FFFFFF",
  "weight": 600,
  "maxLines": null
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `value` | string | ✔ | May contain `\n`. Never `null` — omit the whole payload instead |
| `size` | number | ✔ | dp at the reference viewport |
| `align` | enum | ✔ | `start` · `center` · `end` |
| `color` | Color | ✔ | Not nullable. Text with no colour is invisible, which is a design error, not a state |
| `weight` | int | ✔ | 100–900 in steps of 100. CSS and Compose both take this natively |
| `lineHeight` | number \| null | ✔ | dp. `null` means `size × 1.3` |
| `fontFamily` | string | ✔ | Must resolve on both surfaces. v1: `"Roboto"` |
| `maxLines` | int \| null | ✔ | `null` = unbounded |

### `text: null` vs `text: { "value": "" }`

| State | Means | Editor shows |
|---|---|---|
| `"text": null` | No text slot | *"Add text to this shape"* |
| `"text": { "value": "" }` | Has a slot, currently empty | The text section, empty field |

Any component may carry text — that is how a labelled shape works. A
`type: "text"` node is just a node whose `fill` and `stroke` are `null`; it is
not a special case in the renderer.

### Rendering rules. Normative.

1. `\n` is a hard line break. CSS **must** emit `white-space: pre-wrap`.
   Without it the newline collapses to a space and only the web output is wrong
2. Line height **must** be emitted explicitly on both targets. CSS
   `line-height: normal` is font-dependent and differs from Compose's default.
   A `null` `lineHeight` resolves to `size × 1.3`; the generator writes the
   resolved number, never the word `normal`
3. Vertical alignment is **centred** in the node's box on both targets
4. Horizontal padding is **6dp** on both targets
5. Font family is **Roboto** on both. The web must self-host it, not fall back
   to `system-ui`, or every text box is a different width

`size` is emitted as `sp` on Android and `px` on web. These diverge when the
user raises their system font size: Android reflows, web does not. This is a
**known, accepted divergence** — record it in the divergence register rather
than discovering it in a demo.

---

## 10 · Per-type field applicability

| Field | rect | ellipse | triangle | line | text | image |
|---|---|---|---|---|---|---|
| `rect` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `fill` | ✔ | ✔ | ✔ | null | null | placeholder colour |
| `stroke` | ✔ | ✔ | see below | **required** | null | ✔ |
| `radius` | number | `"50%"` | `0` | `0` | `0` | number |
| `text` | optional | optional | optional | null | **required** | null |
| `line` | — | — | — | `orientation` | — | — |
| `source` | — | — | — | — | — | nullable — `null` is an unfilled frame |
| `contentScale` | — | — | — | — | — | **required** |

### Triangle

An isosceles triangle filling the node's box: apex at the top centre, base on
the bottom edge. It carries no radius.

| Target | Mapping |
|---|---|
| CSS | `clip-path: polygon(50% 0%, 100% 100%, 0% 100%)` |
| Compose | a `GenericShape` with the same three points |

**Stroke on a triangle is not the same primitive.** CSS `clip-path` clips the
border away, so a bordered triangle renders with no visible outline, while
Compose's `.border(shape)` follows the path. Rather than let the two surfaces
disagree, v1 declares `stroke` **must be `null`** on a triangle (rule V16).
Outlined triangles need a drawn path on both sides and are deferred.

### Line

A straight stroke across the node's box, corner to corner. It has no fill, no
text, and no radius — it is drawn, not painted. `stroke` is **required**;
there is nothing to render without one (rule V17).

```json
"line": { "orientation": "topLeftToBottomRight" }
```

`orientation` is `topLeftToBottomRight` · `bottomLeftToTopRight` — which
diagonal of the box the line runs along. Arbitrary angles are deferred; see
`docs/roadmap-frontend.md` F1.23.

| Target | Mapping |
|---|---|
| CSS | `background: linear-gradient(...)` — a hard-edged diagonal band the width of `stroke.width`, not an SVG element. Keeps the line on the same box model as every other node, so selection, nudge and resize need no special case |
| Compose | `Canvas { drawLine(...) }` along the same diagonal |

A `type: "image"` node adds:

```json
"source": { "ref": "asset_12", "mime": "image/png" },   // or null
"contentScale": "crop"
```

`contentScale` is `crop` · `fit` · `fill`.

**Compose modifier order for images is semantic:** `.clip(shape)` must come
**before** the painter, or the corners do not round. CSS has no equivalent
ordering constraint. This is the most common porting bug in the project.

---

## 11 · Sync envelope

Nodes are pushed wrapped:

```json
{
  "id": "n7",
  "type": "rect",
  "schemaVersion": 1,
  "version": 12,
  "updatedAt": "2026-09-20T14:22:31Z",
  "checksum": "sha256:3f8a…",
  "payload": { }
}
```

### What `payload` contains. Normative.

`payload` is **the complete §4 component object, unchanged** — including `id`,
`type`, `version` and `updatedAt`. The envelope's copies of those four fields
are a routing convenience so a server can dispatch without parsing the body;
they are duplicates, not a different shape.

On any disagreement between an envelope field and its copy inside `payload`,
**`payload` wins** and the server responds `422`. There is exactly one
serialisation of a node, and `payload` is it.

- `checksum` covers the **canonical serialisation of `payload` only** —
  §13. It is **computed by the server and returned**; clients never compute it.
  It lets the server reject a torn write and lets a client skip a no-op push
- Conflict resolution is **per-node last-write-wins on `updatedAt`**, with
  higher `version` breaking a tie. Node granularity is the point: two people on
  different components never conflict
- A push whose `version` is not greater than the stored version is rejected
  with **409**, and the client must re-render from the server's copy

---

## 12 · Validation

A contract is valid when all hold. Reject, do not repair.

| # | Rule |
|---|---|
| V1 | `schemaVersion == 1` |
| V2 | Every map key matches `^(rect\|ellipse\|triangle\|line\|text\|image)_[A-Za-z0-9]+$` |
| V3 | Every `id` is unique within the document |
| V4 | `opacity` ∈ [0.0, 1.0] |
| V5 | `rect.w > 0` and `rect.h > 0` |
| V6 | `radius` is a number ≥ 0, or exactly `"50%"` |
| V7 | `type == "text"` ⟹ `text != null` |
| V8 | `type == "image"` ⟹ `contentScale` is set. `source` may be `null` — an image frame can exist before a file is chosen, as in any design tool |
| V9 | Every colour matches `^#[0-9A-F]{6}([0-9A-F]{2})?$` |
| V10 | `ellipse` ⟹ `radius == "50%"` |
| V11 | No unknown fields anywhere |
| V12 | `z` values are unique |
| V13 | `name` is non-empty after trimming |
| V14 | `name` is unique within its parent scope — the screen in v1 |
| V15 | `key` equals `{type}_{slug(name)}` for its node |
| V16 | `type == "triangle"` ⟹ `stroke` is `null` and `radius` is `0` |
| V17 | `type == "line"` ⟹ `fill` is `null` and `stroke` is non-null with `stroke.width > 0` |

V11 is the one people want to relax. Do not. A field one implementation writes
and another silently drops is a divergence that only shows up at a demo.

---

## 13 · Canonical serialisation

For checksums to agree across languages, serialisation must be deterministic:

1. Object keys sorted **lexicographically, ascending**
2. No insignificant whitespace
3. Numbers: integers without a decimal point; floats with exactly one decimal
   place for `rect` values, minimal representation elsewhere
4. Strings: UTF-8, `\n` escaped as `\n`, no `\/` escaping
5. `null` written explicitly, never omitted

Both a Kotlin and a TypeScript implementation must produce byte-identical
output for the same object. **Test this across languages**, not within one.

---

## 14 · Worked example

A complete, valid two-node contract:

```json
{
  "schemaVersion": 1,
  "checkpoint": "cp_005",
  "screen": "Home",
  "reference": { "w": 375, "h": 667, "unit": "dp" },
  "layout": "relative",
  "components": {
    "rect_1": {
      "id": "n1",
      "type": "rect",
      "z": 0,
      "visible": true,
      "opacity": 1.0,
      "rect": { "x": 0.0, "y": 0.0, "w": 100.0, "h": 27.0, "unit": "%" },
      "fill": "#65558F",
      "stroke": null,
      "radius": 0,
      "text": null,
      "version": 3,
      "updatedAt": "2026-09-20T14:22:31Z"
    },
    "text_2": {
      "id": "n2",
      "type": "text",
      "z": 1,
      "visible": true,
      "opacity": 1.0,
      "rect": { "x": 7.5, "y": 11.1, "w": 69.3, "h": 5.1, "unit": "%" },
      "fill": null,
      "stroke": null,
      "radius": 0,
      "text": {
        "value": "Good morning",
        "size": 26,
        "align": "start",
        "color": "#FFFFFF",
        "weight": 600,
        "maxLines": null
      },
      "version": 7,
      "updatedAt": "2026-09-20T14:25:02Z"
    }
  }
}
```

### Generated CSS

```css
/* GENERATED FROM contract cp_005 — DO NOT EDIT */
.screen { position: relative; width: 100%; aspect-ratio: 375 / 667; overflow: hidden; }
.c { position: absolute; box-sizing: border-box; margin: 0;
     display: flex; align-items: center; overflow: hidden; }

.rect_1 {
  left: 0%; top: 0%; width: 100%; height: 27%;
  background: #65558F;
  border-radius: 0px;
}

.text_2 {
  left: 7.5%; top: 11.1%; width: 69.3%; height: 5.1%;
  color: #FFFFFF;
  font-family: Roboto, sans-serif;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
  text-align: left;
  justify-content: flex-start;
  white-space: pre-wrap;
  padding: 0 6px;
}
```

### Generated Kotlin

```kotlin
// GENERATED FROM contract cp_005 — DO NOT EDIT
// Rewritten wholesale on every [Generate]. Behaviour goes in Home.kt

package com.ikk.ui.generated

@Composable
fun HomeLayout(modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxSize()) {
  fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this
    .offset(maxWidth * x, maxHeight * y)
    .size(maxWidth * w, maxHeight * h)

  Box(
    Modifier.rel(0.000f, 0.000f, 1.000f, 0.270f)
      .background(Color(0xFF65558F), RoundedCornerShape(0.dp))
  )
  Box(
    Modifier.rel(0.075f, 0.111f, 0.693f, 0.051f),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = "Good morning",
      color = Color(0xFFFFFFFF),
      fontSize = 26.sp,
      lineHeight = 33.8.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Start,
      modifier = Modifier.padding(horizontal = 6.dp),
    )
  }
}
```

Note `rect_1` has `fill` and no `text`, so it emits a childless `Box`.
`text_2` has `text` and `fill: null`, so it emits a `Box` with no background
and a `Text` child. **Both go through the same code path** — there is no
special case for "a text node".

---

## 15 · Divergence register

Accepted differences between the two targets. Anything not on this list is a
bug.

| # | Divergence | Why accepted |
|---|---|---|
| D1 | `sp` on Android reflows with system font size; `px` on web does not | Accessibility on Android outweighs pixel parity |
| D2 | Font rasterisation differs at identical metrics | Unavoidable; golden tolerance absorbs it |

---

## 16 · Changelog

| Version | Change |
|---|---|
| 1 | Initial. rect, ellipse, text, image; relative geometry; per-node sync |
| 1 | `triangle` (V16) and `line` (V17) added to the node-type enum. Still `schemaVersion 1` — this row exists because those two types shipped in Kotlin, JS and `docs/fixtures/` before this document was updated to match; the code was never wrong, this file was |
