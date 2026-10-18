"""Typed Oracyl boundary around Victor's unchanged MobileBridge JSON RPC.

Qwen proposes an interpretation.  This module validates it, records accepted
clauses as explicitly unverified semantic observations through Victor's real
``teach`` action, and then asks Victor through its real ``ask`` action.  A
Qwen interpretation is never promoted to verified knowledge or authority.
"""
from __future__ import annotations

import importlib
import json
import math
import re
import sys
from pathlib import Path

SCHEMA = "ORACYL_SEMANTIC_TURN_V1"
VICTOR_VERSION = "VICTOR_ARM64_RPC_V1"
_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:-]{0,95}$")
_SPEECH_ACTS = {"QUERY", "ASSERTION", "COMMAND", "CORRECTION", "ACKNOWLEDGEMENT"}
_POLARITIES = {"AFFIRMED", "NEGATED", "UNKNOWN"}
_TENSES = {"PAST", "PRESENT", "FUTURE", "TIMELESS", "UNKNOWN"}
_MODALITIES = {"ACTUAL", "POSSIBLE", "NECESSARY", "REQUESTED", "UNKNOWN"}
_REFERENCES = {"RESOLVED", "UNRESOLVED", "AMBIGUOUS"}


class SemanticProtocolError(ValueError):
    pass


def _exact(value, keys, name):
    if type(value) is not dict or set(value) != set(keys):
        raise SemanticProtocolError(f"{name} schema mismatch")


def _text(value, limit, name, *, allow_empty=False):
    if type(value) is not str or len(value) > limit or (not allow_empty and not value.strip()):
        raise SemanticProtocolError(f"invalid {name}")
    return " ".join(value.split())


def _identifier(value, name):
    value = _text(value, 96, name)
    if not _ID.fullmatch(value):
        raise SemanticProtocolError(f"invalid {name}")
    return value


