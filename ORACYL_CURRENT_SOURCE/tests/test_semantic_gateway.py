from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
import oracyl_semantic_gateway as gateway


def valid_turn():
    return {
        "schema": "ORACYL_SEMANTIC_TURN_V1",
        "turn_id": "turn-001",
        "thread_id": "thread-001",
        "surface_text": "Mara did not lend the key to Ivo yesterday. Who has it now?",
        "speech_act": "QUERY",
        "clauses": [{
            "clause_id": "c1", "predicate": "transfer", "arguments": [
                {"role": "giver", "entity_id": "person:mara", "surface": "Mara"},
                {"role": "item", "entity_id": "object:key", "surface": "the key"},
                {"role": "recipient", "entity_id": "person:ivo", "surface": "Ivo"},
            ], "polarity": "NEGATED", "tense": "PAST", "modality": "ACTUAL",
            "confidence": 0.97, "epistemic_status": "OPERATOR_STATEMENT_UNVERIFIED",
        }],
        "references": [{"mention": "it", "entity_id": "object:key", "status": "RESOLVED"}],
        "temporal_context": {"utterance_time": "2026-10-18T00:00:00Z",
                             "effective_time": "2026-10-17", "timezone": "America/Chicago"},
        "provenance": {"source_kind": "operator_via_local_qwen", "source_id": "chat:thread-001",
                       "captured_at": "2026-10-18T00:00:00Z"},
        "ambiguity": {"requires_clarification": False, "alternatives": [], "question": ""},
    }


class SemanticGatewayTest(unittest.TestCase):
    def test_typed_negation_roles_and_provenance_reach_victor(self):
        calls = []
        def dispatch(_private, request_json):
            request = json.loads(request_json); calls.append(request)
            if request["action"] == "teach":
                return json.dumps({"ok": True, "result": {"status": "CER_NATIVE_SEMANTIC_BATCH", "receipt_id": "r1"}})
            return json.dumps({"ok": True, "result": {"status": "NATIVE_INQUIRY_OPEN", "text": "Unknown."}})
        result = gateway.coordinate_turn("/private", "/source", valid_turn(), dispatch=dispatch)
        self.assertEqual(result["status"], "VICTOR_RESULT")
        self.assertEqual([c["action"] for c in calls], ["teach", "ask"])
        meaning = calls[0]["rows"][0]["meaning"]
        self.assertEqual(meaning["predicate"], "oracyl_interpretation:transfer")
        self.assertEqual(meaning["arguments"], ["giver=Mara", "item=the key", "recipient=Ivo"])
        self.assertEqual(meaning["scope"]["polarity"], "NEGATED")
        self.assertEqual(meaning["scope"]["epistemic_status"], "QWEN_INTERPRETATION_PROPOSED_UNVERIFIED")
        self.assertTrue(result["verbalization_contract"]["must_not_add_claims"])

    def test_ambiguity_never_reaches_victor(self):
        turn = valid_turn()
        turn["references"][0] = {"mention": "it", "entity_id": None, "status": "AMBIGUOUS"}
        turn["ambiguity"] = {"requires_clarification": True,
                             "alternatives": ["the key", "the door"],
                             "question": "Does ‘it’ mean the key or the door?"}
        calls = []
        result = gateway.coordinate_turn("/private", "/source", turn, dispatch=lambda *args: calls.append(args))
        self.assertEqual(result["status"], "INTERPRETATION_REJECTED")
        self.assertFalse(result["victor_called"])
        self.assertEqual(calls, [])

    def test_low_confidence_never_teaches(self):
        turn = valid_turn(); turn["clauses"][0]["confidence"] = 0.42
        result = gateway.coordinate_turn("/private", "/source", turn, dispatch=lambda *_: self.fail("Victor called"))
        self.assertIn("low_interpretation_confidence", result["reasons"])

    def test_schema_rejects_hidden_fields(self):
        turn = valid_turn(); turn["authority"] = "execute_anything"
        with self.assertRaises(gateway.SemanticProtocolError):
            gateway.validate_envelope(turn)

    def test_qwen_cannot_impersonate_operator_provenance(self):
        turn = valid_turn(); turn["provenance"]["source_kind"] = "operator_explicit_statement"
        with self.assertRaises(gateway.SemanticProtocolError):
            gateway.validate_envelope(turn)


if __name__ == "__main__":
    unittest.main()