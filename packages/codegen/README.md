# IKK codegen

Deterministic contract generator for the two MVP targets:

- Web: `generated/web/home.generated.css`
- Web structure: `generated/web/home.generated.html`
- Android: `generated/android/HomeLayout.generated.kt` (Jetpack Compose)

No model call is involved. The same validated contract is normalized once and
then passed to both emitters.

## Run

Node.js 18 or newer is the only requirement. There are no npm dependencies.

```sh
cd codegen
npm run generate
npm test
```

The default input is `examples/home.json`. Custom paths can be supplied from
the repository root:

```sh
node packages/codegen/generate.mjs \
  --input path/to/contract.json \
  --css path/to/home.generated.css \
  --html path/to/home.generated.html \
  --kotlin path/to/HomeLayout.generated.kt
```

Use `--check` in CI. It exits unsuccessfully when any generated file is
missing or stale:

```sh
node packages/codegen/generate.mjs --check
```

## Contract v1 assumptions

- `reference.unit` is `dp`.
- `layout` is `relative`.
- `rect.unit` is `%`; `x`, `y`, `w`, and `h` are percentages from `0` to `100`.
- `radius` and `stroke.width` are interpreted as CSS px / Android dp.
- `text.size` and `text.lineHeight` are interpreted as CSS px / Android sp.
- Colours are `#RRGGBB`.
- Supported nodes are `rect`, `text`, `ellipse`, and `image`.
- `z` is the paint order. When missing, JSON input order is used.
- `visible`, text metrics, and image `contentScale` have deterministic defaults.

Compose receives `maxLines` directly. CSS keeps text inside the component's
fixed design bounds with `overflow: hidden`; exact multi-line clamping would
require an additional inner text element in the authored HTML.

Web elements need both the common and generated classes, for example:

```html
<div class="ikk-node rect_7">Open inbox</div>
```

For images, the generated Compose function exposes an `imageContent` slot so
authored code can supply a painter without modifying the generated layout.

Generated files are replaced wholesale. Behaviour, navigation, callbacks, and
asset loading belong in authored files outside `generated/`.
