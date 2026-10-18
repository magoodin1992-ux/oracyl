# Oracyl — Android workstation source

Oracyl combines Victor's native F/CER-BC semantic-node cognition and research with Qwen/llama.cpp for language interpretation and expression. Victor thinks; Qwen talks; the human Operator authorizes.

**Status:** Source-only pre-compilation development. No successful Android build or device execution is established by the uploaded source. See `ORACYL_CURRENT_SOURCE/RECOVERY_STATUS.md`.

## Repository layout

- `ORACYL_CURRENT_SOURCE/` — Gradle Android project, host/native bridges and tests.
- `reference/` — approved HTML, UI contract and mission documents.
- `victor/` — Victor checkpoint handoff; the large native checkpoint is not yet committed.
- `dependencies/` — expected local ARM64 llama.cpp distribution; the binary has not yet been committed.
- `WORKER_START_HERE.md` — original source handoff.

## Binary inputs still needed to compile

Place the following **original unmodified** files alongside `ORACYL_CURRENT_SOURCE/` (not inside its source tree):

- `dependencies/llama-b11707-bin-android-arm64.tar.gz` — original ARM64 llama.cpp package (approximately 76 MB).
- `victor/VICTOR_POST104_RELATION_FORGING_CONTINUATION_20261017.zip` — original protected Victor baseline (approximately 39 MB).

Do not put Qwen's 491 MB development GGUF into Git. The Operator imports a compatible GGUF on device. `ORACYL_DIGITAL_HARNESS_BUILD_READY_20261017.zip` is a redundant release archive and intentionally not committed; its individual source files are committed instead.

**IMPORTANT: This is a PUBLIC repository.** Never commit secrets, private keys, API credentials or mutable Victor state. Anything made public can be copied permanently even if the repository is later deleted.

## Build note

The Gradle project is under `ORACYL_CURRENT_SOURCE/`, not at the repository root. The build calls `tools/stage_llama_runtime.py` and expects sibling directories `../dependencies` and `../victor`. Integration and source tests do not establish APK runtime success.
