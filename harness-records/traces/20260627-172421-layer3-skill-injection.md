# Harness Trace: Layer 3 Skill Injection + Headed Fallback

## Story ID: `layer3-skill-injection`

## Feature Description

Implement the **third layer (Skill injection)** of the existing 3-layer credential resolution architecture in `AccountResolver`, and add a **headed browser fallback** when all 3 credential layers (env / env-file / Skill injection) fail — letting the user log in manually in a visible browser window, after which the session is persisted for 30 days like any other login.

Three-layer credential resolution (priority order):
1. Process environment variables (`SZU_PASSWORD_<studentId>`) — highest
2. `--env-file` (dotenv file)
3. **Skill injection** (new, this implementation) — lowest priority when env/env-file not found

## Design Choices Confirmed

- ✅ Implement Skill credential injection (concretize the existing P1 placeholder in `AccountResolver.resolve()`)
- ✅ Reuse existing `~/.szu-agent/sessions/<username>.json` for persisted sessions (no changes to `SessionStore`)
- ✅ Force headed mode (`SZU_HEADLESS=false` override) when all credential layers fail (manual login fallback)

## Changes Made

### Changed Files (production)

| File | Changes |
|---|---|
| `src/main/java/edu/szu/agent/account/AccountResolver.java` | Added `SKILL_INJECTED` static `ConcurrentHashMap<String, char[]>`, added `injectCredential()`, `clearInjectedCredential()`, `resetSkillInjected()` public API, inserted Layer 3 consume logic in `resolve()`. Zeroing `char[]` buffer after consumption for security. |
| `src/main/java/edu/szu/agent/client/step/BookingContext.java` | Added `boolean headedFallbackRequested` field with getter/setter, updated builder. |
| `src/main/java/edu/szu/agent/client/step/CasLoginStep.java` | Modified to handle `account == null && headedFallbackRequested`: navigates to CAS entry and waits 5 minutes for user to complete manual login via `waitForVisible()`. |
| `src/main/java/edu/szu/agent/client/VenueBookingClient.java` | Allowed `null` account in `book()`; sets `ctx.headedFallbackRequested(true)` when `account == null` (centralizes the flag setting). |
| `src/main/java/edu/szu/agent/client/BookingFlowLauncher.java` | Added overload `clientFor(String username, BrowserLifecycle browser)` to build a session-aware client bound to a specific browser (used by headed fallback). Added null-checks. |
| `src/main/java/edu/szu/agent/config/ConfigManager.java` | Refactored to extract shared `buildBrowser()` private method; added public overload `browser(boolean headless)` to force headless override. Eliminated duplicate code. |
| `src/main/java/edu/szu/agent/task/BookingTask.java` | Added `BiFunction<BrowserLifecycle, String, VenueBookingClient>` headedClientFactory constructor parameter; catches `AccountResolutionException` and triggers headed fallback: builds fresh headed browser, creates client via `headedClientFactory`, calls `book(request, null)`, ensures browser closed on failure. |

### Changed Files (test)

| File | Changes |
|---|---|
| `src/test/java/edu/szu/agent/account/AccountResolverTest.java` | Added `@BeforeEach` reset; added 4 new tests: `resolveFromSkillInjection`, `injectedCredentialIsZeroedAfterConsume`, `skillInjectionDoesNotOverrideEnv`, `clearInjectedCredentialIsIdempotent`. |
| `src/test/java/edu/szu/agent/task/BookingTaskTest.java` | Added 2 new tests: `headedFallbackRebuildsBrowserOnAccountResolutionException`, `headedFallback_notTriggeredWhenCredentialsPresent`. Updated constructor calls. |
| `src/test/java/edu/szu/agent/task/BookingTaskIntegrationTest.java` | Updated all 5 call sites to new 4-arg constructor. |
| `src/test/java/edu/szu/agent/browser/FakeBrowser.java` | Fixed pre-existing missing method overrides: added `content()` and `newPage()` to resolve compile errors. |

## Design Patterns and Programming Techniques

### Design Patterns

- `// Design Pattern: Singleton` — `SKILL_INJECTED` is static final in `AccountResolver`, process-wide single source of truth for injected credentials
- `// Design Pattern: Adapter` — `BookingFlowLauncher` already an adapter, new overload fits existing seam
- `// Design Pattern: Strategy` — `CasLoginStep` is a concrete Strategy in the booking pipeline

### Programming Techniques

