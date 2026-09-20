# frontend

UI for IKK on two surfaces: the Android app, and a web build that renders the
same interface in a fixed, phone-shaped display.

This document is a **proposal**. Nothing here is wired into the Gradle build
yet except what is noted as existing.

---

## The part that is usually gotten wrong

> "Make sure the HTML also has the Android data."

Sharing UI code and sharing data are two unrelated problems, and solving the
first does not touch the second.

Android app data lives in the app's private sandbox at `/data/data/com.ikk/`.
A browser page lives in an origin-scoped storage jar on a different machine.
They share nothing, and no UI framework changes that. Even with 100% shared
Kotlin, an Android build and a web build are two processes on two devices.

There are exactly three ways data crosses:

| # | Mechanism | Data actually shared? | Needs a server? |
|---|---|---|---|
| 1 | **Compile-time** — one Kotlin source compiled to both targets | No. Only the *types and logic* are shared | No |
| 2 | **Network** — both clients talk to one backend | Yes, genuinely | Yes |
| 3 | **In-process bridge** — the web UI runs inside the app in a `WebView` | Yes, but only to that one embedded page | No |

Mechanism 1 is what Kotlin Multiplatform gives you. It is worth having — one
`@Serializable` model means the two clients cannot drift out of sync — but on
its own the web build starts empty.

**So: pick 1 + 2 for a real product, or 3 alone for a demo.** The combination
that does not work is 1 alone while expecting the browser to see the phone's
data.

---

## Three structures, and what each costs

### A — Compose Multiplatform (recommended)

One Kotlin UI compiled to Android and to Wasm. The web build is literally the
Android interface running in a canvas, which is exactly what "the display
should look like Android" asks for.

```text
frontend/
├── shared-ui/                    # Compose UI, one source of truth
│   └── src/
│       ├── commonMain/kotlin/com/ikk/ui/      # screens, components, theme
│       ├── androidMain/kotlin/com/ikk/ui/     # actual: platform bits
│       └── wasmJsMain/kotlin/com/ikk/ui/      # actual: platform bits
└── web/
    ├── index.html                # device frame + canvas host
    └── device-frame.css
```

`app/` keeps the `Activity` and calls into `shared-ui`. A new `:frontend:web`
Gradle module produces the Wasm bundle.

- **Good**: one UI codebase; pixel-identical on both surfaces; native Android performance; JetBrains-native
- **Bad**: web is **Beta**, not Stable; Wasm bundle is multi-megabyte; needs a WasmGC browser (Chrome 119+, Firefox 120+, Safari 18.2+); no HTML in the output, so no SEO and no text selection by default

### B — Separate UIs, shared data contract

Android stays Compose. Web is an ordinary TS/React app. Only the models are
shared, from the existing `core` module promoted to Kotlin Multiplatform.

```text
core/                             # promoted to KMP: commonMain holds @Serializable models
frontend/
└── web/
    ├── src/
    │   ├── components/
    │   ├── screens/
    │   └── generated/types.ts    # emitted from core's models
    └── package.json
```

- **Good**: each surface is idiomatic; real HTML; web is small and fast; no Beta dependency
- **Bad**: two UI codebases to keep visually in sync — and "looks like Android" becomes manual work

### C — WebView-hosted

One HTML UI. The Android app is a shell around a `WebView` that loads it.

```text
frontend/
└── web/                          # the only UI
app/src/main/assets/web/          # the same bundle, shipped in the APK
```

- **Good**: fastest to build; one UI; data bridging is trivial and local
- **Bad**: not really a native app; scroll and gesture feel is wrong; the JS bridge is a genuine attack surface

---

## Recommendation

**A**, with the data path deferred.

It is the only option where "the web display looks like Android" is free
rather than a maintenance burden, and for a JetBrains-hosted event, the
JetBrains stack is a defensible choice on its own. Accept that web is Beta.

Do not build a backend until there is data worth syncing. Until then the web
build runs on seeded local state, which is what `web/index.html` does today.

If the schedule tightens, fall back to **C** — a WebView shell is a day of
work, and the HTML written for it is not wasted if you later move to B.

---

## Data path, when it is time

Promote `core` to Kotlin Multiplatform and put the models in `commonMain`:

```kotlin
// core/src/commonMain/kotlin/com/ikk/core/Deck.kt
@Serializable
data class Deck(val id: String, val title: String, val slides: List<Slide>)
```

Both clients then serialize identically, and the wire format cannot drift.
Transport options, cheapest first:

1. **Seeded local state** — web build ships with sample data. No server. Good enough to demo
2. **Static JSON export** — the app writes `deck.json`, the web build reads it. One-way, no server
3. **Ktor backend** — one service, `core` shared with both clients. The real answer
4. **WebView bridge** — only if the page is embedded in the app

### If you do embed a WebView

`addJavascriptInterface` injects the object into **every frame, including
iframes**, so any injected script can call it. Since API 17 only
`@JavascriptInterface`-annotated methods are exposed, which closes the old
reflection path to `Runtime.exec` but does not make the bridge safe.

Prefer `WebViewCompat.addWebMessageListener` from Jetpack Webkit, which takes
an **allow-list of origins**, and gate it on
`WebViewFeature.isFeatureSupported()`. Load only first-party HTTPS or
`file:///android_asset/` content, and keep the exposed method count near zero.

---

## What exists right now

```text
frontend/
├── README.md       this file
└── web/
    └── index.html  device-frame shell — a fixed Android-sized display
```

`web/index.html` is standalone: open it directly, no build step. It renders a
phone frame at true Android `dp` dimensions with a status bar and gesture
bar, and hosts a placeholder screen in the content slot.

That content slot is the seam. Under option A it becomes the Wasm canvas;
under B, the React root; under C, an iframe of the shipped bundle. The frame
itself does not change.

## Sources

- [Compose Multiplatform 1.12.0](https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/)
- [Compose Multiplatform for Web goes Beta](https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/)
- [Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [WebView native bridges — security risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [Access native APIs with a JavaScript bridge](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
