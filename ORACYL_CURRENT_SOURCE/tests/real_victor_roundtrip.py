from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parent
ARCHIVE=WORK/'victor/VICTOR_POST104_RELATION_FORGING_CONTINUATION_20261017.zip'
EXPECTED='0c34b8854105af6f37b705a7a9015c468185adfe5478c179dc66d1734e6652c1'
assert hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()==EXPECTED
sys.path.insert(0,str(ROOT/'app/src/main/python'));sys.path.insert(0,str(ROOT/'tests'))
from oracyl_semantic_gateway import coordinate_turn
from test_semantic_gateway import valid_turn

with tempfile.TemporaryDirectory(prefix='oracyl-victor-real-') as folder:
    target=Path(folder)
    with zipfile.ZipFile(ARCHIVE) as bundle:
        selected=[item for item in bundle.infolist() if item.filename.startswith(('victor_world_dialogue_state/','victor_observation_clone/SOURCE/'))]
        assert len(selected)>4000
        for item in selected:
            parts=Path(item.filename).parts
            assert '..' not in parts and not item.filename.startswith('/')
            bundle.extract(item,target)
    result=coordinate_turn(str(target/'victor_world_dialogue_state'),str(target/'victor_observation_clone/SOURCE'),valid_turn())
    receipt=result['typed_receipt'];native=result['victor']
    assert result['status']=='VICTOR_RESULT'
    assert receipt['status']=='CER_NATIVE_SEMANTIC_BATCH' and receipt['rows']==1
    assert native['status']=='NATIVE_INQUIRY_OPEN'
    assert result['verbalization_contract']['must_not_add_claims'] is True
    print(json.dumps({'status':result['status'],'typed_status':receipt['status'],'victor_status':native['status'],
                      'research_requested':bool(native.get('research_request') or native.get('research_handoff'))},sort_keys=True))