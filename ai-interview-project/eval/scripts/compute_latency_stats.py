import csv,statistics,sys
p=sys.argv[1]
vals=[]
with open(p,'r',encoding='utf-8') as f:
    r=csv.DictReader(f)
    for row in r:
        try:
            vals.append(float(row['latency_ms']))
        except:
            pass
if not vals:
    print('no data')
    sys.exit(1)
vals=sorted(vals)
print('count',len(vals))
print('avg_s', round(sum(vals)/len(vals)/1000.0,3))
print('p50_s', round(statistics.median(vals)/1000.0,3))
idx=max(0,int(len(vals)*0.95)-1)
print('p95_s', round(vals[idx]/1000.0,3))
print('min_s', round(min(vals)/1000.0,3))
print('max_s', round(max(vals)/1000.0,3))
