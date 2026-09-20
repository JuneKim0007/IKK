# Applications

`apps/` contains deployable product surfaces. Code here may depend on shared
packages; shared packages must never depend on an app.

| Path | Purpose |
|---|---|
| `android/` | Native Android application, Android data layer, and future editor feature |
| `web/` | Browser-based editor and renderer |

Android and Web have separate UI implementations. They converge through the
contract in `packages/design-contract` and `docs/json_contract.md`, not through
shared UI code.
