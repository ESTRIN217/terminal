# AGENTS.md — Termux App

## Build & Test

- **Java 17 required.** Build will fail with older versions.
- **NDK `30.0.14904198`** — native C code in `terminal-emulator`, `termux-shared`, and `app` modules. CMake `3.31.6`.
- Build: `./gradlew assembleDebug`
- Tests: `./gradlew test` (unit tests only; no instrumented tests in CI)
- **No lint, detekt, ktlint, or formatter is configured.** The only CI quality gate is `./gradlew test`.
- Gradle 9.7.0, AGP 9.3.1. Daemon enabled, parallel builds capped at 2 workers.

## Module Architecture

```
app  →  termux-shared  →  terminal-view  →  terminal-emulator
```

| Module | Package | Purpose |
|---|---|---|
| `terminal-emulator` | `com.termux.terminal` | VT100/xterm emulation engine, JNI pty, zero internal deps |
| `terminal-view` | `com.termux.view` | Android `View` rendering for the terminal |
| `termux-shared` | `com.termux.shared` | Shared logic: settings, file utils, error system, crash handling, constants |
| `app` | `com.termux` | UI activities, services, app entry point |

- `terminal-view` exposes `terminal-emulator` via `api()` (transitive). Don't change this to `implementation()` without understanding the impact on consumers.
- Modify `terminal-emulator` for emulation bugs. Modify `app` for UI/Activity bugs. Modify `termux-shared` for cross-cutting concerns.

## Code Conventions

- **Pure Java.** No Kotlin source files exist. Kotlin/Compose libraries in the version catalog are unused — do not add `.kt` files.
- **Hungarian notation:** instance fields use `m` prefix (`mTermuxService`, `mIsVisible`).
- **`LOG_TAG`:** every class defines `private static final String LOG_TAG = "ClassName";` at the top.
- **`final` classes** for concrete implementations (e.g., `TerminalView`, `TerminalSession`).
- **Javadoc** on all public methods with `@param`/`@return`. Use `{@link ClassName}` for cross-references.
- **Constants in `TermuxConstants.java`** (1338 lines) — all string constants, paths, intent actions, and extras live here. Add new constants here, not scattered across classes.
- Indent: 4 spaces (2 for YAML). LF line endings. UTF-8. See `.editorconfig`.

## Error Handling

- **Return `Error` objects, don't throw.** `null` means success:
  ```java
  Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(ctx, true, true);
  if (error != null) {
      Logger.logErrorExtended(LOG_TAG, "Failed\n" + error);
      return;
  }
  ```
- **`Errno` class** defines error codes (`ERRNO_SUCCESS`, `ERRNO_CANCELLED`, `ERRNO_FAILED`). Use `Errno.getError()` factory methods.
- **Try/catch is targeted,** not blanket. Catch specific exceptions (`IOException`, `BadTokenException`). Log via `Logger`, show Toast to user, set state flag (e.g., `mIsInvalidState = true`).
- **Crash handling:** `CrashHandler` (uncaught exceptions) → writes to crash log file → notifies app via broadcast. Set in `TermuxApplication.onCreate()`.
- **Logging:** Use `com.termux.shared.logger.Logger`, not `android.util.Log` directly. Logger handles Android's 4068-byte logcat limit by splitting long messages.

## Architecture Patterns

- **Client Interface Pattern:** Interfaces define contracts (`TerminalSessionClient`, `TerminalViewClient`). Base classes provide no-op defaults (`TermuxTerminalSessionClientBase`). Concrete implementations in `app` extend bases. Follow this pattern for new callbacks.
- **Service lifecycle:** `TermuxService` outlives `TermuxActivity`. Activity re-binds on rotation/restart. Don't store activity references in the service — use the client interface.
- **Static utility classes** for stateless helpers (`TermuxUtils`, `TermuxThemeUtils`, `DataUtils`).

## Design Principles

- **SRP:** Each module has one reason to change. `terminal-emulator` knows nothing about Android UI. `termux-shared` knows nothing about specific activities.
- **OCP:** Extend via the Client Interface Pattern — add new behavior by implementing interfaces, don't modify existing classes.
- **DIP:** `app` depends on abstractions in `termux-shared` and `terminal-view`, not on concrete internals.
- **DRY:** Shared logic belongs in `termux-shared`. Don't duplicate logic across `app` activities.
- **KISS/YAGNI:** Don't add abstractions until a second use case exists. The codebase favors concrete classes over interfaces where the pattern isn't needed.
- **Robustness:** Every external input (intents, file paths, user commands) is validated before use. `FunctionErrno` defines validation error codes.

## Gotchas

- **targetSdk 28 is intentional** — raising it triggers scoped storage and other restrictions that break Termux's file access model.
- **ABI splits** are enabled by default for debug builds (`arm64-v8a` + universal). Controlled by `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS` env var.
- **Bootstrap download** happens at build time via `downloadBootstraps` Gradle task. Checksums are verified. Zips are in `.gitignore`.
- **JitPack NDK** (`29.0.14206865`) differs from local/CI NDK (`30.0.14904198`). This is expected — JitPack uses an older NDK.
- **Shared UID** (`com.termux`) — all Termux apps share a Linux UID. APKs must be signed with the same key.
