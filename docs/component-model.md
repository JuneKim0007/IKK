# Component model

Shared base classes and behaviours for the web layer and the mobile layer.
Written before either layer is built, so both are implemented against the same
tree rather than reconciled afterwards.

---

## 1. The first cut: two trees, not one

The most expensive mistake available here is putting everything under one
hierarchy. There are two populations of component and they have opposite
requirements.

| | **DesignNode** | **EditorControl** |
|---|---|---|
| What it is | What the user draws | The tool's own UI |
| Examples | Rectangle, Text, Image | Tool button, Layers row, Generate |
| In `contract.json`? | **Yes** | **Never** |
| Synced to backend? | Yes | No |
| Rendered by | Both surfaces, identically | Each platform, idiomatically |
| Count | Unbounded, user-created | Fixed, developer-created |

`[Generate]` is the clarifying case. It is a button the user presses, but it is
not a button the user *drew*. It must never appear in the contract, never sync,
and never be rendered by the generated output. It is an `EditorControl`.

The rule: **if regenerating from the contract would recreate it, it is a
DesignNode. Otherwise it is an EditorControl.**

---

## 2. BaseUIComponent

Everything in the system inherits this. It exists to guarantee three things:
stable identity, a JSON shape, and a sync story.

```
BaseUIComponent  (abstract)
│
├── identity
│   ├── id          : String        stable across sessions, never reused
│   ├── type        : String        discriminator for deserialisation
│   ├── version     : Int           bumped on every mutation
│   └── updatedAt   : Instant       set on every mutation, UTC
│
├── serialisation
│   ├── toJson()    : JsonObject    the only writer of the wire format
│   ├── fromJson()  : T             static; must round-trip toJson exactly
│   └── schemaVersion : Int         so old payloads can be migrated
│
├── validation
│   └── validate()  : ValidationResult   Ok | Warn(msg) | Error(msg)
│
├── change tracking
│   ├── isDirty     : Boolean
│   ├── markDirty()                 bumps version, stamps updatedAt
│   └── clearDirty()                called only by the sync layer on ack
│
└── sync
    └── syncPolicy  : SyncPolicy    Immediate | Debounced(ms) | Interval(cron) | Manual
```

**Round-trip is the contract.** `fromJson(toJson(x)) == x` must hold for every
component. It is one property test per type and it is the cheapest defence
against the two surfaces drifting.

**`markDirty()` is the only mutation path.** Every setter routes through it.
If a field can be written without bumping `version`, sync will silently lose
that edit.

---

## 3. DesignNode tree

```
BaseUIComponent
│
└── DesignNode  (abstract)
    │
    ├── geometry
    │   ├── rect      : RelRect { x, y, w, h }   fractions of the frame, 0..1
    │   ├── z         : Int                       paint order
    │   ├── visible   : Boolean
    │   └── opacity   : Float  0..1
    │
    ├── behaviour
    │   ├── hitTest(point)     : Boolean
    │   ├── translate(dx, dy)
    │   ├── resize(dir, dx, dy)
    │   └── bounds()           : RelRect
    │
    │   (no emit methods — see the note below)
    │
    ├─── ShapeNode  (abstract)  :: Fillable, Strokable, TextCarrier
    │    ├── fill     : Color | None
    │    ├── stroke   : Stroke { color, width } | None
    │    ├── radius   : Radius
    │    │
    │    ├── RectNode        radius = Dp
    │    └── EllipseNode     radius = Percent(50)
    │
    ├─── TextNode  :: TextCarrier
    │    └── (a ShapeNode with fill=None, stroke=None — see §5)
    │
    ├─── ImageNode  :: Fillable
    │    ├── source       : AssetRef
    │    ├── contentScale : Crop | Fit | Fill
    │    └── alt          : String
    │
    └─── GroupNode  (later)  :: Container
         └── children : List<DesignNode>
```

### Capabilities, not subclasses

Text-in-shape is the reason these are interfaces. A rectangle that carries a
label is not a different class from a rectangle.

```
Fillable      fill: Color|None
Strokable     stroke: Stroke|None            stroke alignment: INSIDE (see §6)
TextCarrier   text: TextPayload?             null = no text, not empty string
Resizable     minSize, aspectLocked
Container     children, layout policy        GroupNode only
Bindable      binding: StatePath?            phase 2, behaviour layer
```

`TextPayload`:

```
TextPayload
├── value     : String        may contain \n
├── size      : Sp
├── align     : Start | Center | End
├── color     : Color
├── weight    : Normal | Medium | SemiBold
└── maxLines  : Int?          null = unbounded
```

**`text` is nullable, and null is meaningful.** `null` means "this shape has no
text" and the inspector shows *Add text*. `""` means "has a text slot, currently
empty" and the inspector shows the text section. Collapsing these two states is
the bug that makes the Add-text affordance flicker.

