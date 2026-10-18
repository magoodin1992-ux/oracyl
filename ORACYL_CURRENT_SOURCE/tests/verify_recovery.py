from pathlib import Path
import hashlib,json,re,struct,tarfile,zipfile
root=Path(__file__).resolve().parents[1]
work=root.parent;assets=root/'app/src/main/assets';ui=assets/'index.html';original=work/'reference/Oracyl_Responsive_Desktop_Portrait.html'
assert ui.read_bytes()==original.read_bytes(),'bundled UI must exactly match approved reference'
assert ui.stat().st_size==75785
assert 'OracylHost' in (assets/'oracyl_native.js').read_text()
assert "bind('mInput','mVoice','mNewMessages')" in (assets/'oracyl_native.js').read_text()
assert all(f'id="{element}"' in ui.read_text() for element in ('chatInput','voice','conversationAppend','mInput','mVoice','mNewMessages'))
assert all(s in ui.read_text() for s in ['Research','Tasks','Apps','Tabs','Projects','Skills','Accounts'])
model=work/'dependencies/qwen2.5-0.5b-instruct-q4_k_m.gguf'
if model.exists():
 assert model.read_bytes()[:4]==b'GGUF'
 assert model.stat().st_size==491400032
with (work/'dependencies/llama-b11707-bin-android-arm64.tar.gz').open('rb') as f:
 with tarfile.open(fileobj=f,mode='r:gz') as tf:
  names=tf.getnames();assert any('llama-server' in n for n in names)
victor=work/'victor/VICTOR_POST104_RELATION_FORGING_CONTINUATION_20261017.zip'
assert victor.is_file()
assert hashlib.sha256(victor.read_bytes()).hexdigest()=='0c34b8854105af6f37b705a7a9015c468185adfe5478c179dc66d1734e6652c1'
assert not any(root.rglob('*.gguf'))
assert not any(root.rglob('*.apk'))
print('RECOVERY SOURCE CHECK PASS: canonical UI, external model boundary, native archives, zero APKs')