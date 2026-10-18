# Oracyl pre-compilation source status — 2026-10-18

This worktree has advanced beyond the recovered WebView shell. It remains **pre-compilation source**, in accordance with the Operator's prohibition on Gradle/APK compilation, signing, deployment, emulators, and physical-device testing in this phase.

## Implemented in source

- Approved responsive HTML remains byte-identical to the reference; the injected adapter now uses an asynchronous, callback-correlated native boundary and suppresses the preview's fake-send behavior.
- Qwen → Victor semantic gateway validates a typed turn envelope carrying speech act, entities and roles, reference resolution, polarity/negation, tense, modality, uncertainty, temporal context, and provenance. Ambiguous/unresolved/low-confidence interpretations are rejected before Victor is called.
- Accepted Qwen interpretations reach the **real unchanged Victor MobileBridge** through `VICTOR_ARM64_RPC_V1`: first as explicit `QWEN_INTERPRETATION_PROPOSED_UNVERIFIED` semantic rows via `teach`, then through Victor's `ask`. Qwen content is not marked as confirmed knowledge or operator authority.
- Victor → Qwen has a restrictive verbalization contract. Native Victor text is used directly when present; structured results may be verbalized only with the exact source digest and lexical grounding, otherwise Oracyl falls back to a truthful structured-result notice.
- Victor runs through Chaquopy Python 3.13 against the package's actual `victor_mobile_bridge.py`; no replacement Victor persona or mock kernel was introduced.
- Protected Victor baseline installs from the one original archive into immutable version directories. Import/export uses per-file SHA-256 manifests, bounded safe extraction, atomic active-pointer replacement, prior-version rollback, and no silent overwrite.
- GGUF imports use SAF, GGUF-magic validation, an 8 GB bound, content-addressed immutable filenames, atomic registry writes, safe model switching, cancellation, and prior-model rollback on launch failure.
- The supplied llama.cpp ARM64 package is staged only into Gradle's generated build directory. The local controller launches its real `llama-server` on `127.0.0.1`, waits for health, sends OpenAI-compatible local chat-completion requests, and terminates cleanly. The packaged `libllama.so` contains explicit Qwen 3.5 architecture and vocabulary support.
- Persistent SQLite storage exists for chats, messages, drafts, tasks, Operator Questions, and saved GeckoView tabs. Fixture chats are removed at native startup and replaced by durable records.
- GeckoView is wired as a separate untrusted browser surface with persistent tab/session state. It receives no `OracylHost` JavaScript interface. HTTPS/HTTP URL validation and explicit save-to-Tabs behavior are present.
- UTF-8 text/Markdown/JSON SAF ingestion creates provenance-bearing Victor private document records in bounded chunks. It truthfully reports `NOT_SEMANTIC_TRUTH`; ingestion alone is not claimed as native learning.
- Native research actions expose Victor's own strict `research_pending`, transport request/result, teacher-review, and answer ports rather than inventing a separate research cognition layer.
- Tasks and Operator Questions are durable and truthful. WorkManager schedules only the bounded `VICTOR_RESEARCH_REQUEST` task schema, records native Victor results, supports pause/resume/cancel, and never treats external transport as completed research.

## Digitally exercised now

- Semantic protocol unit suite: 5/5 pass.
- Real isolated protected-Victor round-trip: typed semantic receipt `CER_NATIVE_SEMANTIC_BATCH`; Victor result `NATIVE_INQUIRY_OPEN`; native research request surfaced.
- llama.cpp package/staging checks: 22 ARM64 ELF files selected; Qwen 3.5 symbols present.
- Source/recovery checks, Python parse/compile, JavaScript syntax, and asynchronous native-adapter VM test pass.
- Approved UI asset is byte-identical to its reference.

## Explicit remaining blockers

1. Android/Gradle compilation was not run because the Operator prohibited it for this phase. Therefore Kotlin/manifest/dependency resolution, Chaquopy packaging, GeckoView linkage, and Android resource merge remain unverified.
2. No ARM64 runtime can execute in this x86_64 worker. Actual llama-server launch, Qwen 2.5 development-model load, Qwen 3.5 4B import/load, token generation, cancellation, memory/thermal behavior, and lifecycle recovery require an authorized Android build/device run.
3. The worker image lacks a Chromium/Playwright browser executable, so the historical rendered viewport harness could not be rerun. Static UI identity and adapter behavior passed; fresh rendered phone/DeX geometry remains pending.
4. GeckoView source is integrated but not compiled or device-run. Navigation, session restoration, back behavior, permissions, and DeX presentation need the authorized Android test.
5. WorkManager task source is integrated but not compiled or device-run; Android process restart, exact-alarm behavior, retry/backoff, and long-running constraints remain unverified. Apps, Projects, Skills presentation, Accounts, voice input, and external service credentials remain truthful non-success surfaces rather than shipped integrations.
6. Automated Gecko page extraction into Victor research packets is not yet implemented; transport packets can be supplied through Victor's real bounded port, and manual browsing is isolated.

**Gate:** substantial pre-compilation source integration complete; **not ready for signing or release** until the prohibited build/device checks are separately authorized and pass.