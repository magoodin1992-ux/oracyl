#!/usr/bin/env python3
"""Stage the supplied Android ARM64 llama.cpp runtime into Gradle's build dir."""
from __future__ import annotations

import os
import shutil
import struct
import sys
import tarfile
from pathlib import Path


def is_arm64_elf(path: Path) -> bool:
    raw = path.read_bytes()[:20]
    return len(raw) == 20 and raw[:4] == b"\x7fELF" and raw[4] == 2 and struct.unpack("<H", raw[18:20])[0] == 183


def main(archive_name: str, destination_name: str) -> None:
    archive = Path(archive_name).resolve()
    destination = Path(destination_name).resolve()
    if not archive.is_file() or archive.stat().st_size > 100_000_000:
        raise SystemExit("llama.cpp archive is missing or outside the expected bound")
    staging = destination.with_name(destination.name + ".incoming")
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    selected = {}
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle.getmembers():
            name = member.name
            base = Path(name).name
            if not member.isfile() or not name.startswith("llama-b11707/"):
                continue
            if base == "llama-server" or (base.endswith(".so") and "/" not in name.removeprefix("llama-b11707/")):
                if base in selected:
                    raise SystemExit("duplicate llama.cpp runtime member")
                source = bundle.extractfile(member)
                if source is None:
                    raise SystemExit("unable to read llama.cpp runtime member")
                output_name = "liboracyl_llama_server.so" if base == "llama-server" else base
                target = staging / output_name
                with source, target.open("wb") as output:
                    shutil.copyfileobj(source, output, 128 * 1024)
                os.chmod(target, 0o755)
                if not is_arm64_elf(target):
                    raise SystemExit(f"not an ARM64 ELF: {base}")
                selected[base] = target
    required = {"llama-server", "libllama-server-impl.so", "libllama-common.so", "libllama.so",
                "libmtmd.so", "libggml.so", "libggml-base.so"}
    missing = sorted(required - selected.keys())
    if missing:
        raise SystemExit("missing llama.cpp runtime members: " + ", ".join(missing))
    shutil.rmtree(destination, ignore_errors=True)
    staging.rename(destination)
    print(f"staged {len(selected)} ARM64 llama.cpp runtime files")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: stage_llama_runtime.py ARCHIVE DESTINATION")
    main(sys.argv[1], sys.argv[2])