#!/usr/bin/env python3
"""Dependency-free fallback for testing the exact original samples with current signatures."""
import argparse, base64, hashlib, hmac, pathlib, datetime, time, urllib.request, urllib.error
p=argparse.ArgumentParser();p.add_argument('provider',choices=['quetzal','andina']);p.add_argument('sample',type=pathlib.Path);p.add_argument('--url',default='http://localhost:8080');args=p.parse_args()
raw=args.sample.read_bytes()
headers={'Content-Type':'application/json'}
if args.provider=='quetzal':
 t=str(int(time.time()));signature=hmac.new(b'qz_test_whsec_4f8a1c2e9b7d',t.encode()+b'.'+raw,hashlib.sha256).hexdigest();headers['Quetzal-Signature']=f't={t},v1={signature}'
else:
 ts=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds');signature=base64.b64encode(hmac.new(b'andina_test_secret_7c3e19ab',ts.encode()+b'.'+raw,hashlib.sha256).digest()).decode();headers['X-Andina-Timestamp']=ts;headers['X-Andina-Signature']=signature
req=urllib.request.Request(args.url+'/api/v1/webhooks/'+args.provider,data=raw,headers=headers,method='POST')
try:
 with urllib.request.urlopen(req,timeout=10) as r: print('HTTP',r.status,r.read().decode())
except urllib.error.HTTPError as e:print('HTTP',e.code,e.read().decode())
