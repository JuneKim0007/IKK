# IKK

A design tool whose output is a **contract**, not a picture.

A designer draws a screen. A developer gets `.css` and `.kt` generated from the
same JSON the designer was editing. Neither hands the other a file — they hold
two views of one object.

Hackathon scope: one laptop, one process, no database, no cloud. Everything
here runs from `./gradlew` and a browser tab.

## Where this stands

| Area | State |
|---|---|
| JSON contract, Kotlin + Python models | Implemented, 3 suites green |
| Deterministic codegen (`packages/codegen`) | Implemented — emits `.kt` and `.css` |
| Backend (`apps/backend`) | Runs; validates, stores, generates. No database |
| Android application | Hello World scaffold |
| Web application | Static device-frame harness |
| Android and Web editor UX | Interactive prototypes only |
| Sync, checkpoints, assets | Not started |

Authority order is in [docs/README.md](docs/README.md): the JSON contract wins
over the component model, the roadmap, and the prototypes.

---

## Why a contract and not an export

Design handoff loses information because the two sides hold different objects.
The designer holds a canvas; the developer holds code; a PNG or a spec document
sits between them and goes stale the moment either side moves.

IKK removes the document in the middle. There is one object — a JSON contract
of positioned, styled nodes — and everything else is a projection of it:

| Projection | Produced by | Editable by hand |
|---|---|---|
| Editor canvas | the editor, live from the contract | — (you edit the contract through it) |
| `home.generated.css` / `.html` | static emitter | **no** |
| `HomeLayout.generated.kt` | static emitter | **no** |
| `Home.kt`, `app.js` — behaviour | a human or an agent | yes |

The contract is normative and specified in
[`docs/json_contract.md`](docs/json_contract.md). If an implementation
disagrees with that file, the implementation is wrong.

---

## Pipeline

```mermaid
flowchart TB
    subgraph edit["Authoring surfaces"]
        WEB["Web editor<br/>HTML · CSS · JS"]
        AND["Android app<br/>Compose"]
    end

    subgraph srv["Local backend — one process, no DB"]
        CONTRACT[("contract.json<br/>per-node, versioned")]
        GEN["Static emitters<br/>JSON to CSS / Kotlin"]
    end

    subgraph out["Generated artifacts — read-only"]
        CSS["home.generated.css<br/>home.generated.html"]
        KT["HomeLayout.generated.kt"]
    end

    AGENT["AI agent<br/>registered tools"]
    HAND["Hand-written code<br/>Home.kt · app.js"]

    WEB -- "dirty nodes · debounce + 5s tick" --> CONTRACT
    CONTRACT -- "reconcile" --> WEB
    AND -- "dirty nodes" --> CONTRACT
    CONTRACT -- "reconcile" --> AND

    WEB == "POST /v1/projects/{id}/generate" ==> GEN
    CONTRACT --> GEN
    GEN --> CSS
    GEN --> KT
    CSS --> AGENT
    KT --> AGENT
    AGENT --> HAND
    HAND -. "references global names" .-> CSS
```

---

## Two clocks

The load-bearing decision in this design is that **syncing and generating are
driven by different triggers**.

| | Contract clock | Artifact clock |
|---|---|---|
| Trigger | an edit | the `[Generate]` button |
| Cadence | debounce ~400 ms, reconcile every 5 s | only when a human asks |
| Granularity | one node | the whole screen, plus a checkpoint |
| Writes | rows in `contract.json` | `.css` / `.html` / `.kt` on disk |
| If it goes wrong | stale canvas, fixed by the next tick | wrong code committed |

If the 5-second tick also generated code, every keystroke would cut a
checkpoint and rewrite files underneath whoever was editing them. Generation
has to be an *act*, not a side effect.

```mermaid
sequenceDiagram
    autonumber
    actor D as Designer
    participant E as Editor
    participant B as Backend
    participant F as Files on disk

    Note over D,B: contract clock — continuous
    D->>E: drag a rectangle
    E->>E: mark node dirty, bump version
    E-->>B: PUT /nodes/rect_1 (400ms after last edit)
    B-->>E: 200
    loop every 5s
        E->>B: GET /contract?since=version
        B-->>E: changed nodes only
    end

    Note over D,F: artifact clock — discrete
    D->>E: click [Generate]
    E->>E: refuse if the queue is still dirty
    E->>B: POST /v1/projects/{id}/generate
    B->>B: cut checkpoint cp_006
    B->>F: write .css / .html / .kt
    B-->>E: {checkpoint, artifacts[]}
```

**Why `[Generate]` is blocked on a clean queue:** if it fires while nodes are
still in flight, the backend generates from a contract that is behind the
screen. The user sees output that does not match their canvas and reports it as
the generator being broken, when it is a sync bug. One boolean prevents an hour
of debugging the wrong thing.

### Why not cron