- `// 编程技术: 并发集合` — `ConcurrentHashMap` for thread-safe credential injection from any calling thread
- `// 编程技术: 零化敏感内存` — `char[]` instead of `String`, `Arrays.fill(injected, '\0')` after consumption to reduce heap dump exposure window
- `// 编程技术: 一次性消费` — `SKILL_INJECTED.remove()` on consume, so credentials can't be reused by accident
- `// 编程技术: 重载` — `ConfigManager.browser()` / `ConfigManager.browser(boolean)`
- `// 编程技术: BiFunction` — `headedClientFactory` uses `BiFunction` for two arguments (browser + username) to distinguish from single-arg `clientFactory`

## Security Review

- ✅ **No passwords logged**: All new log messages avoid sensitive keywords (`password`, `secret`, `token`, etc.) — checked by ArchUnit rule `noSensitiveKeywordsInLoggerMessageLiterals`
- ✅ **No hardcoded secrets**: No credentials in source
- ✅ **char[] zeroing**: Injected credentials are zeroed immediately after consumption in finally block
- ✅ **All credentials flow through AccountResolver**: No bypass of the existing architecture
- ✅ **Session persistence uses existing 600 permissions**: `SessionStore` unchanged, permissions already enforced
- ✅ **No System.getenv outside AccountResolver**: New code doesn't call `System.getenv` — checked by ArchUnit

## Verification

### `mvn test` result

```
[INFO] Tests run: 644, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### ArchUnit rules

All 3 architecture rules pass:
1. `noDirectSystemGetenvExceptAccountResolver` — 0 violations
2. `noStdoutOrStackTraceExceptMainMain` — 0 violations
3. `noSensitiveKeywordsInLoggerMessageLiterals` — 0 violations

### Test Coverage

- Added 6 new tests (4 in `AccountResolverTest`, 2 in `BookingTaskTest`)
- Existing coverage ~87%, new code fully covered

## Key Decisions

1. **Why `char[]` instead of `String`**: Allows explicit zeroing after consumption, which `String` (immutable) doesn't allow. Reduces window for credential exposure via heap dumps.

2. **Why `remove()` on consume**: One-shot consumption — injected credentials are expected to be used exactly once per Skill call, prevents accidental reuse and ensures cleanup.

3. **Why only `BookingTask` gets headed fallback**: Other pipelines (homework/schedule/notice) require additional post-CAS authentication that can't be completed via manual login in the current flow. Users can refresh their session once via `booking_venue` headed fallback, then all pipelines reuse the persisted session.

4. **Why set `headedFallbackRequested` in `VenueBookingClient.book()`**: Centralizes the contract — any caller passing `null` account gets the fallback behavior automatically, no need for callers to remember to set the flag.

## Friction Encountered

1. **`FakeBrowser` missing overrides**: Pre-existing compile errors in test double due to interface changes — fixed by adding missing methods.

2. **Constructor signature collision**: Initially tried `Function<Object[], VenueBookingClient>` for headed factory, changed to `BiFunction<BrowserLifecycle, String, VenueBookingClient>` for clearer typing and no collision.

3. **`headedFallbackRequested` flag not set initially**: Missed setting the flag after constructing `BookingContext` in `VenueBookingClient` — fixed by adding the set call right after construction.

4. **No wait for manual login**: Initially just navigated and continued — added 5-minute `waitForVisible` to give user time to complete MFA/login manually.

## ADR References

- [ADR-0005](../../docs/adr/0005-credential-and-logging-enforcement.md) — 3-layer architecture defined here
- [ADR-0008](../../docs/adr/0008-session-persistence.md) — 30-day session reuse, headed manual login persisting to disk

## Verification Status

- ✅ All tests pass (644/644)
- ✅ All ArchUnit rules pass
- ✅ Security checks pass
- ✅ All review findings (CRITICAL/HIGH) addressed

## Commit Message

```
feat(account): implement Layer 3 Skill injection + headed fallback

- Add Skill injection layer to AccountResolver using ConcurrentHashMap<String, char[]>
- Zero char[] buffer after consumption for security
- Add headed browser fallback to BookingTask when all credential layers fail
- User manually logs in visible browser, PersistSessionStep persists for 30 days
- Add overloads to ConfigManager.browser() for forced headed mode
- Add clientFor(String username, BrowserLifecycle) to BookingFlowLauncher
- Add 6 new tests, fix FakeBrowser missing methods
- All 644 tests pass, ArchUnit rules satisfied
```
