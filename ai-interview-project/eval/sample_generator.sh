#!/usr/bin/env bash
python - <<'PY'
import csv, json, random, datetime
models=['gpt-3.5-turbo','gpt-4o-mini','gpt-4-turbo']
N=60
rows=[]
for i in range(N):
    id=f"sample_{i+1}"
    model=random.choices(models, weights=[0.5,0.3,0.2])[0]
    latency=round(random.gauss(120 if model=='gpt-3.5-turbo' else 90 if model=='gpt-4o-mini' else 160, 30),2)
    cost=round(0.0001 * (1 if model=='gpt-3.5-turbo' else 1.5 if model=='gpt-4o-mini' else 3),6)
    ts=(datetime.datetime.utcnow()-datetime.timedelta(minutes=random.randint(0,10000))).isoformat()+"Z"
    tokens=random.randint(20,200)
    validator_pass=random.choice(['True']*9+['False'])
    rows.append({'id':id,'model_version':model,'latency_ms':latency,'estimated_cost':cost,'timestamp':ts,'tokens_used':tokens,'validator_pass':validator_pass})

with open('eval/model_inference_log.csv','w',newline='',encoding='utf-8') as f:
    writer=csv.DictWriter(f,fieldnames=['id','model_version','latency_ms','estimated_cost','timestamp','tokens_used','validator_pass'])
    writer.writeheader();
    writer.writerows(rows)

# refs and hyps
refs={}
hyps={}
for r in rows:
    rid=r['id']
    ref_text=f"Reference answer for {rid}. Key points: skill A, skill B, experience X."
    # produce hyp slightly varied per model
    if r['model_version']=='gpt-4o-mini':
        hyp_text=ref_text + ' (concise)'
    elif r['model_version']=='gpt-4-turbo':
        hyp_text=ref_text + ' (detailed, high quality)'
    else:
        hyp_text=ref_text + ' (short)'
    refs[rid]=ref_text
    hyps[rid]=hyp_text

with open('eval/refs.jsonl','w',encoding='utf-8') as rf, open('eval/hyps.jsonl','w',encoding='utf-8') as hf:
    for k in refs:
        rf.write(json.dumps({'id':k,'reference':refs[k]})+"\n")
        hf.write(json.dumps({'id':k,'hypothesis':hyps[k]})+"\n")
print('Wrote sample files to eval/')
PY