`cron` cannot express 5 seconds — its floor is one minute. What the sync needs
is two client-side policies, not a scheduler:

- **Debounce (~400 ms after the last edit)** — this is what makes it feel live.
- **Interval reconcile (5 s)** — a safety net that catches dropped pushes and
  pulls the other surface's changes.

A bare 5-second poll with no debounce is the worst of both: up to 5 s of
latency on your own edit, and a request every 5 s from an idle tab.

---

## The generate route

```http
POST /v1/projects/{id}/generate
Content-Type: application/json

{ "screen": "Home" }
```

```json
{
  "checkpoint": "cp_006",
  "artifacts": [
    "frontend/web/generated/home.generated.css",
    "frontend/web/generated/home.generated.html",
    "app/src/main/kotlin/com/ikk/ui/generated/HomeLayout.generated.kt"
  ]
}
```

`POST`, not `GET` — it cuts a checkpoint and writes files. That is a mutation
with side effects, and a route that a browser or a link prefetcher can trigger
by accident is a route that will rewrite someone's working tree.

Codegen runs **on the backend, not in either client**. Both surfaces then get
byte-identical output, and neither has to ship an emitter.

---

## What the emitter produces

One contract node, two targets. Full worked example in
[`docs/json_contract.md` §14](docs/json_contract.md).

```json
"text_2": {
  "id": "n2", "type": "text", "z": 1,
  "rect": { "x": 7.5, "y": 11.1, "w": 69.3, "h": 5.1, "unit": "%" },
  "fill": null,
  "text": { "value": "Good morning", "size": 26, "color": "#FFFFFF",
            "align": "start", "weight": "semibold" },
  "version": 7
}
```

```css
/* GENERATED FROM contract cp_006 — DO NOT EDIT */
.text_2 {
  left: 7.5%; top: 11.1%; width: 69.3%; height: 5.1%;
  color: #FFFFFF; font-size: 26px; font-weight: 600;
  line-height: 1.3; text-align: left;
}
```

```kotlin
// GENERATED FROM contract cp_006 — DO NOT EDIT
Box(Modifier.rel(0.075f, 0.111f, 0.693f, 0.051f),
    contentAlignment = Alignment.CenterStart) {
  Text("Good morning", color = Color(0xFFFFFFFF),
       fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
}
```

Geometry is **relative** — fractions of the reference viewport, never pixels.
One contract lays out at any screen size, which is the only reason the same
numbers can drive a CSS percentage and a Compose `BoxWithConstraints` without a
per-device table.

---

## Global names are the join key

The map key in the contract — `rect_1`, `text_2` — becomes the CSS class *and*
the Compose identifier. That name is the entire connection between the
designer's rectangle and the developer's code.

```
contract key   rect_1
     ├── CSS        .rect_1
     └── Kotlin     HomeLayout, node index 0
```

Two consequences, both deliberate:

1. **The key is stable for the life of the node.** A node has both an `id`
   (used by sync and conflict resolution) and a map key (used by codegen). They
   are separate so a node can be renamed in generated output without breaking
   its sync history.
2. **Generated files are never hand-edited.** They are rewritten wholesale on
   every `[Generate]`. Behaviour lives in a sibling file that imports the
   generated names, so regeneration can never destroy authored code.

---

## Where the AI agent fits

The agent does not look at a screenshot and guess at a layout. It receives
**typed input**: a contract it can parse and generated files with known names.
Its job is wiring behaviour onto fixed geometry — a far smaller and far more
reliable problem than "build the UI".

| Tool | Returns |
|---|---|
| `read_contract(screen)` | the contract JSON — node ids, types, geometry, text |
| `list_artifacts(checkpoint)` | generated file paths and the global names in them |
| `write_impl(path, source)` | writes a hand-written sibling; refuses any `*.generated.*` path |

The refusal in `write_impl` is the whole safety model. The generated/authored
boundary is enforced by the tool, not by asking the agent nicely.

---

## Scope

Targets are fixed. This is not a plugin system.

| | In | Out |
|---|---|---|
| Web output | HTML + CSS (+ JS for behaviour) | React, Tailwind, SCSS |
| Android output | Kotlin + Compose | XML layouts, Views |
| Node types | rect, ellipse, text, image | groups, components, variants |
| Layout | relative geometry only | flex, constraints, auto-layout |
| Sync | per-node last-write-wins | operational transform, CRDTs, presence |
| Data | one screen, one `contract.json` | multi-project, auth, a database |

Cut order if time runs out, from [`docs/roadmap.md`](docs/roadmap.md) — each
line is still a demonstrable product:

1. Sync → manual export/import of the contract file
2. Android *editing* → Android as a read-only renderer (still proves one
   contract, two surfaces)
3. Backend → codegen in the web client, contract in `localStorage`
4. `image`, then `ellipse`

**Never cut:** the round-trip test on the contract, the pixel-parity goldens
between surfaces, and the generated/authored file boundary.