def validate_envelope(raw):
    _exact(raw, ("schema", "turn_id", "thread_id", "surface_text", "speech_act",
                 "clauses", "references", "temporal_context", "provenance",
                 "ambiguity"), "semantic turn")
    if raw["schema"] != SCHEMA:
        raise SemanticProtocolError("unsupported semantic turn schema")
    turn_id = _identifier(raw["turn_id"], "turn id")
    thread_id = _identifier(raw["thread_id"], "thread id")
    surface = _text(raw["surface_text"], 4096, "surface text")
    speech_act = raw["speech_act"]
    if speech_act not in _SPEECH_ACTS:
        raise SemanticProtocolError("invalid speech act")

    clauses = raw["clauses"]
    if type(clauses) is not list or not 1 <= len(clauses) <= 12:
        raise SemanticProtocolError("clauses must contain 1 through 12 items")
    normalized_clauses = []
    seen_clause_ids = set()
    for row in clauses:
        _exact(row, ("clause_id", "predicate", "arguments", "polarity", "tense",
                     "modality", "confidence", "epistemic_status"), "clause")
        clause_id = _identifier(row["clause_id"], "clause id")
        if clause_id in seen_clause_ids:
            raise SemanticProtocolError("duplicate clause id")
        seen_clause_ids.add(clause_id)
        predicate = _identifier(row["predicate"], "predicate")
        arguments = row["arguments"]
        if type(arguments) is not list or not 1 <= len(arguments) <= 20:
            raise SemanticProtocolError("clause arguments must contain 1 through 20 items")
        normalized_args = []
        seen_roles = set()
        for arg in arguments:
            _exact(arg, ("role", "entity_id", "surface"), "argument")
            role = _identifier(arg["role"], "semantic role")
            if role in seen_roles:
                raise SemanticProtocolError("duplicate semantic role")
            seen_roles.add(role)
            normalized_args.append({
                "role": role,
                "entity_id": _identifier(arg["entity_id"], "entity id"),
                "surface": _text(arg["surface"], 160, "argument surface"),
            })
        polarity, tense, modality = row["polarity"], row["tense"], row["modality"]
        if polarity not in _POLARITIES or tense not in _TENSES or modality not in _MODALITIES:
            raise SemanticProtocolError("invalid clause operators")
        confidence = row["confidence"]
        if type(confidence) not in (int, float) or isinstance(confidence, bool) or not math.isfinite(confidence) or not 0 <= confidence <= 1:
            raise SemanticProtocolError("invalid interpretation confidence")
        normalized_clauses.append({
            "clause_id": clause_id, "predicate": predicate, "arguments": normalized_args,
            "polarity": polarity, "tense": tense, "modality": modality,
            "confidence": float(confidence),
            "epistemic_status": _identifier(row["epistemic_status"], "epistemic status"),
        })

    references = raw["references"]
    if type(references) is not list or len(references) > 20:
        raise SemanticProtocolError("invalid references")
    normalized_refs = []
    for ref in references:
        _exact(ref, ("mention", "entity_id", "status"), "reference")
        if ref["status"] not in _REFERENCES:
            raise SemanticProtocolError("invalid reference status")
        entity_id = ref["entity_id"]
        if entity_id is not None:
            entity_id = _identifier(entity_id, "reference entity id")
        normalized_refs.append({"mention": _text(ref["mention"], 160, "reference mention"),
                                "entity_id": entity_id, "status": ref["status"]})

    _exact(raw["temporal_context"], ("utterance_time", "effective_time", "timezone"), "temporal context")
    temporal = {key: _text(raw["temporal_context"][key], 96, key, allow_empty=True)
                for key in ("utterance_time", "effective_time", "timezone")}
    _exact(raw["provenance"], ("source_kind", "source_id", "captured_at"), "provenance")
    provenance = {key: _text(raw["provenance"][key], 160, key)
                  for key in ("source_kind", "source_id", "captured_at")}
    if provenance["source_kind"] != "operator_via_local_qwen":
        raise SemanticProtocolError("Qwen may only identify operator-mediated local input")
    _exact(raw["ambiguity"], ("requires_clarification", "alternatives", "question"), "ambiguity")
    ambiguity = raw["ambiguity"]
    if type(ambiguity["requires_clarification"]) is not bool or type(ambiguity["alternatives"]) is not list or len(ambiguity["alternatives"]) > 8:
        raise SemanticProtocolError("invalid ambiguity block")
    alternatives = [_text(item, 240, "ambiguity alternative") for item in ambiguity["alternatives"]]
    question = _text(ambiguity["question"], 320, "clarification question", allow_empty=True)
    return {
        "schema": SCHEMA, "turn_id": turn_id, "thread_id": thread_id,
        "surface_text": surface, "speech_act": speech_act,
        "clauses": normalized_clauses, "references": normalized_refs,
        "temporal_context": temporal, "provenance": provenance,
        "ambiguity": {"requires_clarification": ambiguity["requires_clarification"],
                      "alternatives": alternatives, "question": question},
    }


def _clarification_reasons(turn):
    reasons = []
    if turn["ambiguity"]["requires_clarification"]:
        reasons.append("qwen_reported_ambiguity")
    if any(row["status"] != "RESOLVED" for row in turn["references"]):
        reasons.append("unresolved_reference")
    if any(row["confidence"] < 0.70 for row in turn["clauses"]):
        reasons.append("low_interpretation_confidence")
    return reasons


def _teaching_rows(turn):
    rows = []
    for clause in turn["clauses"]:
        args = [f'{arg["role"]}={arg["surface"]}' for arg in clause["arguments"]]
        meaning = {
            "predicate": "oracyl_interpretation:" + clause["predicate"],
            "arguments": args,
            "scope": {
                "turn_id": turn["turn_id"], "thread_id": turn["thread_id"],
                "speech_act": turn["speech_act"], "polarity": clause["polarity"],
                "tense": clause["tense"], "modality": clause["modality"],
                "epistemic_status": "QWEN_INTERPRETATION_PROPOSED_UNVERIFIED",
                "source_kind": turn["provenance"]["source_kind"],
                "source_id": turn["provenance"]["source_id"],
                "qwen_confidence_milli": int(round(clause["confidence"] * 1000)),
            },
        }
        rows.append({"text": f'Oracyl interpretation {turn["turn_id"]}/{clause["clause_id"]}: {turn["surface_text"]}',
                     "meaning": meaning})
    return rows