---

## 4. EditorControl tree

Never serialised into the contract. Shared *behaviour*, platform-specific
rendering.

```
BaseUIComponent
│
└── EditorControl  (abstract)
    ├── label     : String
    ├── icon      : IconRef?
    ├── enabled   : Boolean
    ├── visible   : Boolean
    └── shortcut  : KeyStroke?        desktop only; null on touch
    │
    ├─── ActionControl
    │    ├── run()        : Unit
    │    ├── undoable     : Boolean
    │    └── confirm      : Boolean      true for destructive
    │    │
    │    ├── DeleteAction      undoable, confirm
    │    ├── UndoAction
    │    └── GenerateAction    cuts a checkpoint, runs codegen
    │                          ← not a DesignNode. See §7
    │
    ├─── ToolControl
    │    ├── activeTool  : ToolId
    │    ├── cursor      : CursorRef      desktop only
    │    │
    │    ├── MoveTool
    │    ├── ShapeTool      group: [RectTool, EllipseTool], remembers last
    │    ├── TextTool
    │    └── ImageTool
    │
    ├─── PropertyControl<T>
    │    ├── read()   : T                 from the selected DesignNode
    │    ├── write(T) : Unit              routes through markDirty()
    │    │
    │    ├── NumberField        X, Y, W, H, radius, size
    │    ├── Stepper            touch-first variant of NumberField
    │    ├── NudgePad           touch only; no desktop equivalent needed
    │    ├── ColorSwatchGrid    fill, stroke, text colour
    │    └── SegmentedToggle    align
    │
    └─── SurfaceControl
         ├── InspectorPanel     web: right column · android: bottom sheet
         ├── LayersPanel        web: left column · android: full detent
         └── ToolSurface        web: toolbar     · android: bottom rail
```

`SurfaceControl` is where the two layers legitimately diverge. The *contents*
are the same `PropertyControl` set; only the container differs. Keep the
divergence at this one node of the tree and nowhere below it.

---

### Emission does not live here

An earlier draft put `toCompose()` / `toCss()` on `DesignNode`. It no longer
does. Emission is owned by the standalone `packages/codegen/` package, which reads a
contract and writes `.kt` and `.css`.

*Rendering* a contract at runtime — what both editors do — and *generating*
source from it are different jobs. The Android app never needs to emit Kotlin
source, so giving the model that responsibility bought nothing and created a
second emitter to keep byte-identical.

---

## 5. Per-component analysis

The column that matters is **Divergence risk** — where the two renderers can
disagree while both looking plausible.

### RectNode

| Property | Contract | Compose | CSS |
|---|---|---|---|
| rect | `{x,y,w,h}` % | `Modifier.rel(...)` | `left/top/width/height` % |
| fill | hex \| null | `.background(Color(…), shape)` | `background` |
| stroke | `{color,width}` \| null | `.border(w.dp, color, shape)` | `border` |
| radius | dp | `RoundedCornerShape(n.dp)` | `border-radius: npx` |
| opacity | 0..1 | `.alpha(n)` | `opacity` |
| text | `TextPayload?` | child `Text` in a `Box` | flex child |

**Divergence risk — stroke alignment.** CSS `border` with the default
`box-sizing: content-box` grows the element outward; Compose `.border()` draws
**inside** the bounds. A 4dp stroke puts the two renderers 8dp apart on width.
Fix: force `box-sizing: border-box` in the generated CSS and treat stroke as
inside on both. Write this down in the emitter, not in a code review.

### EllipseNode

Same as RectNode, except radius.

**Divergence risk — non-square ellipses.** CSS `border-radius: 50%` on a
200×100 box produces a true ellipse. Compose `CircleShape` produces a stadium
(pill), not an ellipse. Correct mapping is `RoundedCornerShape(percent = 50)`,
which *does* give the ellipse. `CircleShape` is the obvious-looking wrong
answer and it will be reached for.

### TextNode

| Property | Contract | Compose | CSS |
|---|---|---|---|
| value | String, `\n` allowed | `text = "…"` | text node |
| size | sp | `fontSize = n.sp` | `font-size: npx` |
| align | Start/Center/End | `textAlign` | `text-align` + `justify-content` |
| colour | hex | `color = Color(…)` | `color` |
| maxLines | Int? | `maxLines`, `overflow` | `-webkit-line-clamp` |

**Divergence risk — line box and wrapping.** Three separate traps:

1. `sp` scales with the user's font-size setting; `px` does not. A design that
   fits at 100% font scale clips at 130%. Either emit `dp` for text (and accept
   the accessibility cost, documented) or design with slack
2. CSS default `line-height: normal` ≈ 1.2 and is font-dependent; Compose
   defaults differ. **Always emit an explicit line-height on both**
