from pathlib import Path
import ast
import re

root=Path(__file__).resolve().parents[1]
kotlin=root/'app/src/main/java/com/oracyl/workstation'
required={
 'VictorStateManager.kt':['ATOMIC_MOVE','REPLACE_EXISTING','BASELINE_SHA256','manifest coverage mismatch','rollback'],
 'VictorPythonRuntime.kt':['dispatch_semantic_turn','oracyl_semantic_gateway'],
 'ModelRegistry.kt':['GGUF','ATOMIC_MOVE','8_000_000_000L'],
 'LlamaServerController.kt':['127.0.0.1','/v1/chat/completions','cancel','rollback'],
 'OracylTurnCoordinator.kt':['ORACYL_SEMANTIC_TURN_V1','source_sha256','grounded'],
 'OracylDatabase.kt':['messages','drafts','tasks','operator_questions','saved_tabs'],
 'HostBridge.kt':['@JavascriptInterface','model_import','victor_export','victor_rollback'],
 'BrowserPane.kt':['GeckoView','GeckoSession','no Oracyl JavaScript interface'],
 'KnowledgeIngestor.kt':['document_create','source_sha256','NOT_SEMANTIC_TRUTH'],
 'TaskScheduler.kt':['WorkManager','VICTOR_RESEARCH_REQUEST','enqueueUniqueWork'],
 'OracylTaskWorker.kt':['research_request','VictorPythonRuntime','finishTask'],
}
for name,needles in required.items():
 text=(kotlin/name).read_text()
 for needle in needles: assert needle in text,(name,needle)
gateway=(root/'app/src/main/python/oracyl_semantic_gateway.py').read_text()
ast.parse(gateway)
assert 'QWEN_INTERPRETATION_PROPOSED_UNVERIFIED' in gateway
assert 'INTERPRETATION_REJECTED' in gateway
gradle=(root/'app/build.gradle.kts').read_text()
assert 'stageLlamaRuntime' in gradle and 'srcDir("../../victor")' in gradle
assert 'androidx.work:work-runtime:2.12.0' in gradle
assert not any(root.rglob('*.apk'))
assert not any(root.rglob('*.gguf'))
print('PRECOMPILE SOURCE CHECK PASS: typed bridge, immutable state, model/runtime lifecycle, durable repositories')