def _load_dispatch(source_root):
    source = Path(source_root).resolve()
    python_dir = source / "python"
    if not python_dir.is_dir() or not (python_dir / "victor_mobile_bridge.py").is_file():
        raise SemanticProtocolError("Victor source root is unavailable")
    path = str(python_dir)
    if path not in sys.path:
        sys.path.insert(0, path)
    return importlib.import_module("victor_mobile_bridge").dispatch_json


def coordinate_turn(private_dir, source_root, envelope, dispatch=None):
    turn = validate_envelope(envelope)
    reasons = _clarification_reasons(turn)
    if reasons:
        return {
            "schema": "ORACYL_TURN_RESULT_V1", "status": "INTERPRETATION_REJECTED",
            "turn_id": turn["turn_id"], "reasons": reasons,
            "clarification_question": turn["ambiguity"]["question"] or
                "I found more than one defensible interpretation. What did you mean?",
            "victor_called": False,
        }
    if dispatch is None:
        dispatch = _load_dispatch(source_root)
    rows = _teaching_rows(turn)
    teaching = json.loads(dispatch(private_dir, json.dumps({
        "version": VICTOR_VERSION, "action": "teach", "rows": rows,
    }, ensure_ascii=False, separators=(",", ":"))))
    if not teaching.get("ok"):
        return {"schema": "ORACYL_TURN_RESULT_V1", "status": "VICTOR_REJECTED_INTERPRETATION",
                "turn_id": turn["turn_id"], "victor_called": True,
                "error": teaching.get("message", teaching.get("error", "Victor rejected typed meaning"))}
    answer = json.loads(dispatch(private_dir, json.dumps({
        "version": VICTOR_VERSION, "action": "ask", "text": turn["surface_text"],
    }, ensure_ascii=False, separators=(",", ":"))))
    if not answer.get("ok"):
        return {"schema": "ORACYL_TURN_RESULT_V1", "status": "VICTOR_ERROR",
                "turn_id": turn["turn_id"], "victor_called": True,
                "typed_receipt": teaching["result"],
                "error": answer.get("message", answer.get("error", "Victor failed"))}
    return {
        "schema": "ORACYL_TURN_RESULT_V1", "status": "VICTOR_RESULT",
        "turn_id": turn["turn_id"], "victor_called": True,
        "typed_receipt": teaching["result"], "victor": answer["result"],
        "verbalization_contract": {
            "source": "victor_only", "must_preserve_unknowns": True,
            "must_not_add_claims": True, "must_not_claim_action_success": True,
        },
    }


def dispatch_semantic_turn(private_dir, source_root, envelope_json):
    try:
        envelope = json.loads(envelope_json)
        return json.dumps({"ok": True, "result": coordinate_turn(private_dir, source_root, envelope)},
                          ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except (SemanticProtocolError, ValueError, TypeError, OSError, ImportError) as exc:
        return json.dumps({"ok": False, "error": type(exc).__name__, "message": str(exc)[:300]},
                          ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def dispatch_victor_rpc(private_dir, source_root, request_json):
    """Direct access to MobileBridge's own strict action schemas."""
    try:
        request = json.loads(request_json)
        if type(request) is not dict or request.get("version") != VICTOR_VERSION:
            raise SemanticProtocolError("invalid Victor RPC envelope")
        dispatch = _load_dispatch(source_root)
        return dispatch(private_dir, json.dumps(request, ensure_ascii=False, separators=(",", ":")))
    except (SemanticProtocolError, ValueError, TypeError, OSError, ImportError) as exc:
        return json.dumps({"ok": False, "error": type(exc).__name__, "message": str(exc)[:300]},
                          ensure_ascii=False, sort_keys=True, separators=(",", ":"))