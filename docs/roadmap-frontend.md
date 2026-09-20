# Roadmap — frontend

Owner: the web and Android surfaces. Backend plan lives in
[roadmap-backend.md](roadmap-backend.md).

**Completed items are deleted from this file, not ticked.** A roadmap is a list
of work left to do; a list of work already done is what `git log` is for.
**Superseded code is removed, not parked in a `legacy/` folder.** Keeping two
answers to the same question is how a repo stops being trustworthy.

---

## Definition of done

1. A designer draws a screen on either surface, and the other shows the same
   screen after a sync
2. `[Generate]` produces `.kt` and `.css` that render **visually identical to
   the editor canvas at the reference viewport, at default font scale**, within
   the Phase G tolerance
3. Every `DesignNode` type round-trips through JSON without loss

---

## F1 · Web editor — `apps/web`

Vanilla ES modules, no bundler. Every element extends `BaseElement`, and
`markDirty()` is the only mutation path — if a field can change without it,
sync silently loses the edit.

**Renderer**

- [ ] **F1.1** Canvas surface at the reference viewport, zoom, pan
- [ ] **F1.2** `RectElement` — fill, stroke inside, radius, opacity
- [ ] **F1.3** `EllipseElement` — `border-radius: 50%`
- [ ] **F1.4** `TextElement` — explicit line-height, `pre-wrap`
- [ ] **F1.5** `ImageElement` — `object-fit`, clipped corners, null source is a frame
- [ ] **F1.6** Text-on-shape for any element whose node carries `text`
- [ ] **F1.7** Selection outline + 8 resize handles

**Interaction**

- [ ] **F1.8** Click to select, click-empty to deselect
- [ ] **F1.9** Drag to move
- [ ] **F1.10** Drag handles to resize, all 8 directions
- [ ] **F1.11** Draw-by-drag creation per tool
- [ ] **F1.12** Double-click to edit text in place
- [ ] **F1.13** Arrow-key nudge, shift = 10
- [ ] **F1.14** Delete and undo — command stack, not JSON snapshots

**Chrome**

- [ ] **F1.15** Toolbar with the grouped shape tool (rect + ellipse, remembers last)
- [ ] **F1.16** Inspector: position, size, fill, stroke, radius, opacity
- [ ] **F1.17** Inspector: text section only when `text != null`; "Add text" when null
- [ ] **F1.18** Layers panel: select, rename, visibility, reorder
- [ ] **F1.19** Name collision rejected with the standard warning, per
      `json_contract.md` §4.1 — never a silent suffix
- [ ] **F1.20** Status bar: frame size, zoom, selection

---

## F2 · Android editor

Touch-first. Layout decisions are settled; see `docs/component-model.md` §4.

- [ ] **F2.1** Canvas composable, pinch-zoom, two-finger pan
- [ ] **F2.2** Element renderers matching the web output exactly
- [ ] **F2.3** `RoundedCornerShape(percent = 50)` for ellipses, **not** `CircleShape`
- [ ] **F2.4** `.clip()` before the painter for images — modifier order is semantic
- [ ] **F2.5** Selection handles: 48dp targets, 14dp visuals
- [ ] **F2.6** Tap select, drag move, drag resize
- [ ] **F2.7** Double-tap text editing, canvas clears the keyboard
- [ ] **F2.8** Nudge pad — 1dp per tap, auto-repeat on hold
- [ ] **F2.9** Bottom tool rail with the shape popup
- [ ] **F2.10** Contextual app bar, bottom sheet at peek and half detents
- [ ] **F2.11** Layers at full detent
- [ ] **F2.12** `SupportingPaneScaffold` for the Expanded window size class

---

## F3 · Client sync

- [ ] **F3.1** `SyncQueue` — enqueue, flush, onAck, onConflict
- [ ] **F3.2** `Debounced(400ms)` default. A drag emits ~60 mutations a second
- [ ] **F3.3** Batching — one request per flush, not one per node
- [ ] **F3.4** Offline queue survives reload (web) and process death (Android)
- [ ] **F3.5** Web: flush on `visibilitychange → hidden`
- [ ] **F3.6** Android: `WorkManager` reconcile. The cron repairs drift; if it is
      doing the real syncing, the debounce is broken
- [ ] **F3.7** Sync status in the UI — clean, pending, failed
- [ ] **F3.8** Two-client test: edit different nodes, both converge

---

## G · Parity gate

A gate, not a feature. Nothing ships past it.

- [ ] **G.1** Golden harness: render a contract on both surfaces, diff
- [ ] **G.2** Golden: rect with stroke — catches `box-sizing`
- [ ] **G.3** Golden: non-square ellipse — catches `CircleShape`
- [ ] **G.4** Golden: multi-line text — catches `pre-wrap` and line-height
- [ ] **G.5** Golden: image with radius — catches modifier order
- [ ] **G.6** Golden: text-on-shape, all three alignments
- [ ] **G.7** Tolerance policy written down
- [ ] **G.8** Goldens in CI, failing the build on drift
- [ ] **G.9** Golden: drag a node while the canvas is zoomed and panned,
      commit, assert the written `%` equals an un-zoomed drag to the same
      visual spot — catches "forgot to divide out the transform" before
      capture-to-percent normalization, per
      `docs/architecture/frontend-web.md` §2 /
      `docs/architecture/frontend-android.md` §2
