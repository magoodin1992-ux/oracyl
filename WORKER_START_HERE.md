# ORACYL WORKER WORKSPACE — START HERE

This folder is a **self-contained copy** created for a new engineering worker. It is separate from the prior Oracyl workspace. Work only inside this folder. Do not produce recursive backups, duplicate the GGUF, or create additional giant archives.

## Mission
Finish the **digital Android harness source**, not an APK. Preserve the approved responsive UI and implement/test actual local Qwen via llama.cpp, the separate native Victor F/CER-BC system, persistent state management and reversible Victor import/export, document/research/task pipelines, GeckoView as an isolated real browser, navigation, and lifecycle/security. Do not fake functional tests. Compilation, signing, GitHub, and device deployment require separate explicit Operator authorization.

## Files
- `ORACYL_CURRENT_SOURCE/` — recovered minimal Kotlin/JS Android source tree and its tests. The source is a **partial reconstruction**, not a complete previously tested implementation.
- `reference/Oracyl_Responsive_Desktop_Portrait.html` — canonical approved responsive UI, do not redesign.
- `reference/ORACYL_UI_CONTRACT.json` — visual/navigation contract.
- `reference/ORACYL_UI_END_USER_MISSION.md` — end-user product mission.
- `reference/ORACYL_UI_NODES.jsonl` — UI nodes.
- `dependencies/qwen2.5-0.5b-instruct-q4_k_m.gguf` — real local development model; do not copy again.
- `dependencies/llama-b11707-bin-android-arm64.tar.gz` — Android ARM64 llama.cpp distribution. Its support for later Qwen3.5 4B must be verified.
- `victor/VICTOR_POST104_RELATION_FORGING_CONTINUATION_20261017.zip` — designated protected post-104 native Victor baseline; use immutable reference plus a separate writable runtime state.
- `victor/VICTOR_POST104_RELATION_FORGING_HANDOFF_20261017.md` — Victor native state handoff.
- `ORACYL_CURRENT_SOURCE/RECOVERY_STATUS.md` and `tests/results/DIGITAL_HARNESS_AUDIT_20261017.md` — current recovery notes and historical audit; old pass counts must be independently reproduced.

## Operational constraints
- Change only this worker workspace, not the original.
- Don't create new bulk archives, backups or Library uploads.
- No GitHub, Gradle APK builds, signing, or physical-device tests without permission.
- Keep model state, Victor state, and chat storage distinct.
- Ensure Victor checkpoint handling cannot silently overwrite user state.
- UI eight sections in exact order: Chats, Research, Tasks, Apps, Tabs, Projects, Skills, Accounts.
- UI supports phone portrait, DeX desktop, sidebar, pinned Oracyl tab, persistence and truthful errors.

**Workspace portability:** A different ChatGPT Work session may not share this container's `/mnt/data` filesystem. Verify access to this actual folder before starting. If inaccessible, request it as an uploaded attachment or a supported shared location. Mere knowledge of this path does not grant file access.