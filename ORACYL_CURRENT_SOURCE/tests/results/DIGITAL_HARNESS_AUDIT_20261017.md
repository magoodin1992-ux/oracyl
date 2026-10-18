# Oracyl — pre-signing digital harness audit (2026-10-17)

**Scope:** read-only source/assets checks and a real Chromium rendering/interaction harness with mocked Android `OracylHost` boundary. No Android compilation, emulator, physical device, GitHub, or signing.

## Tests executed
- `python tests/check_victor_bundle.py` — PASS: bundled unchanged native Victor portable ZIP hash, 4,334 entries, source/state entries, import/export/restore handler contracts.
- `node --check app/src/main/assets/oracyl_native.js` — PASS.
- `python tests/digital_ui_harness.py` — **36/36 PASS** after navigation source repair. Four viewport profiles: 1920×1080 DeX; 360×780 phone; 1024×768 windowed; 430×850 narrow. Validated app bridge initialization, errors, title, portrait/desktop responsive mode, no document horizontal overflow, snapshot rendering, send dispatch, Tasks and Accounts routing. Four Accounts navigation failures were fixed by routing category buttons through the native workspace while retaining canonical dropdown logic.
- `python tests/expanded_harness.py` — **28/28 PASS** (desktop and phone): Chats, waiting knowledge job, Tabs unavailable state, Settings controls for model selection, Victor import/export/rollback/restore, dispatched native SAF/export actions, untrusted assistant text rendered as text, no JS exceptions.
- Bundled assets — PASS: Qwen GGUF magic, 491,400,032 bytes and SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`. Fifteen native ARM64 ELF `.so` files present; `readelf -d` reveals no missing non-system transitive SONAME in bundled shared-library set. Assets approximately 793.7 MB before packaging.

## Product defects found and repaired
- Native top-level navigation originally exposed Tasks via one hardcoded handler; Accounts and other categories could expand prototype menus without opening honest native workspaces. Updated native adapter to route all eight desktop category buttons through their respective real/explicitly unavailable workspaces. Re-tested at all four viewports.

## Release blockers and unverified criteria (do NOT sign yet)
1. **GeckoView is not implemented:** the dependency is commented out in `app/build.gradle.kts`; `browser_open` returns an unavailable notice; persistent GeckoView sessions and browser tabs cannot pass acceptance.
2. **Victor is bundled but not executing:** the Android MobileBridge adapter and actual native conversation/learning/research loop are not connected. State backup UI is only static/browser simulated, not verified Android file import/export.
3. **Qwen executable path remains unproven on Android:** native `liboracyl_server.so` exists but no actual Android launch, model load, model-switch, chat completion, memory/thermal or app-lifecycle verification. Runtime source needs synchronized model-switch/generation review.
4. **Android build not yet run:** no Gradle compilation, dex verification, manifest merge, API 36 native executable security check, physical Samsung S24 Ultra or DeX exercise. Browser preview is not Android host evidence.
5. **Visual fidelity incomplete:** the canonical design is present, but native workspace overlays for Settings/Accounts/Research/etc. are not all measured against the official phone/desktop reference screenshots and some remain preview/unavailable.
6. **Settings/voice/external accounts/apps/skills** have placeholders or unavailable endpoints; no false successful integrations should be claimed.

## Gate decision
**DIGITAL HARNESS PARTIAL PASS; NOT READY FOR SIGNING.** 64/64 executed browser-level interaction checks passed following one source fix. This does not establish feature-complete Android acceptance. Keep all work local until the Operator separately authorizes GitHub compilation/signing.