---

## Repo layout

```text
docs/           json_contract.md   normative spec — the authority
                component-model.md class tree behind the contract
                roadmap.md         build order, cut lines, risks
prototype/      standalone HTML prototypes of the editor and pipeline
                (not in the Gradle build)
frontend/       web surface and the shared-UI proposal
app/ data/ core Android app, Kotlin modules (see below)
```

---
## The Gradle scaffold

What exists in the build today, independent of the pipeline above.

### Requirements

- JDK 17; Gradle toolchains can provision it automatically
- Android SDK with API 37 installed
- Gradle 9.6.1 through the checked-in wrapper

No JDK is pinned. Each module declares `jvmToolchain(17)` and the foojay
resolver in `settings.gradle.kts` fetches a matching JDK, so the build works on
a fresh clone. The SDK path is read from `local.properties` (`sdk.dir`), which
is git-ignored and must exist locally.

### Modules

Dependencies point downward only. Nothing below reaches up.

```text
app  ──>  data  ──>  core
```

| Module | Plugin | Purpose |
|---|---|---|
| `app` | `com.android.application` | Activity, Compose UI, dependency wiring |
| `data` | `com.android.library` | Repositories and data sources |
| `core` | `org.jetbrains.kotlin.jvm` | Pure logic. No Android dependency |

`core` applies no Android plugin, so `android.*` is not on its compile
classpath — a layering violation is a build failure, not a review comment. Its
tests run on the desktop JVM in well under a second.

`data` exposes `core` with `api(project(":core"))`, so `app` sees both through
one dependency.

Feature modules get added alongside `app` when there are features. None exist
yet, so none are declared.

### Components

#### Build

| File | Role |
|---|---|
| `settings.gradle.kts` | Declares the three modules and the repositories |
| `build.gradle.kts` | Declares plugins for subprojects, applies none itself |
| `gradle/libs.versions.toml` | Single source of versions for plugins and libraries |
| `gradle.properties` | JDK pin, JVM args, AndroidX flag |
| `app/build.gradle.kts` | `compileSdk 37`, `minSdk 26`, `targetSdk 36`, Compose on |
| `data/build.gradle.kts` | Android library, `compileSdk 37`, `minSdk 26` |
| `core/build.gradle.kts` | Kotlin/JVM targeting Kotlin 17 bytecode |

Kotlin sources live in `src/<set>/kotlin`, not the Android default
`src/<set>/java`. Each Android module's `sourceSets` block sets that.

#### `core`

| File | Role |
|---|---|
| `com/ikk/core/Greeting.kt` | `greeting(name): String` — pure, the one piece of real logic |
| `com/ikk/core/GreetingTest.kt` | Unit test for it |

#### `data`

| File | Role |
|---|---|
| `com/ikk/data/GreetingRepository.kt` | `GreetingRepository` interface and `DefaultGreetingRepository`, which delegates to `core` |
| `com/ikk/data/GreetingRepositoryTest.kt` | Unit test for the delegation |

The repository is a seam, not yet an abstraction that earns its keep. If no
real data source ever lands here, fold it into `app`.

#### `app`

| File | Role |
|---|---|
| `com/ikk/MainActivity.kt` | `ComponentActivity`, constructs the repository, sets the Compose content |
| `com/ikk/ui/GreetingScreen.kt` | Stateless composable taking the message as a parameter, plus its `@Preview` |
| `AndroidManifest.xml` | Declares `MainActivity` as the launcher activity |
| `res/values/strings.xml` | `app_name` |
| `res/values/themes.xml` | `Theme.IKK`, a platform theme — no appcompat dependency needed |

`MainActivity` instantiates `DefaultGreetingRepository` directly. That is a
`TODO`: swap for dependency injection once there is more than one dependency.

#### Other

| Path | Role |
|---|---|
| `docs/` | Project documentation. Empty of substance so far |
| `prototype/` | Standalone HTML UI prototypes. Not part of the Gradle build |

### Build

```sh
./gradlew test
./gradlew assembleDebug
```

### Test

```sh
./gradlew :packages:design-contract:test
./gradlew :apps:android:data:testDebugUnitTest
./gradlew :apps:android:app:assembleDebug
```

`app` currently has no unit tests — its logic lives in the modules below it.

Note that `connectedDebugAndroidTest` uninstalls the app when it finishes, so
run `installDebug` again before launching by hand.

### Run

Open the project root in IntelliJ IDEA / Android Studio and let it import the
Gradle build, then run the `app` configuration. From the command line:

```sh
./gradlew :apps:android:app:installDebug
adb shell am start -n com.ikk/.MainActivity
```

### Known issues

- `compileSdk`, `minSdk` and the Kotlin 17 settings are duplicated across `app`
  and `data`. Move to a convention plugin under `build-logic/` if a third
  Android module appears.
