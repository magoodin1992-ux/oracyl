from pathlib import Path
import io
import struct
import tarfile

root=Path(__file__).resolve().parents[1]
archive=root.parent/'dependencies/llama-b11707-bin-android-arm64.tar.gz'
with tarfile.open(archive,'r:gz') as bundle:
    names=set(bundle.getnames())
    required={'llama-b11707/llama-server','llama-b11707/libllama.so','llama-b11707/libllama-server-impl.so',
              'llama-b11707/libllama-common.so','llama-b11707/libggml.so','llama-b11707/libggml-base.so'}
    assert required<=names,sorted(required-names)
    server=bundle.extractfile('llama-b11707/llama-server').read(20)
    library=bundle.extractfile('llama-b11707/libllama.so').read()
assert server[:4]==b'\x7fELF' and server[4]==2 and struct.unpack('<H',server[18:20])[0]==183
assert b'src/models/qwen35.cpp' in library
assert b'LLM_ARCH_QWEN35' in library and b'LLAMA_VOCAB_PRE_TYPE_QWEN35' in library
print('LLAMA PACKAGE PASS: ARM64 server/runtime present; Qwen 3.5 architecture and vocabulary support compiled in')