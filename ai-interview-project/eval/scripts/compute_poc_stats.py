import csv,statistics,sys
p='d:/dev/AI_Interview/ai-interview-project/eval/results_poc/eval_results_20251231_171407.csv'
vals=[]
with open(p,'r',encoding='utf-8') as f:
    r=csv.DictReader(f)
    for row in r:
        try:
            v=float(row['latency_ms'])
            vals.append(v)
        except:
            pass
if not vals:
    print('no data')
    sys.exit(1)
print('count',len(vals))
print('avg', sum(vals)/len(vals))
print('p50', statistics.median(vals))
vs=sorted(vals)
idx=max(0,int(len(vs)*0.95)-1)
print('p95', vs[idx])
print('min', min(vals))
print('max', max(vals))
