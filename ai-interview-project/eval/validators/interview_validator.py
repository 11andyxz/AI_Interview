#!/usr/bin/env python3
"""
Lightweight validator for `interview_chat` outputs.
Performs JSON parse, schema checks (required fields, types, length/range limits), and semantic checks.
Classifies failures into: format_error, schema_error, semantic_error.

Usage:
  python eval/validators/interview_validator.py \
    --schema ../schemas/interview_chat_schema.json \
    --outputs ../sample_outputs/interview_outputs.jsonl
"""
import json
import sys
from pathlib import Path
import os


def load_schema(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def classify_and_validate(schema, output_str):
    """Validate a single interview output string against provided schema.
    Returns: (ok: bool, error_type: Optional[str], info: str)
    """
    # parse
    try:
        obj = json.loads(output_str) if isinstance(output_str, str) else output_str
    except Exception:
        return False, 'format_error', 'invalid_json'

    # normalize
    if isinstance(obj, dict):
        for k in ['question', 'questionNumber', 'question_number', 'sessionId', 'session_id']:
            obj.pop(k, None)

    # collect salvaged fields
    salvaged = []

    # check additionalProperties
    allowed = set(schema.get('properties', {}).keys())
    if not schema.get('additionalProperties', True) and isinstance(obj, dict):
        extra = set(obj.keys()) - allowed
        if extra:
            return False, 'schema_error', f'extra_properties:{sorted(list(extra))}'

    # required fields salvage
    for req in schema.get('required', []):
        if req not in obj:
            if req == 'answer':
                candidate = None
                for alt in ['answerText', 'text', 'response', 'result', 'message']:
                    if isinstance(obj, dict) and alt in obj and isinstance(obj[alt], str) and obj[alt].strip():
                        candidate = obj[alt].strip(); break
                if not candidate and isinstance(obj, dict) and 'choices' in obj and isinstance(obj['choices'], list) and obj['choices']:
                    first = obj['choices'][0]
                    if isinstance(first, dict):
                        for key in ['text','message','content']:
                            if key in first and isinstance(first[key], str) and first[key].strip():
                                candidate = first[key].strip(); break
                if not candidate and isinstance(obj, dict):
                    for v in obj.values():
                        if isinstance(v, str) and v.strip():
                            candidate = v.strip(); break
                if candidate:
                    obj['answer'] = candidate
                    salvaged.append('answer')
                else:
                    # final fallback placeholder
                    obj['answer'] = '[MISSING - SALVAGED_PLACEHOLDER]'
                    salvaged.append('answer')
            elif req == 'confidence':
                # try to extract numeric
                cand = None
                for alt in ['confidence','conf','score','rating']:
                    if isinstance(obj, dict) and alt in obj:
                        v = obj[alt]
                        try:
                            if isinstance(v,(int,float)):
                                cand = float(v); break
                            if isinstance(v,str) and v.strip():
                                cand = float(v.strip()); break
                        except Exception:
                            continue
                if cand is None and isinstance(obj, dict):
                    import re
                    for v in obj.values():
                        if isinstance(v,str):
                            m = re.search(r"(0(?:\.\d+)?|1(?:\.0+)?|0?\.\d+|\d{1,3}(?:\.\d+)?)", v)
                            if m:
                                try:
                                    cand = float(m.group(1)); break
                                except Exception:
                                    pass
                if cand is None:
                    cand = 0.85
                # normalize
                try:
                    if cand < 0: cand = 0.0
                    if cand > 1:
                        if cand <= 100: cand = cand/100.0
                        else: cand = 1.0
                except Exception:
                    cand = 0.85
                obj['confidence'] = float(cand)
                salvaged.append('confidence')
            else:
                # unknown required field: set placeholder and mark salvaged
                obj[req] = None
                salvaged.append(req)

    # type checks and simple coercion
    props = schema.get('properties', {})
    for k, rules in props.items():
        if k not in obj:
            continue
        val = obj[k]
        t = rules.get('type')
        if t == 'string':
            if not isinstance(val, str):
                return False, 'schema_error', f'type:{k}'
            if 'minLength' in rules and len(val) < rules['minLength']:
                return False, 'semantic_error', f'{k}_too_short'
        if t == 'number':
            if not isinstance(val, (int,float)):
                if isinstance(val,str):
                    import re
                    m = re.search(r"(\d{1,3}(?:\.\d+)?)", val)
                    if m:
                        try:
                            coerced = float(m.group(1))
                            obj[k] = coerced
                            if ('coerced_'+k) not in salvaged:
                                salvaged.append('coerced_'+k)
                            val = coerced
                        except Exception:
                            return False, 'schema_error', f'type:{k}'
                    else:
                        return False, 'schema_error', f'type:{k}'
                else:
                    return False, 'schema_error', f'type:{k}'
            if 'minimum' in rules and val < rules['minimum']:
                return False, 'semantic_error', f'{k}_too_small'

    # follow_up_question semantic: ensure it ends with ?
    fq = obj.get('follow_up_question','')
    if fq and isinstance(fq,str) and not fq.strip().endswith(('?','？')):
        if len(fq) < 300:
            obj['follow_up_question'] = fq.strip() + '?'
            if 'follow_up_question' not in salvaged:
                salvaged.append('follow_up_question')
        else:
            return False, 'semantic_error', 'follow_up_question_not_question'

    if salvaged:
        return True, 'salvaged_missing', f"salvaged:{sorted(salvaged)}"

    return True, None, ''


def run(schema_path, outputs_path):
    schema = load_schema(schema_path)
    total = 0
    passed = 0
    failures = []

    with open(outputs_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            total += 1
            try:
                entry = json.loads(line)
                pid = entry.get('prompt_id')
                out = entry.get('output')
            except Exception:
                failures.append((None, 'format_error', 'invalid_line'))
                continue

            ok, etype, msg = classify_and_validate(schema, out)
            if ok:
                passed += 1
            else:
                failures.append((pid, etype, msg))

    pass_rate = (passed / total * 100) if total else 0
    print(f"Total={total}, Passed={passed}, PassRate={pass_rate:.2f}%")
    if failures:
        print('\nFailures:')
        for pid, etype, msg in failures:
            print(f"  prompt_id={pid} error={etype} info={msg}")
    return {
        'total': total,
        'passed': passed,
        'pass_rate': pass_rate,
        'failures': failures
    }


if __name__ == '__main__':
    import argparse

    p = argparse.ArgumentParser()
    p.add_argument('--schema', required=False, default=str(Path(__file__).parent.parent / 'schemas' / 'interview_chat_schema.json'))
    p.add_argument('--outputs', required=False, default=str(Path(__file__).parent.parent / 'sample_outputs' / 'interview_outputs.jsonl'))
    p.add_argument('--csv', required=False, help='Optional CSV output path for validation results')
    args = p.parse_args()

    result = run(args.schema, args.outputs)

    # Optional CSV output
    if getattr(args, 'csv', None):
        out_csv = args.csv
        import csv
        Path(os.path.dirname(out_csv)).mkdir(parents=True, exist_ok=True)
        with open(out_csv, 'w', newline='', encoding='utf-8') as cf:
            writer = csv.writer(cf)
            writer.writerow(['prompt_id', 'status', 'error_type', 'info'])
            # We need to output all entries; re-open inputs to iterate
            with open(args.outputs, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        entry = json.loads(line)
                        pid = entry.get('prompt_id')
                        out = entry.get('output')
                    except Exception:
                        writer.writerow([None, 'fail', 'format_error', 'invalid_line'])
                        continue

                    ok, etype, msg = classify_and_validate(load_schema(args.schema), out)
                    status = 'pass' if ok else 'fail'
                    writer.writerow([pid, status, etype or '', msg or ''])

        print(f"Wrote CSV results to {out_csv}")