3. `\n` needs `white-space: pre-wrap` in CSS. Without it the newline collapses
   to a space and only the web output is wrong

Also: pin the same font family on both surfaces, or every text box has a
different width. Roboto on Android, Roboto served as a webfont on the web —
not `system-ui`.

### ImageNode

| Property | Contract | Compose | CSS |
|---|---|---|---|
| source | AssetRef | `painterResource` / Coil | `src` |
| contentScale | Crop/Fit/Fill | `ContentScale.*` | `object-fit` |
| radius | dp | `.clip(shape)` | `border-radius` + `overflow:hidden` |
| alt | String | `contentDescription` | `alt` |

**Divergence risk — clipping.** `object-fit: cover` crops from the centre;
`ContentScale.Crop` also centres, so these agree. But `.clip()` must be applied
**before** the painter in the modifier chain or the corners don't round. Order
in a Compose modifier chain is semantic; order in a CSS declaration block is
not. This is the single most common Compose porting bug.

---

## 6. Synchronisation

### The envelope

Every synced component serialises into the same wrapper, whatever its type:

```json
{
  "id": "n7",
  "type": "rect",
  "schemaVersion": 1,
  "version": 12,
  "updatedAt": "2026-09-20T14:22:31Z",
  "checksum": "sha256:…",
  "payload": { }
}
```

`checksum` covers `payload` only. It lets the backend reject a torn write and
lets the client skip a no-op push.

### Policies

| Policy | When | Use for |
|---|---|---|
| `Immediate` | On mutation | Nothing, initially — it will hammer the backend on drag |
| `Debounced(400ms)` | Mutation, coalesced | **Default for DesignNode** |
| `Interval(cron)` | Background timer | Full-document reconcile, drift repair |
| `Manual` | User action | `[Generate]`, `[Generate]` |

A drag emits ~60 mutations a second. `Debounced` is not an optimisation here,
it is the difference between working and not.

The cron job is a **reconciler, not the primary path**. It exists to repair
drift after a dropped connection — it re-pushes anything still dirty and pulls
anything with a newer `updatedAt`. If the cron job is doing the real syncing,
the debounce is broken.

### Conflict resolution

Per-node last-write-wins on `updatedAt`, with `version` breaking ties.

Node-level granularity is the whole point: two people editing different
components never conflict, and that is the only realistic multi-user case for
a design tool. Document-level LWW would make them clobber each other.

```
SyncQueue
├── enqueue(component)        on markDirty
├── flush()                   debounced; batches into one request
├── onAck(ids)                clearDirty for each
└── onConflict(remote)        compare updatedAt, keep newer, re-render
```

Platform scheduler:

- **Web** — `setTimeout` debounce, plus flush on `visibilitychange` → hidden
- **Android** — coroutine debounce, plus `WorkManager` for the cron reconcile so
  it survives process death

---

## 7. `[Generate]`

Not a DesignNode. Not in the contract. Not rendered by generated output.

```
GenerateAction : ActionControl
├── enabled when  : contract is clean (no pending sync) and validates
├── run()         : POST /projects/{id}/generate   body = current contract
└── returns       : { checkpoint, artifacts: [ "Home.generated.kt",
                                               "home.generated.css",
                                               "home.generated.html" ] }
```

**Guard it on a clean sync queue.** If `[Generate]` fires while components are
still dirty, the backend generates from a stale contract and the user gets
output that does not match what is on their screen — which they will report as
the generator being broken, not as a sync bug.

Sequence:

```
client                         backend
  │  flush()  ────────────────▶  persist nodes
  │  ◀──────────────────────────  ack
  │  [Generate] ────────────────▶  load contract
  │                              cut checkpoint cp_00N
  │                              run static codegen
  │  ◀──────────────────────────  artifact list
```

Codegen stays on the backend, so both clients get byte-identical output and
neither has to ship an emitter. That is also why `toCompose()` / `toCss()` sit
on `DesignNode` as a shared definition rather than as client code — the backend
runs them.

---

## 8. Build order

1. `BaseUIComponent` + `RelRect` + round-trip property test
2. `DesignNode` with `RectNode` only, both renderers, verify pixel parity
3. `TextCarrier` on `RectNode` — this is where text-in-shape is proven
4. `EllipseNode`, `TextNode`, `ImageNode`
5. `EditorControl` tree — chrome, per platform
6. `SyncQueue` with `Debounced` only
7. `Interval` reconcile, then `[Generate]`

Steps 1–3 are the ones worth doing carefully. If a rectangle with a label
renders identically on both surfaces, everything after it is repetition. If it
does not, nothing after it matters.

**Do not build** `GroupNode` or `Bindable` yet. Both are real, both are later,
and both will distort the base classes if designed against imagined
requirements.
